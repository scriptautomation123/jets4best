package com.baml.mav.aieutil.validate;

import java.io.IOException;
import java.util.Map;

import org.apache.logging.log4j.Logger;

import com.baml.mav.aieutil.database.ConnectionStringGenerator;
import com.baml.mav.aieutil.util.LoggingUtils;
import com.baml.mav.aieutil.util.VaultClient;

public class VaultClientTest {
    private static final Logger logger = LoggingUtils.getLogger(VaultClientTest.class);
    private static final String TEST_DATABASE = "ECICMD03_SVC01";
    private static final String TEST_USER = "MAV_T2T_APP";
    private static final String NO_VAULT_PARAMS_MSG = "No vault parameters found for user='{}' and db='{}'";
    private static final String BASE_URL_KEY = "base-url";
    private static final String ROLE_ID_KEY = "role-id";
    private static final String SECRET_ID_KEY = "secret-id";
    private static final String ORACLE_TYPE = "oracle";
    private static final String CONNECTION_URL_MSG = "Connection URL: {}";

    public static void main(String[] args) {
        testLookup(TEST_USER, TEST_DATABASE); // Should match
        testLookup(TEST_USER, "SOME_OTHER_DB"); // Should not match
        testLookup("testuser", TEST_DATABASE); // Should not match
        testLookup("testuser", null); // Should not match
        testLookup("user2", ""); // Should not match

        logger.info("=== FULL VAULT PASSWORD LOOKUP TEST ===");
        testFullVaultPasswordLookup(TEST_USER, TEST_DATABASE);

        logger.info("=== LDAP CONNECTION STRING GENERATION TEST ===");
        testLdapConnectionStringGeneration(TEST_USER, TEST_DATABASE);

        logger.info("=== DATABASE CONNECTION TEST ===");
        testDatabaseConnection(TEST_USER, TEST_DATABASE);
    }

    private static void testLookup(String user, String db) {
        Map<String, Object> result = VaultClient.getVaultParamsForUser(user, db);
        logger.info("Lookup for user='{}', db='{}':", user, db);
        if (result == null || result.isEmpty()) {
            logger.info("  NOT FOUND: No vault entry for user='{}' and db='{}' in vaults.yaml", user, db);
        } else {
            logger.info("  FOUND: {}", result);
        }
        logger.info("");
    }

    private static void testFullVaultPasswordLookup(String user, String db) {
        logger.info("Testing full vault password lookup for user='{}', db='{}'", user, db);

        try {
            // Step 1: Get vault parameters from vaults.yaml
            logger.info("Step 1: Getting vault parameters from vaults.yaml");
            Map<String, Object> vaultParams = VaultClient.getVaultParamsForUser(user, db);

            if (vaultParams == null || vaultParams.isEmpty()) {
                logger.error(NO_VAULT_PARAMS_MSG, user, db);
                return;
            }

            logger.info("Vault parameters found: {}", vaultParams);

            // Extract parameters
            String baseUrl = (String) vaultParams.get(BASE_URL_KEY);
            String roleId = (String) vaultParams.get(ROLE_ID_KEY);
            String secretId = (String) vaultParams.get(SECRET_ID_KEY);
            String ait = (String) vaultParams.get("ait");

            logger.info("Extracted parameters:");
            logger.info("  base-url: {}", baseUrl);
            logger.info("  role-id: {}", roleId);
            logger.info("  secret-id: {}", secretId);
            logger.info("  ait: {}", ait);

            // Step 2: Create VaultClient and perform full password lookup with proper
            // resource management
            logger.info("Step 2: Performing full vault password lookup");
            try (VaultClient vaultClient = new VaultClient()) {
                String password = vaultClient.fetchOraclePassword(baseUrl, roleId, secretId, db, ait, user);

                if (password != null && !password.isEmpty()) {
                    logger.info("SUCCESS: Password retrieved successfully");
                    int passwordLength = password.length();
                    logger.info("Password length: {} characters", passwordLength);
                    String passwordPrefix = password.substring(0, Math.min(4, passwordLength));
                    logger.info("Password (first 4 chars): {}", passwordPrefix);

                    // Step 3: Generate LDAP connection string
                    logger.info("Step 3: Generating LDAP connection string");
                    testLdapConnectionString(ORACLE_TYPE, db, user, password);
                } else {
                    logger.error("FAILED: No password returned from vault");
                }
            }

        } catch (Exception e) {
            logger.error("Error during vault password lookup: {}", e.getMessage(), e);
        }

        logger.info("=== END FULL VAULT PASSWORD LOOKUP TEST ===\n");
    }

    private static String getVaultPassword(String user, String db, Map<String, Object> vaultParams) {
        try (VaultClient vaultClient = new VaultClient()) {
            String baseUrl = (String) vaultParams.get(BASE_URL_KEY);
            String roleId = (String) vaultParams.get(ROLE_ID_KEY);
            String secretId = (String) vaultParams.get(SECRET_ID_KEY);
            String ait = (String) vaultParams.get("ait");

            return vaultClient.fetchOraclePassword(baseUrl, roleId, secretId, db, ait, user);
        } catch (IOException e) {
            logger.error("Failed to close VaultClient", e);
            return null;
        }
    }

    private static void testLdapConnectionStringGeneration(String user, String db) {
        logger.info("Testing LDAP connection string generation for user='{}', db='{}'", user, db);

        try {
            Map<String, Object> vaultParams = VaultClient.getVaultParamsForUser(user, db);
            if (vaultParams == null || vaultParams.isEmpty()) {
                logger.error(NO_VAULT_PARAMS_MSG, user, db);
                return;
            }

            String password = getVaultPassword(user, db, vaultParams);
            if (password == null || password.isEmpty()) {
                logger.error("No password retrieved for user='{}' and db='{}'", user, db);
                return;
            }

            String connectionUrl = ConnectionStringGenerator
                    .createConnectionString(ORACLE_TYPE, db, user, password, null).getUrl();

            logger.info("SUCCESS: LDAP connection string generated");
            logger.info(CONNECTION_URL_MSG, connectionUrl);

        } catch (Exception e) {
            logger.error("FAILED: Error generating LDAP connection string: {}", e.getMessage(), e);
        }

        logger.info("");
    }

    private static void testDatabaseConnection(String user, String db) {
        logger.info("Testing actual database connection for user='{}', db='{}'", user, db);

        try {
            Map<String, Object> vaultParams = VaultClient.getVaultParamsForUser(user, db);
            if (vaultParams == null || vaultParams.isEmpty()) {
                logger.error(NO_VAULT_PARAMS_MSG, user, db);
                return;
            }

            String password = getVaultPassword(user, db, vaultParams);
            if (password == null || password.isEmpty()) {
                logger.error("No password retrieved for user='{}' and db='{}'", user, db);
                return;
            }

            String connectionUrl = ConnectionStringGenerator
                    .createConnectionString(ORACLE_TYPE, db, user, password, null).getUrl();

            logger.info(CONNECTION_URL_MSG, connectionUrl);
            logger.info("User: {}", user);

            testDatabaseQuery(connectionUrl, user, password);

        } catch (Exception e) {
            logger.error("FAILED: Error during database connection test: {}", e.getMessage(), e);
        }

        logger.info("");
    }

    private static void testLdapConnectionString(String type, String database, String user, String password) {
        logger.info("Testing LDAP connection string generation");
        logger.info("Parameters: type={}, database={}, user={}, password.length={}",
                type, database, user, password != null ? password.length() : 0);

        try {
            // Generate LDAP connection string (null host = use LDAP)
            String connectionUrl = ConnectionStringGenerator
                    .createConnectionString(type, database, user, password, null).getUrl();

            logger.info("SUCCESS: LDAP connection string generated");
            logger.info(CONNECTION_URL_MSG, connectionUrl);

        } catch (Exception e) {
            logger.error("FAILED: Error generating LDAP connection string: {}", e.getMessage(), e);
        }

        logger.info("");
    }

    private static void testDatabaseQuery(String connectionUrl, String user, String password) {
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(connectionUrl, user, password)) {
            logger.info("SUCCESS: Database connection established");

            // Test a simple query to verify connection works
            try (java.sql.Statement stmt = conn.createStatement()) {
                java.sql.ResultSet rs = stmt.executeQuery("SELECT 1 FROM DUAL");
                if (rs.next()) {
                    logger.info("SUCCESS: Test query executed successfully");
                }
            }

        } catch (Exception e) {
            logger.error("FAILED: Database connection failed: {}", e.getMessage(), e);
        }
    }
}