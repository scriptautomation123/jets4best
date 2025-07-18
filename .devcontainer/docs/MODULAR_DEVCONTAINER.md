# Modular Devcontainer Migration Guide

This document tracks the migration to a modular, composable Codespace setup for the jets4best project.

## Step 1: Split Oracle Service

- The Oracle DB service has been moved from the main `docker-compose.yaml` to its own file: `.devcontainer/compose/oracle.yaml`.
- This file contains only the Oracle service and its required volume.
- You can now include this file in your `devcontainer.json` using the `dockerComposeFile` array.

### How to Use (Step 1)

1. In `.devcontainer/devcontainer.json`, set:
   ```json
   "dockerComposeFile": [
     "compose/oracle.yaml"
   ],
   "service": "oracle",
   ```
2. Reopen the folder in container. Only the Oracle DB will be started.

---

## Step 2: Split Vault Service

- The Vault service has been moved from the main `docker-compose.yaml` to its own file: `.devcontainer/compose/vault.yaml`.
- This file contains only the Vault service and its configuration.
- You can now include this file in your `devcontainer.json` using the `dockerComposeFile` array, along with any other services you want.

### How to Use (Step 2)

1. In `.devcontainer/devcontainer.json`, set:
   ```json
   "dockerComposeFile": [
     "compose/oracle.yaml",
     "compose/vault.yaml"
   ],
   "service": "oracle", // or "vault" or your main devcontainer service
   ```
2. Reopen the folder in container. Both Oracle and Vault will be started.

---


## Step 3: Split LDAP Mock Service

- The LDAP mock service has been moved from the main `docker-compose.yaml` to its own file: `.devcontainer/compose/ldap-mock.yaml`.
- This file contains only the first LDAP mock service (add more as needed).
- You can now include this file in your `devcontainer.json` using the `dockerComposeFile` array, along with any other services you want.

### How to Use (Step 3)

1. In `.devcontainer/devcontainer.json`, set:
   ```json
   "dockerComposeFile": [
     "compose/oracle.yaml",
     "compose/vault.yaml",
     "compose/ldap-mock.yaml"
   ],
   "service": "oracle", // or your main devcontainer service
   ```
2. Reopen the folder in container. Oracle, Vault, and LDAP mock will be started.

---


## Step 4: Split PlantUML Service

- The PlantUML service has been moved from the main `docker-compose.yaml` to its own file: `.devcontainer/compose/plantuml.yaml`.
- This file contains only the PlantUML server service.
- You can now include this file in your `devcontainer.json` using the `dockerComposeFile` array, along with any other services you want.

### How to Use (Step 4)

1. In `.devcontainer/devcontainer.json`, set:
   ```json
   "dockerComposeFile": [
     "compose/oracle.yaml",
     "compose/vault.yaml",
     "compose/ldap-mock.yaml",
     "compose/plantuml.yaml"
   ],
   "service": "oracle", // or your main devcontainer service
   ```
2. Reopen the folder in container. Oracle, Vault, LDAP mock, and PlantUML will be started.

---


## Step 5: Split Devcontainer Service

- The main devcontainer service has been moved from the main `docker-compose.yaml` to its own file: `.devcontainer/compose/devcontainer.yaml`.
- This file contains only the devcontainer service, which depends on Oracle, Vault, and PlantUML by default (edit as needed).
- You can now include this file in your `devcontainer.json` using the `dockerComposeFile` array, along with any other services you want.

### How to Use (Step 5)

1. In `.devcontainer/devcontainer.json`, set:
   ```json
   "dockerComposeFile": [
     "compose/oracle.yaml",
     "compose/vault.yaml",
     "compose/ldap-mock.yaml",
     "compose/plantuml.yaml",
     "compose/devcontainer.yaml"
   ],
   "service": "devcontainer",
   ```
2. Reopen the folder in container. All selected services and the devcontainer will be started.

---

## Next Steps
- Add more variants or services as needed (e.g., additional LDAP mocks, test services).
- Update this document after each migration step.

---

## Benefits
- You can now start a Codespace with just Oracle, or any combination of services by listing the desired compose files in the array.
- No need for a monolithic compose file or major refactor in the future.

---

Continue to update this document as you modularize more services.
