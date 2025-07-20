package com.baml.mav.aieutil.validate;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import com.baml.mav.aieutil.auth.PasswordRequest;
import com.baml.mav.aieutil.auth.PasswordResolver;
import com.baml.mav.aieutil.database.ConnectionStringGenerator;
import com.baml.mav.aieutil.service.ProcedureRequest;
import com.baml.mav.aieutil.service.VaultConfig;
import com.baml.mav.aieutil.util.LoggingUtils;
import com.baml.mav.aieutil.util.VaultClient;

/**
 * Integration test class for VaultClient functionality.
 * Tests vault parameter lookup, password resolution, procedure execution flows,
 * connection string generation, and error handling scenarios.
 * This class contains static test methods for validation purposes.
 */
public final class VaultClientTest {

  /** Test database name for validation */
  private static final String TEST_DATABASE = "ECICMD03_SVC01";

  /** Test username for validation */
  private static final String TEST_USER = "MAV_T2T_APP";

  /** Oracle database type identifier */
  private static final String ORACLE_TYPE = "oracle";

  /** Test password for validation */
  private static final String TEST_PASSWORD = "test_password";

  /** Password resolution operation identifier */
  private static final String PASSWORD_RESOLUTION = "password_resolution";

  /** Failed status for operations */
  private static final String FAILED = "FAILED";

  /** Test operation identifier */
  private static final String TEST_OPERATION = "test";

  /** Exception status for operations */
  private static final String EXCEPTION_STATUS = "EXCEPTION";

  /**
   * Private constructor to prevent instantiation of utility class.
   */
  private VaultClientTest() {
    // Utility class - prevent instantiation
  }

  /**
   * Main method to execute all vault client integration tests.
   * 
   * @param args command line arguments (unused)
   */
  public static void main(final String[] args) {
    LoggingUtils.logConfigLoading("=== VAULT CLIENT INTEGRATION TEST ===");

    testVaultParameterLookup();
    testPasswordResolutionWithService();
    testFullProcedureExecutionFlow();
    testConnectionStringGeneration();
    testErrorHandlingScenarios();

    LoggingUtils.logConfigLoading("=== VAULT CLIENT INTEGRATION TEST COMPLETE ===");
  }

  /**
   * Tests vault parameter lookup functionality.
   */
  private static void testVaultParameterLookup() {
    LoggingUtils.logConfigLoading("--- Test 1: Vault Parameter Lookup ---");

    final Map<String, Object> validResult = VaultClient.getVaultParamsForUser(TEST_USER, TEST_DATABASE);
    LoggingUtils.logVaultOperation(
        "lookup", TEST_USER, validResult.isEmpty() ? "NOT_FOUND" : "FOUND");

    testLookup("invalid_user", TEST_DATABASE);
    testLookup(TEST_USER, "invalid_db");
    testLookup(null, TEST_DATABASE);
    testLookup(TEST_USER, null);

    LoggingUtils.logConfigLoading("");
  }

  /**
   * Tests vault parameter lookup for specific user and database.
   * 
   * @param user     the username to test
   * @param database the database name to test
   */
  private static void testLookup(final String user, final String database) {
    final Map<String, Object> result = VaultClient.getVaultParamsForUser(user, database);
    LoggingUtils.logVaultOperation("lookup", user, result.isEmpty() ? "NOT_FOUND" : "FOUND");
  }

  /**
   * Tests password resolution with service layer integration.
   */
  private static void testPasswordResolutionWithService() {
    LoggingUtils.logConfigLoading("--- Test 2: Password Resolution with Service Layer ---");

    try {
      final Map<String, Object> vaultParams = VaultClient.getVaultParamsForUser(TEST_USER, TEST_DATABASE);
      if (vaultParams.isEmpty()) {
        LoggingUtils.logStructuredError(
            PASSWORD_RESOLUTION,
            "vault_lookup",
            FAILED,
            "No vault parameters found for user='" + TEST_USER + "' and db='" + TEST_DATABASE + "'",
            null);
        return;
      }

      final String vaultBaseUrl = (String) vaultParams.get("base-url");
      final String roleId = (String) vaultParams.get("role-id");
      final String secretId = (String) vaultParams.get("secret-id");
      final String ait = (String) vaultParams.get("ait");

      LoggingUtils.logVaultOperation("parameter_retrieval", TEST_USER, "SUCCESS");

      testVaultPasswordFetch(vaultBaseUrl, roleId, secretId, ait);
      testServiceLayerIntegration(vaultBaseUrl, roleId, secretId, ait);

    } catch (RuntimeException exception) {
      LoggingUtils.logStructuredError(
          PASSWORD_RESOLUTION,
          TEST_OPERATION,
          EXCEPTION_STATUS,
          "Error during password resolution test: " + exception.getMessage(),
          exception);
    }

    LoggingUtils.logConfigLoading("");
  }

  /**
   * Tests vault password fetching functionality.
   * 
   * @param vaultBaseUrl the vault base URL
   * @param roleId       the role ID
   * @param secretId     the secret ID
   * @param ait          the AIT identifier
   */
  private static void testVaultPasswordFetch(final String vaultBaseUrl, final String roleId,
      final String secretId, final String ait) {
    try (VaultClient vaultClient = new VaultClient()) {
      final String password = vaultClient.fetchOraclePassword(
          vaultBaseUrl, roleId, secretId, TEST_DATABASE, ait, TEST_USER);

      if (password != null && !password.trim().isEmpty()) {
        LoggingUtils.logPasswordResolutionSuccess(TEST_USER, "vault_fetch");
      } else {
        LoggingUtils.logStructuredError(
            PASSWORD_RESOLUTION,
            "vault_fetch",
            FAILED,
            "Failed to fetch password from vault for user='" + TEST_USER + "'",
            null);
      }
    } catch (IOException exception) {
      LoggingUtils.logStructuredError(
          PASSWORD_RESOLUTION,
          "vault_close",
          EXCEPTION_STATUS,
          "Error closing VaultClient: " + exception.getMessage(),
          exception);
    }
  }

  /**
   * Tests service layer integration functionality.
   * 
   * @param vaultBaseUrl the vault base URL
   * @param roleId       the role ID
   * @param secretId     the secret ID
   * @param ait          the AIT identifier
   */
  private static void testServiceLayerIntegration(final String vaultBaseUrl, final String roleId,
      final String secretId, final String ait) {
    final PasswordResolver passwordResolver = new PasswordResolver(() -> TEST_PASSWORD);

    LoggingUtils.logDatabaseConnection(ORACLE_TYPE, TEST_DATABASE, TEST_USER);

    final Optional<String> resolvedPassword = passwordResolver.resolvePassword(
        new PasswordRequest(TEST_USER, TEST_DATABASE, vaultBaseUrl, roleId, secretId, ait));

    if (resolvedPassword.isPresent()) {
      LoggingUtils.logPasswordResolutionSuccess(TEST_USER, "service_resolution");
    } else {
      LoggingUtils.logStructuredError(
          PASSWORD_RESOLUTION,
          "service_resolution",
          FAILED,
          "Password resolution failed for user='" + TEST_USER + "'",
          null);
    }
  }

  /**
   * Tests full procedure execution flow.
   */
  private static void testFullProcedureExecutionFlow() {
    LoggingUtils.logConfigLoading("--- Test 3: Full Procedure Execution Flow ---");

    try {
      final Map<String, Object> vaultParams = VaultClient.getVaultParamsForUser(TEST_USER, TEST_DATABASE);
      if (vaultParams.isEmpty()) {
        LoggingUtils.logStructuredError(
            "procedure_execution",
            "vault_lookup",
            FAILED,
            "No vault parameters found for user='" + TEST_USER + "' and db='" + TEST_DATABASE + "'",
            null);
        return;
      }

      final VaultConfig vaultConfig = new VaultConfig(
          (String) vaultParams.get("base-url"),
          (String) vaultParams.get("role-id"),
          (String) vaultParams.get("secret-id"),
          (String) vaultParams.get("ait"));

      final ProcedureRequest procedureRequest = ProcedureRequest.builder()
          .type(ORACLE_TYPE)
          .database(TEST_DATABASE)
          .user(TEST_USER)
          .procedure("MAV_OWNER.TempTable_Onehadoop_proc")
          .input(
              "in_schema:VARCHAR2:MAV_OWNER,in_src_table_nm:VARCHAR2:CUST_INSGHT_DLY,in_typ_cd:INTEGER:136,in_prant_grp:VARCHAR2:HADOOP_DML_ROLE")
          .output("p_outmsg:STRING")
          .vaultConfig(vaultConfig)
          .build();

      LoggingUtils.logProcedureExecution(
          procedureRequest.getProcedure(), procedureRequest.getInput(), procedureRequest.getOutput());

      LoggingUtils.logConfigLoading("Procedure execution flow test completed");

    } catch (RuntimeException exception) {
      LoggingUtils.logStructuredError(
          "procedure_execution",
          TEST_OPERATION,
          EXCEPTION_STATUS,
          "Error during procedure execution flow test: " + exception.getMessage(),
          exception);
    }

    LoggingUtils.logConfigLoading("");
  }

  /**
   * Tests connection string generation for different database types.
   */
  private static void testConnectionStringGeneration() {
    LoggingUtils.logConfigLoading("--- Test 4: Connection String Generation ---");

    try {
      final String ldapUrl = ConnectionStringGenerator.createConnectionString(
          ORACLE_TYPE, TEST_DATABASE, TEST_USER, TEST_PASSWORD, null)
          .getUrl();

      LoggingUtils.logConnectionUrl(ldapUrl);

      final String jdbcUrl = ConnectionStringGenerator.createConnectionString(
          ORACLE_TYPE, TEST_DATABASE, TEST_USER, TEST_PASSWORD, "localhost")
          .getUrl();

      LoggingUtils.logConnectionUrl(jdbcUrl);

      final String h2Url = ConnectionStringGenerator.createConnectionString(
          "h2", "testdb", "sa", "password", null)
          .getUrl();

      LoggingUtils.logConnectionUrl(h2Url);

    } catch (RuntimeException exception) {
      LoggingUtils.logStructuredError(
          "connection_string",
          "generation",
          EXCEPTION_STATUS,
          "Error during connection string generation test: " + exception.getMessage(),
          exception);
    }

    LoggingUtils.logConfigLoading("");
  }

  /**
   * Tests various error handling scenarios.
   */
  private static void testErrorHandlingScenarios() {
    LoggingUtils.logConfigLoading("--- Test 5: Error Handling Scenarios ---");

    testMissingVaultConfig();
    testInvalidVaultParameters();
    testInvalidProcedureParameters();

    LoggingUtils.logConfigLoading("");
  }

  /**
   * Tests missing vault configuration scenario.
   */
  private static void testMissingVaultConfig() {
    LoggingUtils.logConfigLoading("Testing missing vault config scenario");

    try {
      VaultClient.getVaultParamsForUser("test", "test");
    } catch (RuntimeException exception) {
      LoggingUtils.logStructuredError(
          "vault_config",
          "missing",
          "EXPECTED",
          "Expected error for missing vault config: " + exception.getMessage(),
          null);
    }
  }

  /**
   * Tests invalid vault parameters scenario.
   */
  private static void testInvalidVaultParameters() {
    LoggingUtils.logConfigLoading("Testing invalid vault parameters scenario");

    try {
      final VaultConfig invalidConfig = new VaultConfig(null, null, null, null);
      LoggingUtils.logVaultOperation(
          "config_validation", "test", invalidConfig.hasDirectVaultParams() ? "VALID" : "INVALID");
    } catch (RuntimeException exception) {
      LoggingUtils.logStructuredError(
          "vault_config", "validation", "UNEXPECTED", "Unexpected error: " + exception.getMessage(), exception);
    }
  }

  /**
   * Tests invalid procedure parameters scenario.
   */
  private static void testInvalidProcedureParameters() {
    LoggingUtils.logConfigLoading("Testing invalid procedure parameters scenario");

    try {
      final ProcedureRequest procedureRequest = ProcedureRequest.builder()
          .type(ORACLE_TYPE)
          .database(TEST_DATABASE)
          .user(TEST_USER)
          .procedure("INVALID_PROC")
          .input("invalid:format")
          .output("invalid:format")
          .vaultConfig(VaultConfig.empty())
          .build();

      LoggingUtils.logProcedureExecution(
          procedureRequest.getProcedure(), procedureRequest.getInput(), procedureRequest.getOutput());

    } catch (RuntimeException exception) {
      LoggingUtils.logStructuredError(
          "procedure_parameters",
          "validation",
          "EXPECTED",
          "Expected error for invalid procedure parameters: " + exception.getMessage(),
          null);
    }
  }
}
