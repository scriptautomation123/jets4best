#!/usr/bin/env bash


install_oracle_vault_plugin() {
  set -e
  PLUGIN_VERSION="0.10.2"
  PLUGIN_URL="https://releases.hashicorp.com/vault-plugin-database-oracle/${PLUGIN_VERSION}/vault-plugin-database-oracle_${PLUGIN_VERSION}_linux_amd64.zip"
  PLUGIN_BIN="vault-plugin-database-oracle"
  VAULT_CONTAINER="vault-dev"
  VAULT_ADDR="http://localhost:8200"
  VAULT_TOKEN="myroot"

  echo "Downloading Oracle Vault plugin..."
  wget -q "$PLUGIN_URL" -O /tmp/vault-plugin-database-oracle.zip
  unzip -o /tmp/vault-plugin-database-oracle.zip -d /tmp/
  chmod +x /tmp/$PLUGIN_BIN

  echo "Copying plugin into Vault container..."
  docker cp /tmp/$PLUGIN_BIN $VAULT_CONTAINER:/bin/$PLUGIN_BIN

  echo "Calculating SHA256..."
  PLUGIN_SHA256=$(sha256sum /tmp/$PLUGIN_BIN | awk '{print $1}')

  echo "Registering plugin with Vault..."
  curl -sf --header "X-Vault-Token: $VAULT_TOKEN" \
    --request POST \
    --data '{"sha256": "'$PLUGIN_SHA256'", "command": "'$PLUGIN_BIN'"}' \
    $VAULT_ADDR/v1/sys/plugins/catalog/database/oracle-database-plugin || {
      echo "ERROR: Plugin registration failed"; exit 1;
    }

  echo "Oracle Vault plugin installed and registered."
}
set -euo pipefail

cd "$(dirname "$0")"

# Start containers
echo "Starting Oracle and Vault containers..."
docker-compose up -d

# Wait for Oracle to be ready
echo "Waiting for Oracle to be ready..."
until docker exec oracle-xe sqlplus -L sys/Oracle123@//localhost:1521/XE as sysdba <<< "exit" >/dev/null 2>&1; do
  echo "Oracle is starting up... please wait"
  sleep 30
done
echo "Oracle Database is ready!"

# Wait for Vault to be ready
echo "Waiting for Vault to be ready..."
until curl -s http://localhost:8200/v1/sys/health >/dev/null 2>&1; do
  echo "Vault is starting up... please wait"
  sleep 5
done
echo "Vault is ready!"

# Install and register the Oracle Vault plugin before configuring the database connection
install_oracle_vault_plugin

# Vault setup
VAULT_ADDR="http://localhost:8200"
VAULT_TOKEN="myroot"

# Helper function for error checking
vault_api() {
  local desc="$1"
  shift
  if ! "$@"; then
    echo "ERROR: $desc failed"
    exit 1
  fi
}

# Enable AppRole auth
echo "Enabling AppRole auth..."
vault_api "Enable AppRole" \
  curl -sf --header "X-Vault-Token: $VAULT_TOKEN" --request POST --data '{"type":"approle"}' $VAULT_ADDR/v1/sys/auth/approle

# Enable database secrets engine
echo "Enabling database secrets engine..."
vault_api "Enable database secrets engine" \
  curl -sf --header "X-Vault-Token: $VAULT_TOKEN" --request POST --data '{"type":"database"}' $VAULT_ADDR/v1/sys/mounts/database

# Create policy
echo "Creating policy..."
POLICY_BODY='{ "policy": "path \"database/creds/testdb\" { capabilities = [\"read\"] }" }'
vault_api "Create policy" \
  curl -sf --header "X-Vault-Token: $VAULT_TOKEN" --request POST --data "$POLICY_BODY" $VAULT_ADDR/v1/sys/policies/acl/test-policy

# Create AppRole
echo "Creating AppRole..."
ROLE_BODY='{ "policies": "test-policy" }'
vault_api "Create AppRole" \
  curl -sf --header "X-Vault-Token: $VAULT_TOKEN" --request POST --data "$ROLE_BODY" $VAULT_ADDR/v1/auth/approle/role/test-role

# Get role_id
echo "Getting role_id..."
ROLE_ID=$(curl -sf --header "X-Vault-Token: $VAULT_TOKEN" $VAULT_ADDR/v1/auth/approle/role/test-role/role-id | jq -r .data.role_id)
if [[ -z "$ROLE_ID" || "$ROLE_ID" == "null" ]]; then
  echo "ERROR: Failed to retrieve role_id"
  exit 1
fi

# Create secret_id
echo "Creating secret_id..."
SECRET_ID=$(curl -sf --header "X-Vault-Token: $VAULT_TOKEN" --request POST --data '{}' $VAULT_ADDR/v1/auth/approle/role/test-role/secret-id | jq -r .data.secret_id)
if [[ -z "$SECRET_ID" || "$SECRET_ID" == "null" ]]; then
  echo "ERROR: Failed to retrieve secret_id"
  exit 1
fi


# Configure database connection
echo "Configuring database connection in Vault..."
DB_CONFIG_BODY='{
  "plugin_name": "oracle-database-plugin",
  "allowed_roles": "testdb-role",
  "connection_url": "oracle://{{username}}:{{password}}@localhost:1521/XE",
  "username": "system",
  "password": "Oracle123"
}'
vault_api "Configure database connection" \
  curl -sf --header "X-Vault-Token: $VAULT_TOKEN" --request POST --data "$DB_CONFIG_BODY" $VAULT_ADDR/v1/database/config/testdb

# Create database role
echo "Creating database role in Vault..."
ROLE_BODY='{
  "db_name": "testdb",
  "creation_statements": "CREATE USER {{name}} IDENTIFIED BY \"{{password}}\"; GRANT CONNECT TO {{name}};",
  "default_ttl": "1h",
  "max_ttl": "24h"
}'
vault_api "Create database role" \
  curl -sf --header "X-Vault-Token: $VAULT_TOKEN" --request POST --data "$ROLE_BODY" $VAULT_ADDR/v1/database/roles/testdb-role

# Print connection info
echo ""
echo "=== Vault Connection Information ==="
echo "Vault URL: $VAULT_ADDR"
echo "Vault Token: $VAULT_TOKEN"
echo "Role ID: $ROLE_ID"
echo "Secret ID: $SECRET_ID"
echo "====================================="
