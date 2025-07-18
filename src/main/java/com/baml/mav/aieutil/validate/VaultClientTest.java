package com.baml.mav.aieutil.validate;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.baml.mav.aieutil.util.VaultClient;

public class VaultClientTest {
    private static final Logger logger = LogManager.getLogger(VaultClientTest.class);

    public static void main(String[] args) {
        testLookup("MAV_T2T_APP", "ECICMD03_SVC01"); // Should match
        testLookup("MAV_T2T_APP", "SOME_OTHER_DB"); // Should not match
        testLookup("testuser", "ECICMD03_SVC01"); // Should not match
        testLookup("testuser", null); // Should not match
        testLookup("user2", ""); // Should not match
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
}