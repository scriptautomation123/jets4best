#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")"

echo "Stopping Oracle and Vault containers..."
docker-compose down

echo "All containers stopped." 