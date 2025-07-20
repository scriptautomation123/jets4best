package com.baml.mav.aieutil.util;

import java.io.PrintStream;
import java.sql.SQLException;

import picocli.CommandLine;

public final class ExceptionUtils {
    private static final String ERROR_PREFIX = "[ERROR] ";

    private ExceptionUtils() {
        // Utility class - prevent instantiation
    }

    public static void logAndRethrow(Exception e, String operation) {
        LoggingUtils.logStructuredError("exception", operation, null, e.getMessage(), e);
        throw new ConfigurationException(
                "Failed to " + operation + ": " + e.getMessage(), e);
    }

    public static RuntimeException wrap(Exception e, String message) {
        return new ConfigurationException(message, e);
    }

    public static RuntimeException handleSQLException(
            SQLException e, String operation) {
        String errorType = getErrorTypeForSQLException(e);
        String message = String.format("Failed to %s: %s", operation, e.getMessage());

        org.apache.logging.log4j.ThreadContext.put("operation", operation);
        org.apache.logging.log4j.ThreadContext.put("errorType", errorType);
        LoggingUtils.logStructuredError("sql_exception", operation, errorType, e.getMessage(), e);
        org.apache.logging.log4j.ThreadContext.clearAll();

        return new ConfigurationException(message, e);
    }

    /**
     * Handle CLI exceptions - log once, print user-friendly message
     */
    public static int handleCliException(Exception e, String operation, PrintStream errorStream) {
        if (e instanceof SQLException) {
            return handleCliSQLException((SQLException) e, operation, errorStream);
        } else if (e instanceof ConfigurationException) {
            return handleCliConfigurationException((ConfigurationException) e, operation, errorStream);
        } else if (e instanceof CliUsageException) {
            return handleCliUsageException((CliUsageException) e, errorStream);
        } else {
            // Generic exception handling
            LoggingUtils.logStructuredError("cli_exception", operation, "UNEXPECTED_ERROR", e.getMessage(), e);
            String userMessage = translateExceptionForCli(e);
            errorStream.println(ERROR_PREFIX + userMessage);
            return 1;
        }
    }

    /**
     * Handle SQL exceptions specifically for CLI
     */
    public static int handleCliSQLException(SQLException e, String operation, PrintStream errorStream) {
        String errorType = getErrorTypeForSQLException(e);
        String userMessage = translateSQLExceptionForCli(e);

        // Log once with structured data
        LoggingUtils.logStructuredError("sql_exception", operation, errorType, e.getMessage(), e);

        // Print user-friendly message
        errorStream.println(ERROR_PREFIX + userMessage);

        return 1;
    }

    /**
     * Handle configuration exceptions for CLI
     */
    public static int handleCliConfigurationException(ConfigurationException e, String operation,
            PrintStream errorStream) {
        // Log once
        LoggingUtils.logStructuredError("configuration_error", operation, "CONFIG_ERROR", e.getMessage(), e);

        // Print user-friendly message
        errorStream.println(ERROR_PREFIX + "Configuration error: " + e.getMessage());

        return 1;
    }

    /**
     * Handle CLI usage exceptions (user errors)
     */
    public static int handleCliUsageException(CliUsageException e, PrintStream errorStream) {
        // For usage errors, don't log as errors - just print to user
        errorStream.println(ERROR_PREFIX + e.getMessage());
        return 1;
    }

    private static String translateSQLExceptionForCli(SQLException e) {
        String sqlState = e.getSQLState();
        if (sqlState == null) {
            return "Database operation failed: " + e.getMessage();
        }

        String sqlStatePrefix = sqlState.substring(0, 2);
        switch (sqlStatePrefix) {
            case "08":
                return "Cannot connect to database server";
            case "28000":
                return "Invalid username or password";
            case "42000":
                return "Invalid SQL syntax in procedure";
            case "42703":
                return "Procedure parameter not found";
            case "23":
                return "Database constraint violation";
            case "22":
                return "Invalid data provided";
            default:
                return "Database operation failed: " + e.getMessage();
        }
    }

    private static String translateExceptionForCli(Exception e) {
        if (e instanceof IllegalArgumentException) {
            return "Invalid parameter: " + e.getMessage();
        } else if (e instanceof SecurityException) {
            return "Access denied: " + e.getMessage();
        } else {
            return "Operation failed: " + e.getMessage();
        }
    }

    static String getErrorTypeForSQLException(SQLException e) {
        String sqlState = e.getSQLState();
        if (sqlState == null) {
            return "OP_QUERY";
        }

        String sqlStatePrefix = sqlState.substring(0, 2);
        switch (sqlStatePrefix) {
            case "08":
                return "CONN_FAILED"; // Connection errors
            case "42":
                return "SYNTAX_ERROR"; // Syntax errors
            case "23":
                return "CONSTRAINT_VIOLATION"; // Constraint violations
            case "22":
                return "DATA_ERROR"; // Data errors
            default:
                return "OP_QUERY"; // Default
        }
    }

    public static class CliUsageException extends RuntimeException {
        public CliUsageException(String message) {
            super(message);
        }
    }

    public static class ConfigurationException extends RuntimeException {
        public ConfigurationException(String message) {
            super(message);
        }

        public ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class ExecutionExceptionHandler implements CommandLine.IExecutionExceptionHandler {
        @Override
        public int handleExecutionException(Exception ex, CommandLine cmd, CommandLine.ParseResult parseResult) {
            LoggingUtils.logMinimalError(ex);
            return CommandLine.ExitCode.SOFTWARE;
        }
    }

    public static class ParameterExceptionHandler implements CommandLine.IParameterExceptionHandler {
        @Override
        public int handleParseException(CommandLine.ParameterException ex, String[] args) {
            CommandLine cmd = ex.getCommandLine();
            cmd.getErr().println(ex.getMessage());
            if (!CommandLine.UnmatchedArgumentException.printSuggestions(ex, cmd.getErr())) {
                ex.getCommandLine().usage(cmd.getErr());
            }
            return CommandLine.ExitCode.USAGE;
        }
    }
}
