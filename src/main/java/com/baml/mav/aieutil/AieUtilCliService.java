package com.baml.mav.aieutil;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.logging.log4j.Logger;

import com.baml.mav.aieutil.database.DatabaseService;
import com.baml.mav.aieutil.database.SqlExecutor;
import com.baml.mav.aieutil.util.CliMessages;
import com.baml.mav.aieutil.util.ExceptionUtils;
import com.baml.mav.aieutil.util.LoggingUtils;
import com.baml.mav.aieutil.util.VaultClient;
import com.baml.mav.aieutil.util.YamlConfig;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "aieutil", mixinStandardHelpOptions = true, version = "1.0.0", description = "Minimal Oracle CLI utility")
public class AieUtilCliService implements Callable<Integer> {
    private static final YamlConfig appConfig = new YamlConfig(System.getProperty("config.file", "application.yaml"));
    private static final Logger log = LoggingUtils.getLogger(AieUtilCliService.class);

    @Option(names = { "-t", "--type" }, description = "Database type (oracle/h2)", defaultValue = "oracle")
    private String type;

    @Option(names = { "-d", "--database" }, description = "Database name", required = true)
    private String database;

    @Option(names = { "-u", "--user" }, description = "Username", required = true)
    private String user;

    @Option(names = { "-p", "--password" }, description = "Password (optional if using Vault)")
    private String password;

    @Option(names = { "--vault" }, description = "Use Vault for password lookup")
    private boolean useVault;

    @Option(names = { "--role" }, description = "Role")
    private String role;

    @Option(names = { "--secret" }, description = "Secret")
    private String secret;

    @Option(names = { "--ait" }, description = "AIT")
    private String ait;

    @Option(names = { "--host" }, description = "Host (for JDBC connection)")
    private String host;

    @Option(names = { "--procedure" }, description = "Stored procedure name")
    private String procedure;

    @Option(names = { "--sql" }, description = "SQL statement")
    private String sql;

    @Option(names = { "--script" }, description = "SQL script file")
    private String script;

    @Option(names = { "--input" }, description = "Input parameters (name:type:value,name:type:value)")
    private String input;

    @Option(names = { "--output" }, description = "Output parameters (name:type,name:type)")
    private String output;

    @Option(names = { "--print-config" }, description = "Print loaded configuration")
    private boolean printConfig;

    @Option(names = { "--sample-connect" }, description = "Print sample connection string")
    private boolean sampleConnect;

    @Option(names = { "--help-examples" }, description = "Show usage examples")
    private boolean helpExamples;

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new AieUtilCliService());
        cmd.setOut(new java.io.PrintWriter(System.out, true));
        cmd.setErr(new java.io.PrintWriter(System.err, true));
        int exitCode = executeWithCmd(cmd);
        System.exit(exitCode);
    }

    public static int executeWithCmd(CommandLine cmd) {
        try {
            return ((AieUtilCliService) cmd.getCommand()).callWithCmd(cmd);
        } catch (Exception e) {
            ExceptionUtils.logAndRethrow(e, "CLI execution error");
            return 1;
        }
    }

    @Override
    public Integer call() throws Exception {
        return callWithCmd(new CommandLine(this));
    }

    public Integer callWithCmd(CommandLine cmd) {
        try {
            return switch (getCommandType()) {
                case CONFIG -> {
                    printYamlConfig(cmd);
                    yield 0;
                }
                case SAMPLE -> {
                    printSampleConnectString(cmd);
                    yield 0;
                }
                case SQL -> {
                    runSql(cmd);
                    yield 0;
                }
                case SCRIPT -> {
                    runSqlScript(cmd);
                    yield 0;
                }
                case PROCEDURE -> {
                    runProcedure(cmd);
                    yield 0;
                }
                case HELP_EXAMPLES -> {
                    printHelpExamples(cmd);
                    yield 0;
                }
                case NONE -> throw new ExceptionUtils.CliUsageException(
                        "Must specify --procedure, --sql, --script, or --help-examples");
            };
        } catch (Exception e) {
            ExceptionUtils.logAndRethrow(e, "CLI execution error");
            return 1;
        }
    }

    private enum CommandType {
        CONFIG, SAMPLE, SQL, SCRIPT, PROCEDURE, HELP_EXAMPLES, NONE
    }

    private CommandType getCommandType() {
        if (printConfig)
            return CommandType.CONFIG;
        if (sampleConnect)
            return CommandType.SAMPLE;
        if (helpExamples)
            return CommandType.HELP_EXAMPLES;
        if (sql != null)
            return CommandType.SQL;
        if (script != null)
            return CommandType.SCRIPT;
        if (procedure != null)
            return CommandType.PROCEDURE;
        return CommandType.NONE;
    }

    private void runSql(CommandLine cmd) {
        try {
            String resolvedPassword = resolvePassword(cmd);
            DatabaseService db = DatabaseService.create(type, database, user, resolvedPassword, host);
            var result = db.runSql(sql, List.of());
            printSqlResult(cmd, result);
            log.info("SQL executed successfully");
        } catch (Exception e) {
            ExceptionUtils.logAndRethrow(e, "SQL error");
        }
    }

    private void runSqlScript(CommandLine cmd) {
        try {
            String resolvedPassword = resolvePassword(cmd);
            DatabaseService db = DatabaseService.create(type, database, user, resolvedPassword, host);
            String scriptContent = new String(Files.readAllBytes(Paths.get(script)));
            db.runSqlScript(scriptContent, result -> printSqlResult(cmd, result));
            log.info("SQL script executed successfully");
        } catch (Exception e) {
            ExceptionUtils.logAndRethrow(e, "SQL script error");
        }
    }

    private void runProcedure(CommandLine cmd) {
        try {
            String resolvedPassword = resolvePassword(cmd);
            DatabaseService db = DatabaseService.create(type, database, user, resolvedPassword, host);
            var result = db.executeProcedure(procedure, null, null);
            printProcedureResult(cmd, result);
            log.info("Procedure executed successfully");
        } catch (Exception e) {
            ExceptionUtils.logAndRethrow(e, "Procedure error");
        }
    }

    private String resolvePassword(CommandLine cmd) {
        if (password != null && !password.isEmpty()) {
            return password;
        }

        if (useVault) {
            VaultClient client = new VaultClient();
            String vaultPassword = client.fetchOraclePassword(user);
            if (vaultPassword != null) {
                return vaultPassword;
            }
            throw new ExceptionUtils.ConfigurationException("No password found in Vault for user: " + user);
        }

        throw new ExceptionUtils.ConfigurationException(
                "Password required. Provide --password or use --vault for Vault lookup.");
    }

    private void printYamlConfig(CommandLine cmd) {
        cmd.getOut().println("=== Loaded application.yaml ===");
        cmd.getOut().println(appConfig.getAll());
        cmd.getOut().println("==============================");
    }

    private void printSampleConnectString(CommandLine cmd) {
        cmd.getOut().println("=== SAMPLE ORACLE CONNECT STRING ===");
        cmd.getOut().println("User: testuser");
        cmd.getOut().println(
                "Connect string: jdbc:oracle:thin:@ldap://ldap.example.com:389/ORCL,cn=OracleContext,dc=example,dc=com");
        cmd.getOut().println("==============================");
    }

    private void printHelpExamples(CommandLine cmd) {
        cmd.getOut().println(CliMessages.HELP_EXAMPLES);
    }

    private void printSqlResult(CommandLine cmd, SqlExecutor.SqlResult result) {
        if (result.isResultSet()) {
            for (var row : result.rows()) {
                for (var entry : row.entrySet()) {
                    cmd.getOut().print(entry.getKey() + "=" + entry.getValue() + " ");
                }
                cmd.getOut().println();
            }
        } else {
            cmd.getOut().println("Rows affected: " + result.updateCount());
        }
    }

    private void printProcedureResult(CommandLine cmd, java.util.Map<String, Object> result) {
        for (var entry : result.entrySet()) {
            cmd.getOut().println(entry.getKey() + ": " + entry.getValue());
        }
    }
}