package com.baml.mav.aieutil;

import java.sql.DriverManager;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import org.apache.logging.log4j.Logger;

import com.baml.mav.aieutil.database.ConnectionManager;
import com.baml.mav.aieutil.database.ProcedureExecutor;
import com.baml.mav.aieutil.util.ExceptionUtils;
import com.baml.mav.aieutil.util.LoggingUtils;
import com.baml.mav.aieutil.util.VaultClient;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "exec-proc", mixinStandardHelpOptions = true, description = "Vault-authenticated procedure execution")
public class ExecProcCmd implements Callable<Integer> {

    private static final Logger logger = LoggingUtils.getLogger(ExecProcCmd.class);

    @Option(names = { "-t", "--type" }, description = "Database type", defaultValue = "oracle")
    String type;

    @Option(names = { "-d", "--database" }, description = "Database name", required = true)
    String database;

    @Option(names = { "-u", "--user" }, description = "Database username", required = true)
    String user;

    @Option(names = "--vault-url", description = "Vault base URL")
    String vaultUrl;

    @Option(names = "--role-id", description = "Vault role ID")
    String roleId;

    @Option(names = "--secret-id", description = "Vault secret ID")
    String secretId;

    @Option(names = "--ait", description = "AIT")
    String ait;

    @Parameters(index = "0", description = "Stored procedure name (e.g., MAV_OWNER.TemplateTable.Onehadoop_proc)", arity = "0..1")
    String procedure;

    @Option(names = "--input", description = "Input parameters (name:type:value,name:type:value)")
    String input;

    @Option(names = "--output", description = "Output parameters (name:type,name:type)")
    String output;

    @Spec
    CommandSpec spec;

    public static void main(String[] args) {
        if (logger.isDebugEnabled()) {
            logger.debug("java.home: {}", System.getProperty("java.home"));
        }
        System.exit(new CommandLine(new ExecProcCmd()).execute(args));
    }

    @Override
    public Integer call() {
        if (user == null || user.isBlank() || database == null || database.isBlank()) {
            spec.commandLine().getErr().println("[ERROR] --user and --database are required");
            return 1;
        }

        try {
            String password = resolvePassword();

            if (procedure != null && !procedure.isBlank()) {
                executeProcedure(password);
            } else {
                spec.commandLine().getOut().println("=== VAULT PASSWORD DECRYPTION ===");
                spec.commandLine().getOut().println("Success: " + (password != null));
            }
            return 0;
        } catch (Exception e) {
            logger.error("Operation failed", e);
            spec.commandLine().getErr().println("[ERROR] " + e.getMessage());
            return 1;
        }
    }

    private void executeProcedure(String password) {
        try {
            var connInfo = ConnectionManager.createConnection(type, database, user, password, null);

            Map<String, Object> result = new ProcedureExecutor(
                    () -> DriverManager.getConnection(connInfo.url(), connInfo.user(), connInfo.password()))
                    .executeProcedureWithStrings(procedure, input, output);

            if (result.size() == 1) {
                Object value = result.values().iterator().next();
                spec.commandLine().getOut().println(value != null ? value.toString() : "null");
            } else {
                result.forEach((key, value) -> spec.commandLine().getOut().println(key + ": " + value));
            }
        } catch (Exception e) {
            ExceptionUtils.logAndRethrow(e, "Failed to execute procedure: " + procedure);
        }
    }

    private String resolvePassword() {
        if (hasDirectVaultParams()) {
            logger.info("Using direct vault parameters");
            VaultClient client = new VaultClient();
            return client.fetchOraclePassword(vaultUrl, roleId, secretId, database, ait, user);
        }

        Map<String, Object> vaultParams = VaultClient.getVaultParamsForUser(user);
        if (!vaultParams.isEmpty()) {
            logger.info("Found vault config for user: {}", user);
            VaultClient client = new VaultClient();
            return client.fetchOraclePassword(user);
        }

        spec.commandLine().getOut().println("[INFO] No vault config found for user: " + user);
        return promptForPassword();
    }

    private boolean hasDirectVaultParams() {
        return Stream.of(vaultUrl, roleId, secretId, ait)
                .allMatch(param -> param != null && !param.isBlank());
    }

    private String promptForPassword() {
        try {
            spec.commandLine().getOut().print("Enter password: ");
            if (System.console() != null) {
                return new String(System.console().readPassword());
            } else {
                try (java.util.Scanner scanner = new java.util.Scanner(System.in)) {
                    return scanner.nextLine();
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to read password from console: {}", e.getMessage());
            return null;
        }
    }
}