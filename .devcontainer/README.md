# Modular Devcontainer Setup for jets4best

This project uses a modular devcontainer setup. You can launch a Codespace with any combination of services by listing the desired compose files in your `.devcontainer/devcontainer.json`.

## Available Compose Files

- `compose/oracle.yaml` — Oracle Database
- `compose/vault.yaml` — HashiCorp Vault
- `compose/ldap-mock.yaml` — Mock LDAP server
- `compose/plantuml.yaml` — PlantUML server
- `compose/devcontainer.yaml` — Main devcontainer service

## How to Use

1. **Edit `.devcontainer/devcontainer.json`:**
   - Set the `dockerComposeFile` property to an array of the services you want to run. Example:

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

   - You can include any subset of these files to launch only the services you need.

2. **Reopen in Container:**
   - Use the VS Code command palette: `Dev Containers: Reopen in Container`.
   - The Codespace will start with only the services you selected.

## Examples

- **Just Oracle:**
  ```json
  "dockerComposeFile": ["compose/oracle.yaml", "compose/devcontainer.yaml"],
  "service": "devcontainer",
  ```

- **Oracle + Vault:**
  ```json
  "dockerComposeFile": ["compose/oracle.yaml", "compose/vault.yaml", "compose/devcontainer.yaml"],
  "service": "devcontainer",
  ```

- **All Services:**
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

## Benefits
- No need for a monolithic compose file.
- Add or remove services by editing a single array.
- Easy to extend for future services.

See `docs/MODULAR_DEVCONTAINER.md` for migration details and more examples.
