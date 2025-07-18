#!/usr/bin/env bash
set -euo pipefail

VAULT_ADDR="http://localhost:8200"
VAULT_TOKEN="test-token" # Or use a token with write access

# Store a secret
echo "Storing a generic secret in Vault..."
curl -sf --header "X-Vault-Token: $VAULT_TOKEN" \
  --request POST \
  --data '{"data": {"api_key": "super-secret-key"}}' \
  $VAULT_ADDR/v1/secret/data/myapp/apikey

echo "Retrieving the generic secret from Vault..."
curl -sf --header "X-Vault-Token: $VAULT_TOKEN" \
  $VAULT_ADDR/v1/secret/data/myapp/apikey | jq
