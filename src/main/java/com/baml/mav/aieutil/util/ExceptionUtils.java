package com.baml.mav.aieutil.util;

import java.sql.SQLException;

public final class ExceptionUtils {

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

    private static String getErrorTypeForSQLException(SQLException e) {
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
}
