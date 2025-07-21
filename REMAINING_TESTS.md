# Remaining Unit/Integration Tests Checklist

## Service Layer
- [ ] DatabaseServiceTest: Mock DB, test error handling and result mapping
- [ ] SqlExecutionServiceTest: Mock DB, test SQL/script execution and error cases
- [ ] VaultClientServiceTest: Mock VaultClient, test lookup/direct modes and errors

## CLI/Command Layer
- [ ] ExecProcedureCmdTest: Test argument parsing, error handling, and dispatch
- [ ] ExecSqlCmdTest: Test argument parsing, error handling, and dispatch
- [ ] ExecVaultClientCmdTest: Test argument parsing, error handling, and dispatch
- [ ] AieUtilMainTest: Test main entry, help/version, and error exit codes

## Utility Layer
- [ ] ExceptionUtilsTest: Test wrap, handleCliException, error translation
- [ ] YamlConfigTest: Test config loading, missing file, key lookup
- [ ] VaultClientTest: Mock HTTP, test error and success cases
- [ ] LoggingUtilsTest: (Optional) Test minimal and structured logging

## Edge/Negative Cases
- [ ] Add negative/error/edge case tests for all above as needed

## Integration (Optional)
- [ ] VaultClientTest (integration): Real Vault config, test end-to-end
- [ ] DatabaseServiceTest (integration): Real DB config, test end-to-end

---

**All tests should be Java 8 compliant and as simple as possible.** 