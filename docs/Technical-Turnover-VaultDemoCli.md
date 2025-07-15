# Technical Turnover Document: VaultDemoCli

---

## Table of Contents
1. [Introduction & Purpose](#introduction--purpose)
2. [High-Level Architecture](#high-level-architecture)
3. [CLI Modes & Parameter Validation](#cli-modes--parameter-validation)
4. [Call Stacks & Execution Flows](#call-stacks--execution-flows)
5. [Configuration & YAML Handling](#configuration--yaml-handling)
6. [Vault Integration](#vault-integration)
7. [LDAP JDBC String Generation](#ldap-jdbc-string-generation)
8. [Output & Error Handling](#output--error-handling)
9. [Testing & Automation](#testing--automation)
10. [Code Quality, Linting, and Logging](#code-quality-linting-and-logging)
11. [Extensibility & Future Work](#extensibility--future-work)
12. [Appendix: All Diagrams](#appendix-all-diagrams)

---

## 1. Introduction & Purpose

This document provides a comprehensive technical turnover for the `VaultDemoCli` Java CLI tool. It is intended for senior engineers and maintainers, and details the architecture, call stacks, validation logic, configuration, and integration points. All relevant diagrams are embedded and explained to ensure clarity and maintainability.

---

## 2. High-Level Architecture

The VaultDemoCli is designed as a minimal, robust CLI for securely fetching Oracle database credentials from Vault and generating LDAP JDBC connection strings. The architecture enforces strict separation of concerns, explicit parameter validation, and clear output conventions.

![Unified Architecture Overview](docs/out/diagrams34/unified-architecture-docs.png)
*Diagram 1: Unified architecture showing CLI, service, and integration layers.*

### Layered Responsibilities
- **CLI Layer:** Argument parsing, user interaction, output routing.
- **Service Layer:** Orchestration, validation, and flow control.
- **Integration Layer:** Vault HTTP, YAML config, LDAP string generation.

![Architecture Detail 1](docs/out/diagrams34/unified-architecture-docs-1.png)
*Diagram 2: Detailed breakdown of CLI and service responsibilities.*

---

## 3. CLI Modes & Parameter Validation

VaultDemoCli supports two mutually exclusive modes:
- **Simple Mode:** `--user` and `--db` only. Vault parameters are looked up from `vaults.yaml`.
- **Full Parameter Mode:** All vault parameters (`--vault-url`, `--role-id`, `--secret-id`, `--ait`, `--user`, `--db`) are provided explicitly.

Any partial set of vault parameters is a hard error. The CLI enforces this at argument parsing time.

![Parameter Validation Flow](docs/out/diagrams34/diagrams34-1.png)
*Diagram 3: Parameter validation and mode selection logic.*

---

## 4. Call Stacks & Execution Flows

### CLI Entry and Flow
- CLI entrypoint parses arguments.
- Validates mode and parameters.
- Delegates to service layer for orchestration.

![Call Stack Overview](docs/out/diagrams34/diagrams34-2.png)
*Diagram 4: Call stack from CLI entry to service orchestration.*

### Vault and LDAP Flows
- In `--simulate` mode: prints URLs and connection strings, no HTTP calls.
- In `--real` mode: performs Vault authentication, fetches secret, prints results.
- Always prints Vault Auth URL, Vault Secret URL, and LDAP JDBC string.

![Execution Flow](docs/out/diagrams34/diagrams34-3.png)
*Diagram 5: Execution flow for both simulate and real modes, including error handling.*

---

## 5. Configuration & YAML Handling

- Loads `application.yaml` for LDAP server configuration.
- Loads `vaults.yaml` for mapping users/dbs to vault parameters in simple mode.
- YAML parsing is strict; missing or malformed entries result in immediate errors.
- Multi-server LDAP support is derived from YAML configuration.

---

## 6. Vault Integration

- Vault Auth URL and Secret URL are constructed as full HTTPS URLs.
- In real mode, the CLI performs:
  - POST to Vault Auth endpoint to obtain a client token.
  - GET to Vault Secret endpoint to fetch the password.
- All HTTP operations use Java 21's built-in HttpClient.
- Errors in Vault communication are reported with minimal, user-focused output.

---

## 7. LDAP JDBC String Generation

- LDAP JDBC connection string is generated using all configured servers from YAML.
- The string is always printed, regardless of Vault lookup success.
- Supports Oracle failover and multi-server syntax as per configuration.

---

## 8. Output & Error Handling

- All output is routed through `cmd.getOut()`; errors through `cmd.getErr()`.
- Console output is minimal and user-focused, with technical details logged.
- No password prompts or DB connections are performed; only information is printed.

---

## 9. Testing & Automation

- Shell scripts (`vault-demo.sh`, etc.) automate both modes and validate output.
- Scripts are used for regression and interactive testing.
- All flows are covered, including error cases and edge conditions.

---

## 10. Code Quality, Linting, and Logging

- All logging uses `AieUtil.getLogger()` as per project standards.
- Linter and code quality issues (unused variables, string constants, etc.) are proactively fixed.
- No direct logger instantiation or exception throwing; all error handling uses project utilities.

---

## 11. Extensibility & Future Work

- The architecture supports future extension to additional authentication modes or output formats.
- All configuration is externalized for maintainability.
- Strict validation and separation of concerns facilitate safe refactoring and feature addition.

---

## 12. Appendix: All Diagrams

Below is a complete list of all diagrams referenced in this document, with technical captions for each.

| Diagram | Filename | Description |
|---------|----------|-------------|
| 1 | unified-architecture-docs.png | Unified architecture overview |
| 2 | unified-architecture-docs-1.png | CLI and service responsibilities |
| 3 | unified-architecture-docs-2.png | Service and integration breakdown |
| 4 | unified-architecture-docs-3.png | Vault and LDAP integration details |
| 5 | unified-architecture-docs-4.png | Error handling and output flow |
| 6 | unified-architecture-docs-5.png | YAML configuration and validation |
| 7 | unified-architecture-docs-6.png | Testing and automation flows |
| 8 | unified-architecture-docs-7.png | Extensibility and future work |
| 9 | diagrams34-1.png | Parameter validation and mode selection |
| 10 | diagrams34-2.png | Call stack overview |
| 11 | diagrams34-3.png | Execution flow for simulate/real modes |

---

**End of Document** 