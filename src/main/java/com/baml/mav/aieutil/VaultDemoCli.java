package com.baml.mav.aieutil;

import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.logging.log4j.Logger;

import com.baml.mav.aieutil.util.LoggingUtils;
import com.baml.mav.aieutil.util.VaultClient;
import com.baml.mav.aieutil.util.YamlConfig;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "vault-demo", mixinStandardHelpOptions = true, description = "VaultClient CLI: fetch password and show connection strings")
public class VaultDemoCli implements Callable<Integer> {

    private static final Logger logger = LoggingUtils.getLogger(VaultDemoCli.class);
    private static final String VAULT_AUTH_HEADER = "=== VAULT AUTH URL ===";
    private static final String VAULT_SECRET_HEADER = "=== VAULT SECRET URL ===";
    private static final String LDAP_CONNECTION_HEADER = "=== LDAP CONNECTION STRING ===";

    @Option(names = "--user", description = "Database username", required = true)
    String user;

    @Option(names = "--db", description = "Database name", required = true)
    String db;

    @Option(names = "--vault-url", description = "Vault base URL")
    String vaultUrl;

    @Option(names = "--role-id", description = "Vault role ID")
    String roleId;

    @Option(names = "--secret-id", description = "Vault secret ID")
    String secretId;

    @Option(names = "--ait", description = "AIT")
    String ait;

    @Option(names = "--real", description = "Make real Vault connection (default: false)")
    boolean real;

    @Option(names = "--simulate", description = "Simulate Vault lookup (print URLs, no HTTP calls)")
    boolean simulate;

    @Spec
    CommandSpec spec;

    public static void main(String[] args) {
        if (logger.isDebugEnabled()) {
            logger.debug("java.home: {}", System.getProperty("java.home"));
        }
        int exitCode = new CommandLine(new VaultDemoCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        printParameterDebug();

        try {
            // Validate required parameters
            if (user == null || user.trim().isEmpty()) {
                spec.commandLine().getErr().println("[ERROR] --user parameter is required");
                return 1;
            }

            if (db == null || db.trim().isEmpty()) {
                spec.commandLine().getErr().println("[ERROR] --db parameter is required");
                return 1;
            }

            if (real && simulate) {
                spec.commandLine().getErr().println("[ERROR] Cannot use both --real and --simulate");
                return 1;
            }

            if (!real && !simulate) {
                spec.commandLine().getErr().println("[ERROR] Must specify either --real or --simulate");
                return 1;
            }

            // Validate vault parameter modes
            boolean hasVaultUrl = vaultUrl != null && !vaultUrl.trim().isEmpty();
            boolean hasRoleId = roleId != null && !roleId.trim().isEmpty();
            boolean hasSecretId = secretId != null && !secretId.trim().isEmpty();
            boolean hasAit = ait != null && !ait.trim().isEmpty();

            int vaultParamCount = 0;
            if (hasVaultUrl)
                vaultParamCount++;
            if (hasRoleId)
                vaultParamCount++;
            if (hasSecretId)
                vaultParamCount++;
            if (hasAit)
                vaultParamCount++;

            if (vaultParamCount == 0) {
                // Mode 1: Simple mode - just user and db
                processSimpleMode();
            } else if (vaultParamCount == 4) {
                // Mode 2: Full vault parameters mode
                processFullVaultMode();
            } else {
                // Invalid: partial vault parameters
                spec.commandLine().getErr().println("[ERROR] Invalid parameter combination.");
                spec.commandLine().getErr().println("Supported modes:");
                spec.commandLine().getErr().println("  1. Simple mode: --user <user> --db <db> --simulate/--real");
                spec.commandLine().getErr().println(
                        "  2. Full vault mode: --user <user> --db <db> --vault-url <url> --role-id <id> --secret-id <id> --ait <ait> --simulate/--real");
                spec.commandLine().getErr()
                        .println("Current vault parameters provided: " + vaultParamCount + " (need 0 or 4)");
                return 1;
            }

            return 0;

        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg == null || errorMsg.trim().isEmpty()) {
                errorMsg = e.getClass().getSimpleName() + " occurred";
                if (e.getCause() != null) {
                    String causeMsg = e.getCause().getMessage();
                    if (causeMsg != null && !causeMsg.trim().isEmpty()) {
                        errorMsg += " - Caused by: " + causeMsg;
                    }
                }
            }
            spec.commandLine().getErr().println("[ERROR] " + errorMsg);
            if (logger.isDebugEnabled()) {
                logger.error("Exception in VaultDemoCli", e);
            }
            return 1;
        }
    }

    private void processSimpleMode() {
        spec.commandLine().getOut().println("[INFO] Simple mode: Looking up vault config for user: " + user);

        // Get vault parameters for the user
        Map<String, Object> vaultParams = VaultClient.getVaultParamsForUser(user);
        if (vaultParams.isEmpty()) {
            spec.commandLine().getOut().println("[ERROR] No vault entry found for user: " + user);
            return;
        }

        // Extract vault configuration
        String vaultBaseUrl = (String) vaultParams.get("base-url");
        String aitVal = (String) vaultParams.get("ait");

        if (vaultBaseUrl == null || aitVal == null) {
            spec.commandLine().getOut().println("[ERROR] Incomplete vault configuration for user: " + user);
            return;
        }

        generateOutput(vaultBaseUrl, aitVal);
    }

    private void processFullVaultMode() {
        spec.commandLine().getOut().println("[INFO] Full vault mode: Using provided vault parameters");
        generateOutput(vaultUrl, ait);
    }

    private void generateOutput(String baseUrl, String aitValue) {
        // Generate full HTTPS URLs for vault
        String fullVaultBaseUrl = "https://" + baseUrl + ".bankofamerica.com";

        // Print Vault Auth URL
        spec.commandLine().getOut().println(VAULT_AUTH_HEADER);
        spec.commandLine().getOut().println(fullVaultBaseUrl + "/v1/auth/approle/login");

        // Print Vault Secret URL
        spec.commandLine().getOut().println(VAULT_SECRET_HEADER);
        String secretUrl = String.format("%s/v1/secrets/database/oracle/static-creds/%s-%s-%s",
                fullVaultBaseUrl, aitValue, db.toLowerCase(), user.toLowerCase());
        spec.commandLine().getOut().println(secretUrl);

        // Print LDAP Connection String
        printLdapConnectionString();

        // If real mode, attempt to fetch password
        if (real) {
            try {
                VaultClient client = new VaultClient();
                String password;
                if (vaultUrl != null) {
                    // Full vault mode - use all provided parameters
                    password = client.fetchOraclePassword(vaultUrl, roleId, secretId, db, ait, user);
                } else {
                    // Simple mode - use user lookup
                    password = client.fetchOraclePassword(user);
                }

                spec.commandLine().getOut().println("=== VAULT PASSWORD DECRYPTION ===");
                if (password != null) {
                    spec.commandLine().getOut().println("Success: true");
                } else {
                    spec.commandLine().getOut().println("Success: false");
                }
            } catch (Exception e) {
                spec.commandLine().getOut().println("=== VAULT PASSWORD DECRYPTION ===");
                spec.commandLine().getOut().println("Success: false");
            }
        }
    }

    private void printLdapConnectionString() {
        try {
            YamlConfig appConfig = new YamlConfig(System.getProperty("config.file", "application.yaml"));
            String ldapServers = appConfig.getRawValue("databases.oracle.connection-string.ldap.servers");
            String ldapContext = appConfig.getRawValue("databases.oracle.connection-string.ldap.context");
            String ldapPort = appConfig.getRawValue("databases.oracle.connection-string.ldap.port");

            spec.commandLine().getOut().println(LDAP_CONNECTION_HEADER);

            if (ldapServers != null && ldapContext != null && ldapPort != null) {
                // Split servers and build LDAP URLs
                String[] servers = ldapServers.split(",");
                StringBuilder ldapUrl = new StringBuilder("jdbc:oracle:thin:@ldap://");

                for (int i = 0; i < servers.length; i++) {
                    if (i > 0) {
                        ldapUrl.append(" ldap://");
                    }
                    ldapUrl.append(servers[i].trim())
                            .append(":")
                            .append(ldapPort)
                            .append("/")
                            .append(db)
                            .append(",")
                            .append(ldapContext);
                }

                spec.commandLine().getOut().println(ldapUrl.toString());
            } else {
                spec.commandLine().getOut().println("LDAP URL: [ERROR] Missing LDAP configuration in application.yaml");
                spec.commandLine().getOut().println("  Required: databases.oracle.connection-string.ldap.servers");
                spec.commandLine().getOut().println("  Required: databases.oracle.connection-string.ldap.context");
                spec.commandLine().getOut().println("  Required: databases.oracle.connection-string.ldap.port");
            }
        } catch (Exception e) {
            spec.commandLine().getOut().println(LDAP_CONNECTION_HEADER);
            spec.commandLine().getOut().println("LDAP URL: [ERROR] Could not load application.yaml: " +
                    (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    private void printParameterDebug() {
        spec.commandLine().getOut().println("[DEBUG] Parsed parameters:");
        spec.commandLine().getOut().println("  user: " + user + (user != null ? " (not null ✓)" : " (null ✗)"));
        spec.commandLine().getOut().println("  db: " + db + (db != null ? " (not null ✓)" : " (null ✗)"));
        spec.commandLine().getOut().println("  vault-url: " + vaultUrl);
        spec.commandLine().getOut().println("  role-id: " + roleId);
        spec.commandLine().getOut().println("  secret-id: " + secretId);
        spec.commandLine().getOut().println("  ait: " + ait);
        spec.commandLine().getOut().println("  real: " + real);
        spec.commandLine().getOut().println("  simulate: " + simulate + (simulate ? " ✓" : ""));
    }
}