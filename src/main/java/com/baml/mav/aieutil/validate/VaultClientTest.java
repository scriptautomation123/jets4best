package com.baml.mav.aieutil.validate;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.baml.mav.aieutil.util.VaultClient;

public class VaultClientTest {
    private static final Logger logger = LogManager.getLogger(VaultClientTest.class);
    private static final String TEST_DATABASE = "ECICMD03_SVC01";
    private static final String TEST_USER = "MAV_T2T_APP";

    public static void main(String[] args) {
        testLookup(TEST_USER, TEST_DATABASE); // Should match
        testLookup(TEST_USER, "SOME_OTHER_DB"); // Should not match
        testLookup("testuser", TEST_DATABASE); // Should not match
        testLookup("testuser", null); // Should not match
        testLookup("user2", ""); // Should not match

        logger.info("=== FULL VAULT PASSWORD LOOKUP TEST ===");
        testFullVaultPasswordLookup(TEST_USER, TEST_DATABASE);
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
                logger.error("No vault parameters found for user='{}' and db='{}'", user, db);
                return;
            }

            logger.info("Vault parameters found: {}", vaultParams);

            // Extract parameters
            String baseUrl = (String) vaultParams.get("base-url");
            String roleId = (String) vaultParams.get("role-id");
            String secretId = (String) vaultParams.get("secret-id");
            String ait = (String) vaultParams.get("ait");

            logger.info("Extracted parameters:");
            logger.info("  base-url: {}", baseUrl);
            logger.info("  role-id: {}", roleId);
            logger.info("  secret-id: {}", secretId);
            logger.info("  ait: {}", ait);

            // Step 2: Create VaultClient and perform full password lookup
            logger.info("Step 2: Performing full vault password lookup");
            VaultClient vaultClient = new VaultClient();
            String password = vaultClient.fetchOraclePassword(baseUrl, roleId, secretId, db, ait, user);

            if (password != null && !password.isEmpty()) {
                logger.info("SUCCESS: Password retrieved successfully");
                int passwordLength = password.length();
                logger.info("Password length: {} characters", passwordLength);
                String passwordPrefix = password.substring(0, Math.min(4, passwordLength));
                logger.info("Password (first 4 chars): {}", passwordPrefix);
            } else {
                logger.error("FAILED: No password returned from vault");
            }

        } catch (Exception e) {
            logger.error("Error during vault password lookup: {}", e.getMessage(), e);
        }

        logger.info("=== END FULL VAULT PASSWORD LOOKUP TEST ===\n");
    }
}