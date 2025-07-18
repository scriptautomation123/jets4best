package com.baml.mav.aieutil.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.logging.log4j.Logger;

import com.baml.mav.aieutil.util.ExceptionUtils;
import com.baml.mav.aieutil.util.LoggingUtils;

public class SqlExecutor {

    public static class SqlResult {
        private final List<Map<String, Object>> rows;
        private final ResultSetMetaData metaData;
        private final int updateCount;
        private final boolean isResultSet;

        public SqlResult(List<Map<String, Object>> rows, ResultSetMetaData metaData, int updateCount, boolean isResultSet) {
            if (isResultSet && rows == null) {
                throw new IllegalArgumentException("Rows cannot be null for result sets");
            }
            this.rows = rows;
            this.metaData = metaData;
            this.updateCount = updateCount;
            this.isResultSet = isResultSet;
        }

        // Factory methods for different use cases
        public static SqlResult ofResultSet(List<Map<String, Object>> rows, ResultSetMetaData metaData) {
            return new SqlResult(rows, metaData, -1, true);
        }

        public static SqlResult ofUpdateCount(int updateCount) {
            return new SqlResult(null, null, updateCount, false);
        }

        // Getters
        public List<Map<String, Object>> getRows() {
            return rows;
        }

        public ResultSetMetaData getMetaData() {
            return metaData;
        }

        public int getUpdateCount() {
            return updateCount;
        }

        public boolean isResultSet() {
            return isResultSet;
        }

        // Convenience methods
        public boolean isEmpty() {
            return rows == null || rows.isEmpty();
        }

        public int getRowCount() {
            return rows != null ? rows.size() : 0;
        }

        public List<String> getColumnNames() {
            if (metaData == null)
                return java.util.Collections.emptyList();
            try {
                List<String> names = new java.util.ArrayList<String>();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    names.add(metaData.getColumnName(i));
                }
                return names;
            } catch (Exception e) {
                return java.util.Collections.emptyList();
            }
        }
    }

    private final Logger log = LoggingUtils.getLogger(SqlExecutor.class);
    private final ConnectionSupplier connectionSupplier;

    @FunctionalInterface
    public interface ConnectionSupplier {
        Connection get() throws SQLException;
    }

    public SqlExecutor(ConnectionSupplier connectionSupplier) {
        this.connectionSupplier = connectionSupplier;
    }

    public SqlResult executeSql(String sql, List<Object> params) throws SQLException {
        log.info("Executing SQL with parameters: {} | params={}", sql, params);
        int placeholderCount = countPlaceholders(sql);
        if (placeholderCount != params.size()) {
            throw new IllegalArgumentException("SQL placeholder count (" + placeholderCount
                    + ") does not match parameter count (" + params.size() + ")");
        }
        try (Connection conn = connectionSupplier.get();
                java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }
            boolean isResultSet = pstmt.execute();
            if (isResultSet) {
                return handleResultSet(pstmt.getResultSet());
            } else {
                return handleUpdateCount(pstmt.getUpdateCount());
            }
        } catch (Exception e) {
            ExceptionUtils.logAndRethrow(e, "Failed to execute SQL with parameters: " + sql);
            throw new IllegalStateException("Unreachable");
        }
    }

    public void executeSqlScript(String script, Consumer<SqlResult> resultHandler) throws SQLException {
        log.info("Executing SQL script");
        String[] statements = script.split(";");

        for (String sql : statements) {
            if (!sql.trim().isEmpty()) {
                log.info("Script Statement: {}", sql);
                try {
                    resultHandler.accept(executeSql(sql, new ArrayList<Object>()));
                } catch (Exception e) {
                    ExceptionUtils.logAndRethrow(e, "Failed to execute SQL statement in script: " + sql);
                }
            }
        }
    }

    private SqlResult handleResultSet(ResultSet rs) throws SQLException {
        try {
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            List<Map<String, Object>> rows = new ArrayList<>();

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= cols; i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }

            return SqlResult.ofResultSet(rows, meta);
        } catch (Exception e) {
            ExceptionUtils.logAndRethrow(e, "Failed to handle SQL result set");
            throw new IllegalStateException("Unreachable");
        }
    }

    private SqlResult handleUpdateCount(int updateCount) {
        try {
            return SqlResult.ofUpdateCount(updateCount);
        } catch (Exception e) {
            ExceptionUtils.logAndRethrow(e, "Failed to handle SQL update count");
            throw new IllegalStateException("Unreachable");
        }
    }

    // Count the number of '?' placeholders in the SQL
    private int countPlaceholders(String sql) {
        int count = 0;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '?')
                count++;
        }
        return count;
    }
}