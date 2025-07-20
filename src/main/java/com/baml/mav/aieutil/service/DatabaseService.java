package com.baml.mav.aieutil.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

import com.baml.mav.aieutil.database.ConnectionStringGenerator;
import com.baml.mav.aieutil.database.ProcedureExecutor;
import com.baml.mav.aieutil.util.LoggingUtils;

public class DatabaseService {
    private static final ProcedureExecutor procedureExecutor = new ProcedureExecutor();

    private DatabaseService() {
        // Utility class - prevent instantiation
    }

    public static Connection createConnection(String type, String database, String user, String password)
            throws SQLException {
        // Validate inputs
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("Database type cannot be null or empty");
        }
        if (database == null || database.trim().isEmpty()) {
            throw new IllegalArgumentException("Database name cannot be null or empty");
        }
        if (user == null || user.trim().isEmpty()) {
            throw new IllegalArgumentException("Database user cannot be null or empty");
        }
        if (password == null) {
            throw new IllegalArgumentException("Database password cannot be null");
        }

        // If host is null, it gets the LDAP connection string
        String connectionUrl = ConnectionStringGenerator.createConnectionString(type, database, user, password, null)
                .getUrl();

        LoggingUtils.logDatabaseConnection(type, database, user);

        try {
            Connection conn = DriverManager.getConnection(connectionUrl, user, password);
            LoggingUtils.logStructuredError("database_connection", "create", "SUCCESS",
                    "Successfully connected to database", null);
            return conn;
        } catch (SQLException e) {
            LoggingUtils.logStructuredError("database_connection", "create", "FAILED",
                    "Failed to connect to database: " + e.getMessage(), e);
            throw e;
        }
    }

    public static Map<String, Object> executeProcedure(Connection conn, String procedure, String input, String output)
            throws SQLException {
        // Validate inputs
        if (conn == null) {
            throw new IllegalArgumentException("Database connection cannot be null");
        }
        if (procedure == null || procedure.trim().isEmpty()) {
            throw new IllegalArgumentException("Procedure name cannot be null or empty");
        }

        LoggingUtils.logProcedureExecution(procedure, input, output);

        Map<String, Object> result = procedureExecutor.executeProcedureWithStrings(conn, procedure, input, output);

        LoggingUtils.logProcedureExecutionSuccess(procedure);
        return result;
    }
}