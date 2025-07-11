package com.baml.mav.aieutil.util;



import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import java.sql.SQLException;

/**
 * Provides standardized exception handling for database operations.
 * Centralizes error handling logic to ensure consistent error messages and logging.
 */
public final class ExceptionUtils {
    
    private ExceptionUtils() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Handles a SQLException by logging it and creating an appropriate DatabaseException.
     *
     * @param e The SQLException to handle
     * @param operation A description of the operation that failed
     * @param type The error type
     * @param logger The logger to use
     * @return A new DatabaseException
     */
    public static RuntimeException handleSQLException(
            SQLException e, String operation, String type, Logger logger) {
        String message = String.format("Failed to %s: %s", operation, e.getMessage());
        ThreadContext.put("operation", operation);
        ThreadContext.put("errorType", type);
        logger.error(message, e);
        ThreadContext.clearAll();
        return new RuntimeException(message, e);
    }
    
    /**
     * Logs an exception and rethrows it as a DatabaseException.
     * This method is useful for catch blocks to standardize exception handling.
     *
     * @param e The exception to handle
     * @param operation A description of the operation that failed
     * @param logger The logger to use
     * @throws RuntimeException The wrapped exception
     */
    public static void logAndRethrow(Exception e, String operation, Logger logger) {
        if (e instanceof SQLException sqlEx) {
            throw handleSQLException(sqlEx, operation, getErrorTypeForSQLException(sqlEx), logger);
        } else {
            ThreadContext.put("operation", operation);
            logger.error("Error during {}: {}", operation, e.getMessage(), e);
            ThreadContext.clearAll();
            throw new RuntimeException(
                "Failed to " + operation + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Determines the most appropriate ErrorType for a SQLException.
     * This method analyzes the exception to categorize it correctly.
     *
     * @param e The SQLException to analyze
     * @return The appropriate ErrorType
     */
    private static String getErrorTypeForSQLException(SQLException e) {
        // SQL State patterns
        String sqlState = e.getSQLState();
        if (sqlState == null) {
            return "OP_QUERY";
        }
        
        // Categorize by SQL state
        if (sqlState.startsWith("08")) {
            return "CONN_FAILED"; // Connection errors
        } else if (sqlState.startsWith("42")) {
            return "SYNTAX_ERROR"; // Syntax errors
        } else if (sqlState.startsWith("23")) {
            return "CONSTRAINT_VIOLATION"; // Constraint violations
        } else if (sqlState.startsWith("22")) {
            return "DATA_ERROR"; // Data errors
        } else {
            return "OP_QUERY"; // Default
        }
    }
}
