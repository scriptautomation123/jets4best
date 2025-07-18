#!/usr/bin/env bash
set -euo pipefail

# Usage: ./get-oracle-creds.sh <role_id> <secret_id>
VAULT_ADDR="http://localhost:8200"
ROLE_ID="${1:-}"
SECRET_ID="${2:-}"

if [[ -z "$ROLE_ID" || -z "$SECRET_ID" ]]; then
  echo "Usage: $0 <role_id> <secret_id>"
  exit 1
fi

# Login to Vault using AppRole
echo "Logging in to Vault with AppRole..."
VAULT_TOKEN=$(curl -sf --request POST \
  --data "{\"role_id\":\"$ROLE_ID\", \"secret_id\":\"$SECRET_ID\"}" \
  $VAULT_ADDR/v1/auth/approle/login | jq -r .auth.client_token)

# Request dynamic Oracle credentials
echo "Requesting dynamic Oracle credentials from Vault..."
CREDS=$(curl -sf --header "X-Vault-Token: $VAULT_TOKEN" \
  $VAULT_ADDR/v1/database/creds/testdb-role)

echo "Dynamic Oracle credentials:"
echo "$CREDS" | jq
