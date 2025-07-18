#!/usr/bin/env bash

vault_api() {
  local desc="$1"
  shift
  if ! "$@"; then
    echo "ERROR: $desc failed"
    exit 1
  fi
}

# Batch/array input support
run_batch_setup() {
  local csv_file="$1"
  if [[ ! -f "$csv_file" ]]; then
    log_error "Batch input file not found: $csv_file"
    exit 1
  fi
  log_info "Running batch setup from $csv_file"
  local line_num=0
  local -a summary_rows
  summary_rows+=("DB_NAME,DB_ROLE,DB_POLICY,DB_APPROLE,ROLE_ID,SECRET_ID")
  while IFS=, read -r db_name db_role db_policy db_approle db_conn_url db_admin_user db_admin_pass db_creation_sql; do
    line_num=$((line_num+1))
    # Skip header or empty lines
    if [[ $line_num -eq 1 && "$db_name" == "DB_NAME" ]]; then continue; fi
    if [[ -z "$db_name" || "$db_name" == "#"* ]]; then continue; fi
    log_info "Batch: Setting up $db_name ($db_role)"
    local role_id secret_id
    role_id=""
    secret_id=""
    # Capture output from setup_vault_oracle_db
    local result
    result=$(setup_vault_oracle_db "$db_name" "$db_role" "$db_policy" "$db_approle" "$db_conn_url" "$db_admin_user" "$db_admin_pass" "$db_creation_sql")
    role_id=$(echo "$result" | grep 'Role ID:' | awk -F': ' '{print $2}')
    secret_id=$(echo "$result" | grep 'Secret ID:' | awk -F': ' '{print $2}')
    summary_rows+=("$db_name,$db_role,$db_policy,$db_approle,$role_id,$secret_id")
  done < "$csv_file"
  log_info "\n==== Batch Vault Role/ID Summary ===="
  printf '%s\n' "${summary_rows[@]}" | column -t -s,
  log_info "====================================\n"
}

download_plugin() {
  echo "Downloading Oracle Vault plugin..."
  wget -q "$PLUGIN_URL" -O /tmp/vault-plugin-database-oracle.zip || { echo "ERROR: Download failed"; exit 1; }
  unzip -o /tmp/vault-plugin-database-oracle.zip -d /tmp/ || { echo "ERROR: Unzip failed"; exit 1; }
  chmod +x "/tmp/$PLUGIN_BIN" || { echo "ERROR: chmod failed"; exit 1; }
}

copy_plugin_to_container() {
  echo "Copying plugin into Vault container..."
  docker cp "/tmp/$PLUGIN_BIN" "$VAULT_CONTAINER":/bin/"$PLUGIN_BIN" || { echo "ERROR: docker cp failed"; exit 1; }
}

calculate_sha256() {
  echo "Calculating SHA256..."
  PLUGIN_SHA256=$(sha256sum "/tmp/$PLUGIN_BIN" | awk '{print $1}')
  if [[ -z "$PLUGIN_SHA256" ]]; then
    echo "ERROR: SHA256 calculation failed"; exit 1;
  fi
}

register_plugin() {
  echo "Registering plugin with Vault..."
  # Check if plugin is already registered
  if curl -sf --header "X-Vault-Token: $VAULT_TOKEN" $VAULT_ADDR/v1/sys/plugins/catalog/database/oracle-database-plugin | grep -q 'sha256'; then
    echo "Plugin already registered, skipping."
    return 0
  fi
  curl -sf --header "X-Vault-Token: $VAULT_TOKEN" \
    --request POST \
    --data '{"sha256": "'$PLUGIN_SHA256'", "command": "'$PLUGIN_BIN'"}' \
    $VAULT_ADDR/v1/sys/plugins/catalog/database/oracle-database-plugin || {
      echo "ERROR: Plugin registration failed"; exit 1;
    }
}

# Enable AppRole auth
enable_approle_auth() {
  echo "Enabling AppRole auth..."
  # Check if AppRole auth is already enabled
  if curl -sf --header "X-Vault-Token: $VAULT_TOKEN" $VAULT_ADDR/v1/sys/auth | jq -e 'has("approle/")' >/dev/null; then
    echo "AppRole auth already enabled, skipping."
    return 0
  fi
  curl -sf --header "X-Vault-Token: $VAULT_TOKEN" --request POST --data '{"type":"approle"}' $VAULT_ADDR/v1/sys/auth/approle || {
    echo "ERROR: Failed to enable AppRole auth"; exit 1;
  }
}

# Enable database secrets engine
enable_database_secrets_engine() {
  echo "Enabling database secrets engine..."
  # Check if database secrets engine is already enabled
  if curl -sf --header "X-Vault-Token: $VAULT_TOKEN" $VAULT_ADDR/v1/sys/mounts | jq -e 'has("database/")' >/dev/null; then
    echo "Database secrets engine already enabled, skipping."
    return 0
  fi
  vault_api "Enable database secrets engine" \
    curl -sf --header "X-Vault-Token: $VAULT_TOKEN" --request POST --data '{"type":"database"}' $VAULT_ADDR/v1/sys/mounts/database
}

# Create policy
create_policy() {
  local policy_name="${DB_POLICY:-policy-${DB_NAME}}"
  log_info "Creating policy $policy_name..."
  # Check if policy exists
  if curl -sf --header "X-Vault-Token: $VAULT_TOKEN" $VAULT_ADDR/v1/sys/policies/acl/$policy_name | jq -e '.data.name == "'$policy_name'"' >/dev/null 2>&1; then
    echo "Policy $policy_name already exists, skipping."
    return 0
  fi
  POLICY_BODY='{ "policy": "path \"database/creds/'"$DB_NAME"'\" { capabilities = [\"read\"] }" }'
  log_info "Registering policy with Vault..."
  curl -sf --header "X-Vault-Token: $VAULT_TOKEN" --request POST --data "$POLICY_BODY" $VAULT_ADDR/v1/sys/policies/acl/$policy_name || {
    echo "ERROR: Failed to create policy $policy_name"; exit 1;
  }
}
# Create AppRole
create_approle() {
  local approle_name="${DB_APPROLE:-approle-${DB_ROLE}}"
  local policy_name="${DB_POLICY:-policy-${DB_NAME}}"
  echo "Creating AppRole $approle_name..."
  # Check if AppRole exists
  if curl -sf --header "X-Vault-Token: $VAULT_TOKEN" $VAULT_ADDR/v1/auth/approle/role/$approle_name | jq -e '.data.name == "'$approle_name'"' >/dev/null 2>&1; then
    echo "AppRole $approle_name already exists, skipping."
    return 0
  fi
  ROLE_BODY='{ "policies": "'"$policy_name"'" }'
  log_info "Enabling AppRole auth..."
  curl -sf --header "X-Vault-Token: $VAULT_TOKEN" --request POST --data "$ROLE_BODY" $VAULT_ADDR/v1/auth/approle/role/$approle_name || {
    echo "ERROR: Failed to create AppRole $approle_name"; exit 1;
  }
}
# Get role_id
get_role_id() {
  local approle_name="${DB_APPROLE:-approle-${DB_ROLE}}"
  echo "Getting role_id for $approle_name..."
  local role_id
  role_id=$(curl -sf --header "X-Vault-Token: $VAULT_TOKEN" $VAULT_ADDR/v1/auth/approle/role/$approle_name/role-id | jq -r .data.role_id)
  if [[ -z "$role_id" || "$role_id" == "null" ]]; then
  log_info "Enabling database secrets engine..."
    exit 1
  fi
    log_info "Database secrets engine already enabled, skipping."
}

# Create secret_id
create_secret_id() {
  local approle_name="${DB_APPROLE:-approle-${DB_ROLE}}"
  echo "Creating secret_id for $approle_name..."
  local secret_id
  secret_id=$(curl -sf --header "X-Vault-Token: $VAULT_TOKEN" --request POST --data '{}' $VAULT_ADDR/v1/auth/approle/role/$approle_name/secret-id | jq -r .data.secret_id)
  if [[ -z "$secret_id" || "$secret_id" == "null" ]]; then
    echo "ERROR: Failed to retrieve secret_id for $approle_name"
    exit 1
  fi
  echo "$secret_id"
}

create_database_connection() {
  local db_name="${DB_NAME:-testdb}"
  local db_role="${DB_ROLE:-testdb-role}"
  local db_conn_url="${DB_CONN_URL:-oracle://{{username}}:{{password}}@localhost:1521/XE}"
  local db_admin_user="${DB_ADMIN_USER:-system}"
  local db_admin_pass="${DB_ADMIN_PASS:-$DB_ADMIN_PASS}"
  local db_creation_sql="${DB_CREATION_SQL:-CREATE USER {{name}} IDENTIFIED BY \"{{password}}\"; GRANT CONNECT TO {{name}};}"
  
  log_info "Configuring database connection for $db_name..."
# Configure database connection
  echo "Configuring database connection in Vault..."
  # Check if DB config exists
  if curl -sf --header "X-Vault-Token: $VAULT_TOKEN" $VAULT_ADDR/v1/database/config/$DB_NAME | jq -e '.data.name == "'$DB_NAME'"' >/dev/null 2>&1; then
    echo "Database config for $DB_NAME already exists, skipping."
    return 0
  fi
  DB_CONFIG_BODY='{
    "plugin_name": "oracle-database-plugin",
    "allowed_roles": "'"$DB_ROLE"'",
    "connection_url": "'"$DB_CONN_URL"'",
    "username": "'"$DB_ADMIN_USER"'",
    "password": "'"$DB_ADMIN_PASS"'"
  }'
  vault_api "Configure database connection" \
    curl -sf --header "X-Vault-Token: $VAULT_TOKEN" --request POST --data "$DB_CONFIG_BODY" $VAULT_ADDR/v1/database/config/$DB_NAME
}
# Create database role
create_database_role() {
  log_info "Creating database role in Vault..."
  local creation_sql="${DB_CREATION_SQL:-CREATE USER {{name}} IDENTIFIED BY \"{{password}}\"; GRANT CONNECT TO {{name}};}"
  ROLE_BODY='{
    "db_name": "'"$DB_NAME"'",
    "creation_statements": "'"$creation_sql"'",
    "default_ttl": "1h",
    "max_ttl": "24h"
  }'
  vault_api "Create database role" \
    curl -sf --header "X-Vault-Token: $VAULT_TOKEN" --request POST --data "$ROLE_BODY" $VAULT_ADDR/v1/database/roles/$DB_ROLE
}



# Generic per-database setup function
setup_vault_oracle_db() {
    local db_name="$1"
    local db_role="$2"
    local db_policy="${3:-policy-${db_name}}"
    local db_approle="${4:-approle-${db_role}}"
    local db_conn_url="$5"
    local db_admin_user="$6"
    local db_admin_pass="$7"
    local db_creation_sql="$8"

    DB_NAME="$db_name" DB_ROLE="$db_role" DB_POLICY="$db_policy" DB_APPROLE="$db_approle" DB_CONN_URL="$db_conn_url" DB_ADMIN_USER="$db_admin_user" DB_ADMIN_PASS="$db_admin_pass" DB_CREATION_SQL="$db_creation_sql" \
    create_policy
    DB_NAME="$db_name" DB_ROLE="$db_role" DB_POLICY="$db_policy" DB_APPROLE="$db_approle" DB_CONN_URL="$db_conn_url" DB_ADMIN_USER="$db_admin_user" DB_ADMIN_PASS="$db_admin_pass" DB_CREATION_SQL="$db_creation_sql" \
    create_approle
    local role_id
    role_id=$(DB_NAME="$db_name" DB_ROLE="$db_role" DB_POLICY="$db_policy" DB_APPROLE="$db_approle" DB_CONN_URL="$db_conn_url" DB_ADMIN_USER="$db_admin_user" DB_ADMIN_PASS="$db_admin_pass" DB_CREATION_SQL="$db_creation_sql" get_role_id)
    local secret_id
    secret_id=$(DB_NAME="$db_name" DB_ROLE="$db_role" DB_POLICY="$db_policy" DB_APPROLE="$db_approle" DB_CONN_URL="$db_conn_url" DB_ADMIN_USER="$db_admin_user" DB_ADMIN_PASS="$db_admin_pass" DB_CREATION_SQL="$db_creation_sql" create_secret_id)
    DB_NAME="$db_name" DB_ROLE="$db_role" DB_POLICY="$db_policy" DB_APPROLE="$db_approle" DB_CONN_URL="$db_conn_url" DB_ADMIN_USER="$db_admin_user" DB_ADMIN_PASS="$db_admin_pass" DB_CREATION_SQL="$db_creation_sql" \

    return 0
  fi
  DB_CONFIG_BODY='{
    "plugin_name": "oracle-database-plugin",
    "allowed_roles": "'$DB_ROLE'",
    "connection_url": "'$DB_CONN_URL'",
    "username": "'$DB_ADMIN_USER'",
    "password": "'$DB_ADMIN_PASS'"
  }'
  vault_api "Configure database connection" \
    curl -sf --header "X-Vault-Token: $VAULT_TOKEN" --request POST --data "$DB_CONFIG_BODY" $VAULT_ADDR/v1/database/config/$DB_NAME
}

# ============================================================================
# manage_vault.sh - Modular Vault/Oracle Setup Script
#
# Usage:
#   export VAULT_TOKEN=... DB_ADMIN_PASS=... [other vars]
#   ./manage_vault.sh
#
# Required Environment Variables:
#   VAULT_TOKEN      - Vault root/admin token (do not hardcode)
#   DB_ADMIN_PASS    - Oracle DB admin password (do not hardcode)
#
# Optional Environment Variables (with defaults):
#   DB_NAME          - Database name (default: testdb)
#   DB_ROLE          - Vault DB role name (default: testdb-role)
#   DB_CONN_URL      - DB connection URL (default: oracle://{{username}}:{{password}}@localhost:1521/XE)
#   DB_ADMIN_USER    - Oracle DB admin user (default: system)
#   VAULT_ADDR       - Vault address (default: http://localhost:8200)
#   VAULT_CONTAINER  - Vault Docker container name (default: vault-dev)
#   PLUGIN_VERSION   - Oracle plugin version (default: 0.10.2)
#
# One-time setup (first run):
#   - Installs and registers plugin, enables engines, creates policy/role, configures DB
# Per-database setup:
#   - Set DB_NAME, DB_ROLE, DB_CONN_URL, DB_ADMIN_USER, DB_ADMIN_PASS as needed
#   - Re-run script to add new DB/role config to Vault
#
# Idempotent: Safe to re-run; skips existing resources.
#
# Example:
#   export VAULT_TOKEN=... DB_ADMIN_PASS=... DB_NAME=mydb DB_ROLE=myrole
#   ./manage_vault.sh
# ============================================================================

# === Configurable Parameters ===
# Set these as environment variables or edit defaults below
PLUGIN_VERSION="${PLUGIN_VERSION:-0.10.2}"
PLUGIN_URL="${PLUGIN_URL:-https://releases.hashicorp.com/vault-plugin-database-oracle/${PLUGIN_VERSION}/vault-plugin-database-oracle_${PLUGIN_VERSION}_linux_amd64.zip}"
PLUGIN_BIN="${PLUGIN_BIN:-vault-plugin-database-oracle}"
VAULT_CONTAINER="${VAULT_CONTAINER:-vault-dev}"
VAULT_ADDR="${VAULT_ADDR:-http://localhost:8200}"
 # VAULT_TOKEN must be set in the environment; do not self-assign
DB_NAME="${DB_NAME:-testdb}"
DB_ROLE="${DB_ROLE:-testdb-role}"
DB_CONN_URL="${DB_CONN_URL:-oracle://{{username}}:{{password}}@localhost:1521/XE}"
DB_ADMIN_USER="${DB_ADMIN_USER:-system}"
 # DB_ADMIN_PASS must be set in the environment; do not self-assign

# === Secrets Validation ===
if [[ -z "$VAULT_TOKEN" ]]; then
  echo "ERROR: VAULT_TOKEN environment variable must be set (do not hardcode secrets in the script)." >&2
  exit 1
fi
if [[ -z "$DB_ADMIN_PASS" ]]; then
  echo "ERROR: DB_ADMIN_PASS environment variable must be set (do not hardcode secrets in the script)." >&2
  exit 1
fi
