package com.baml.mav.aieutil;

import org.apache.logging.log4j.Logger;

import com.baml.mav.aieutil.util.LoggingUtils;

import java.sql.*;
import java.util.*;

import org.h2.Driver;

public class DatabaseService {
    private final String user;
    private final String password;
    private final String connectString;
    private final Logger log = LoggingUtils.getLogger(DatabaseService.class);

    public DatabaseService(String user, String password, String connectString) {
        this.user = user;
        this.password = password;
        this.connectString = connectString;
        
        // Load H2 driver if needed
        if (connectString.startsWith("jdbc:h2:")) {
            try {
                Class.forName("org.h2.Driver");
                log.info("H2 driver loaded successfully");
            } catch (ClassNotFoundException e) {
                log.error("Failed to load H2 driver", e);
            }
        }
        
        log.info("DatabaseService: user={}, connectString={}", user, connectString);
    }

    public static DatabaseService of(String user, String password, String connectString) {
        return new DatabaseService(user, password, connectString);
    }

    public void runSql(String sql) throws SQLException {
        log.info("Executing SQL: {}", sql);
        try (Connection conn = DriverManager.getConnection(connectString, user, password);
             Statement stmt = conn.createStatement()) {
            boolean isResultSet = stmt.execute(sql);
            if (isResultSet) {
                try (ResultSet rs = stmt.getResultSet()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int cols = meta.getColumnCount();
                    while (rs.next()) {
                        for (int i = 1; i <= cols; i++) {
                            System.out.print(meta.getColumnLabel(i) + "=" + rs.getObject(i) + " ");
                        }
                        System.out.println();
                    }
                }
            } else {
                int count = stmt.getUpdateCount();
                System.out.println("Rows affected: " + count);
            }
        }
    }

    public void runSqlScript(String script) throws SQLException {
        log.info("Executing SQL script");
        try (Connection conn = DriverManager.getConnection(connectString, user, password);
             Statement stmt = conn.createStatement()) {
            String[] stmts = script.split(";");
            for (String s : stmts) {
                String sql = s.trim();
                if (sql.isEmpty()) continue;
                log.info("Script Statement: {}", sql);
                boolean isResultSet = stmt.execute(sql);
                if (isResultSet) {
                    try (ResultSet rs = stmt.getResultSet()) {
                        ResultSetMetaData meta = rs.getMetaData();
                        while (rs.next()) {
                            for (int i = 1; i <= meta.getColumnCount(); i++) {
                                System.out.print(meta.getColumnLabel(i) + "=" + rs.getObject(i) + " ");
                            }
                            System.out.println();
                        }
                    }
                } else {
                    int count = stmt.getUpdateCount();
                    System.out.println("Rows affected: " + count);
                }
            }
        }
    }

    /**
     * Run a stored procedure. Example: procFullName = "SCHEMA.PROCNAME"
     * inputParams is a LinkedHashMap paramName -> value
     * outputParams is a LinkedHashMap paramName -> java.sql.Types.*
     */
    public Map<String, Object> runProcedure(
            String procFullName,
            Map<String, Object> inputParams,
            Map<String, Integer> outputParams
    ) throws SQLException {
        log.info("Executing procedure: {}", procFullName);
        int totalParams = (inputParams != null ? inputParams.size() : 0)
                + (outputParams != null ? outputParams.size() : 0);
        StringJoiner q = new StringJoiner(",", "(", ")");
        for (int i = 0; i < totalParams; i++) q.add("?");
        String callSql = "{call " + procFullName + q + "}";
        try (Connection conn = DriverManager.getConnection(connectString, user, password);
             CallableStatement call = conn.prepareCall(callSql)) {
            int idx = 1;
            if (inputParams != null) {
                for (Object v : inputParams.values()) call.setObject(idx++, v);
            }
            if (outputParams != null) {
                for (var entry : outputParams.entrySet())
                    call.registerOutParameter(idx++, entry.getValue());
            }
            call.execute();
            Map<String, Object> out = new LinkedHashMap<>();
            int outIdx = (inputParams != null ? inputParams.size() : 0) + 1;
            if (outputParams != null) {
                for (var entry : outputParams.entrySet()) {
                    out.put(entry.getKey(), call.getObject(outIdx++));
                }
            }
            // Print in method, but also return the map
            for (var entry : out.entrySet()) {
                System.out.println(entry.getKey() + ": " + entry.getValue());
            }
            return out;
        }
    }
}   