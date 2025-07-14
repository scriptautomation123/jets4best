#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")"

# Start containers
echo "Starting Oracle and Vault containers..."
docker-compose up -d

# Wait for Oracle to be ready
echo "Waiting for Oracle to be ready..."
until docker exec oracle-db sqlplus -L sys/oracle123@//localhost:1521/XE as sysdba <<< "exit" >/dev/null 2>&1; do
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

# Vault setup
VAULT_ADDR="http://localhost:8200"
VAULT_TOKEN="test-token"

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

# Store database credentials
echo "Storing database credentials..."
SECRET_BODY='{ "username": "system", "password": "oracle-password-123" }'
vault_api "Store database credentials" \
  curl -sf --header "X-Vault-Token: $VAULT_TOKEN" --request POST --data "$SECRET_BODY" $VAULT_ADDR/v1/database/creds/testdb

# Print connection info
echo ""
echo "=== Vault Connection Information ==="
echo "Vault URL: $VAULT_ADDR"
echo "Vault Token: $VAULT_TOKEN"
echo "Role ID: $ROLE_ID"
echo "Secret ID: $SECRET_ID"
echo "====================================="
