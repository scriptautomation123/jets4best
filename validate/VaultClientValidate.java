package validate;

import java.util.Map;

import com.baml.mav.aieutil.util.VaultClient;

public class VaultClientValidate {
    public static void main(String[] args) {
        testLookup("MAV_T2T_APP", "ECICMD03_SVC01"); // Should match
        testLookup("MAV_T2T_APP", "SOME_OTHER_DB"); // Should not match
        testLookup("testuser", "ECICMD03_SVC01"); // Should not match
        testLookup("testuser", null); // Should not match
        testLookup("user2", ""); // Should not match
    }

    private static void testLookup(String user, String db) {
        Map<String, Object> result = VaultClient.getVaultParamsForUser(user, db);
        System.out.println("Lookup for user='" + user + "', db='" + db + "':");
        if (result == null || result.isEmpty()) {
            System.out
                    .println("  NOT FOUND: No vault entry for user='" + user + "' and db='" + db + "' in vaults.yaml");
        } else {
            System.out.println("  FOUND: " + result);
        }
        System.out.println();
    }
}