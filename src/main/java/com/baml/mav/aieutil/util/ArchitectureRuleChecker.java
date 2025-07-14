package com.baml.mav.aieutil.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ArchitectureRuleChecker {

    public record RuleViolation(String rule, String file, String method, String description) {
    }

    private static final String AIEUTIL_CLI_SERVICE = "AieUtilCliService.java";
    private static final String DATABASE_SERVICE = "DatabaseService.java";
    private static final String PROCEDURE_EXECUTOR = "ProcedureExecutor.java";
    private static final String SQL_EXECUTOR = "SqlExecutor.java";
    private static final String PARSE_INPUT_PARAMETERS = "parseInputParameters";
    private static final String GET_JDBC_TYPE = "getJdbcType";
    private static final String PATH_PREFIX = "src/main/java/com/baml/mav/aieutil/";

    public static void main(String[] args) {
        String projectRoot = args.length > 0 ? args[0] : ".";
        List<RuleViolation> violations = checkProject(projectRoot);

        if (violations.isEmpty()) {
            System.out.println("✅ All architecture rules followed!"); // NOSONAR
        } else {

            System.out.println("❌ Found " + violations.size() + " rule violations:"); // NOSONAR
            violations.forEach(v -> System.out.println("  - " + v.file() + ":" + v.method() + " - " + v.description())); // NOSONAR
        }
    }

    public static List<RuleViolation> checkProject(String projectRoot) {
        List<RuleViolation> violations = new ArrayList<>();

        // Check specific files
        checkFile(Paths.get(projectRoot, PATH_PREFIX + AIEUTIL_CLI_SERVICE), violations);
        checkFile(Paths.get(projectRoot, PATH_PREFIX + DATABASE_SERVICE), violations);
        checkFile(Paths.get(projectRoot, PATH_PREFIX + PROCEDURE_EXECUTOR), violations);
        checkFile(Paths.get(projectRoot, PATH_PREFIX + SQL_EXECUTOR), violations);

        // Check for duplicate logic across files
        checkDuplicateLogic();

        return violations;
    }

    private static void checkFile(Path file, List<RuleViolation> violations) {
        if (!Files.exists(file))
            return;

        try {
            String content = Files.readString(file);
            String fileName = file.getFileName().toString();

            // CLI Service Rules
            if (fileName.equals(AIEUTIL_CLI_SERVICE)) {
                checkCliServiceRules(content, violations);
            }

            // Database Service Rules
            if (fileName.equals(DATABASE_SERVICE)) {
                checkDatabaseServiceRules(content, violations);
            }

            // Procedure Executor Rules
            if (fileName.equals(PROCEDURE_EXECUTOR)) {
                checkProcedureExecutorRules(content, violations);
            }

        } catch (IOException e) {
            violations.add(new RuleViolation("FILE_READ_ERROR", file.toString(), "N/A",
                    "Could not read file: " + e.getMessage()));
        }
    }

    private static void checkCliServiceRules(String content, List<RuleViolation> violations) {
        // CLI service should NOT have connection logic
        if (content.contains("ConnectionStrategy") || content.contains("makeConnectionInfo")) {
            violations.add(new RuleViolation("CLI_CONNECTION_LOGIC", AIEUTIL_CLI_SERVICE, "ConnectionStrategy",
                    "CLI service should not contain connection building logic"));
        }

        // CLI service should NOT have parameter parsing
        if (content.contains(PARSE_INPUT_PARAMETERS) || content.contains("parseOutputParameters")
                || content.contains(GET_JDBC_TYPE)) {
            violations.add(new RuleViolation("CLI_PARAMETER_PARSING", AIEUTIL_CLI_SERVICE, "Parameter parsing",
                    "CLI service should not contain parameter parsing logic"));
        }

        // CLI service should only pass raw arguments
        if (!content.contains("DatabaseService.create(")) {
            violations.add(new RuleViolation("CLI_ORCHESTRATION", AIEUTIL_CLI_SERVICE, "runSql/runProcedure",
                    "CLI service should use DatabaseService.create() to pass raw arguments"));
        }
    }

    private static void checkDatabaseServiceRules(String content, List<RuleViolation> violations) {
        // DatabaseService should have connection building
        if (!content.contains("buildConnectionString")) {
            violations.add(new RuleViolation("DB_CONNECTION_BUILDING", DATABASE_SERVICE, "Connection",
                    "DatabaseService should handle connection string building"));
        }

        // DatabaseService should delegate to executors
        if (!content.contains("sqlExecutor.executeSql") || !content.contains("procedureExecutor.executeProcedure")) {
            violations.add(new RuleViolation("DB_DELEGATION", DATABASE_SERVICE, "Execution",
                    "DatabaseService should delegate to specialized executors"));
        }
    }

    private static void checkProcedureExecutorRules(String content, List<RuleViolation> violations) {
        // ProcedureExecutor should handle parameter parsing
        if (!content.contains("ProcedureParam.fromString")) {
            violations.add(new RuleViolation("PROC_PARAM_PARSING", PROCEDURE_EXECUTOR, "Parameter parsing",
                    "ProcedureExecutor should handle parameter parsing"));
        }

        // ProcedureExecutor should have JDBC type mapping
        if (!content.contains(GET_JDBC_TYPE)) {
            violations.add(new RuleViolation("PROC_JDBC_TYPES", PROCEDURE_EXECUTOR, "JDBC types",
                    "ProcedureExecutor should handle JDBC type mapping"));
        }
    }

    private static void checkDuplicateLogic() {
        // (No-op: duplicate logic checks are not implemented)
    }
}