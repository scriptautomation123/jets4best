package com.baml.mav.aieutil;

import org.apache.logging.log4j.Logger;

import com.baml.mav.aieutil.util.YamlConfig;
import com.baml.mav.aieutil.util.LoggingUtils;

import java.util.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AieUtilCliService {
    private static final YamlConfig appConfig = new YamlConfig("application.yaml");
    private static final Logger log = LoggingUtils.getLogger(AieUtilCliService.class);

    public static class CliParams {
        public final String type, database, user, password, role, secret, ait, host;
        public final String procedure, sql, script, input, output;

        public CliParams(String type, String database, String user, String password,
                         String role, String secret, String ait, String host,
                         String procedure, String sql, String script,
                         String input, String output) {
            this.type = type; this.database = database; this.user = user; this.password = password;
            this.role = role; this.secret = secret; this.ait = ait; this.host = host;
            this.procedure = procedure; this.sql = sql; this.script = script;
            this.input = input; this.output = output;
        }
    }

    private static class ConnInfo {
        final String url, user, password;
        ConnInfo(String url, String user, String password) {
            this.url = url; this.user = user; this.password = password;
        }
    }

    private static ConnInfo makeConnectionInfo(CliParams params) {
        String user = params.user;
        String password = params.password;
        String url;
        boolean useJdbc = params.host != null && !params.host.isBlank();

        if ("h2".equals(params.type)) {
            if (useJdbc) {
                String template = appConfig.getRawValue("databases.h2.connection-string.jdbc-thin.template");
                url = String.format(template, params.database);
            } else {
                String template = appConfig.getRawValue("databases.h2.connection-string.jdbc-thin.template");
                url = String.format(template, "mem:" + params.database);
            }
        } else {
            if (useJdbc) {
                String template = appConfig.getRawValue("databases.oracle.connection-string.jdbc-thin.template");
                int port = Integer.parseInt(appConfig.getRawValue("databases.oracle.connection-string.jdbc-thin.port"));
                String host = params.host;
                url = String.format(template, host, port, params.database);
            } else {
                String template = appConfig.getRawValue("databases.oracle.connection-string.ldap.template");
                int port = Integer.parseInt(appConfig.getRawValue("databases.oracle.connection-string.ldap.port"));
                String context = appConfig.getRawValue("databases.oracle.connection-string.ldap.context");
                String servers = String.join(",",
                        appConfig.getRawValue("databases.oracle.connection-string.ldap.servers").split(","));
                url = String.format(template, servers, port, params.database, context);
            }
        }

        return new ConnInfo(url, user, password);
    }

    private static void printConnectString(ConnInfo info, String dbType) {
        log.info("Using connect string: {}", info.url);
        System.out.println("=== " + dbType.toUpperCase() + " CONNECT STRING ===");
        System.out.println("User: " + info.user);
        System.out.println("Connect string: " + info.url);
        System.out.println("=============================");
    }

    private static Map<String, Object> parseInputParameters(String input) {
        Map<String, Object> inputParams = new LinkedHashMap<>();
        if (input != null && !input.isBlank()) {
            for (String pair : input.split(",")) {
                String[] parts = pair.split(":");
                if (parts.length == 3) inputParams.put(parts[0], parts[2]);
            }
        }
        return inputParams;
    }

    private static Map<String, Integer> parseOutputParameters(String output) {
        Map<String, Integer> outputParamTypes = new LinkedHashMap<>();
        if (output != null && !output.isBlank()) {
            for (String pair : output.split(",")) {
                String[] parts = pair.split(":");
                if (parts.length == 2) outputParamTypes.put(parts[0], java.sql.Types.VARCHAR);
            }
        }
        return outputParamTypes;
    }

    public static void runSql(CliParams params) {
        try {
            var connInfo = makeConnectionInfo(params);
            printConnectString(connInfo, params.type);
            DatabaseService db = DatabaseService.of(connInfo.user, connInfo.password, connInfo.url);
            db.runSql(params.sql);
            log.info("SQL executed successfully");
        } catch (Exception e) {
            log.error("SQL error", e);
            System.err.println("SQL error: " + e.getMessage());
        }
    }

    public static void runSqlScript(CliParams params) {
        try {
            var connInfo = makeConnectionInfo(params);
            printConnectString(connInfo, params.type);
            DatabaseService db = DatabaseService.of(connInfo.user, connInfo.password, connInfo.url);
            
            // Read script file content
            String scriptContent = new String(Files.readAllBytes(Paths.get(params.script)));
            db.runSqlScript(scriptContent);
            log.info("SQL script executed successfully");
        } catch (Exception e) {
            log.error("SQL script error", e);
            System.err.println("SQL script error: " + e.getMessage());
        }
    }

    public static void runProcedure(CliParams params) {
        try {
            var connInfo = makeConnectionInfo(params);
            printConnectString(connInfo, params.type);
            DatabaseService db = DatabaseService.of(connInfo.user, connInfo.password, connInfo.url);
            Map<String, Object> inputParams = parseInputParameters(params.input);
            Map<String, Integer> outputParams = parseOutputParameters(params.output);
            db.runProcedure(params.procedure, inputParams, outputParams);
            log.info("Procedure executed successfully");
        } catch (Exception e) {
            log.error("Procedure error", e);
            System.err.println("Procedure error: " + e.getMessage());
        }
    }

    public static void printYamlConfig() {
        System.out.println("=== Loaded application.yaml ===");
        System.out.println(appConfig.getAll());
        System.out.println("==============================");
    }

    public static void printSampleConnectString() {
        CliParams params = new CliParams(
            null, // type
            "ORCL", // database
            "testuser", // user
            "testpass", // password
            null, // role
            null, // secret
            null, // ait
            null, // host (null = use LDAP, set to e.g. "localhost" for JDBC)
            null, // procedure
            null, // sql
            null, // script
            null, // input
            null  // output
        );
        ConnInfo info = makeConnectionInfo(params);
        printConnectString(info, params.type);
    }
}