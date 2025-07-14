package com.baml.mav.aieutil;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Consumer;

import org.apache.logging.log4j.Logger;

import com.baml.mav.aieutil.util.ExceptionUtils;
import com.baml.mav.aieutil.util.LoggingUtils;
import com.baml.mav.aieutil.util.YamlConfig;

public class UnifiedDatabaseService {
    private final Logger log = LoggingUtils.getLogger(UnifiedDatabaseService.class);
    private final String user;
    private final String password;
    private final String connectString;
    private final ConnectionSupplier connectionSupplier;
    private static final YamlConfig appConfig = new YamlConfig(System.getProperty("config.file", "application.yaml"));

    @FunctionalInterface
    public interface ConnectionSupplier {
        Connection get() throws SQLException;
    }

    // Nested records for data structures
    public record SqlResult(
            List<Map<String, Object>> rows,
            ResultSetMetaData metaData,
            int updateCount,
            boolean isResultSet) {
        public SqlResult {
            if (isResultSet && rows == null) {
                throw new IllegalArgumentException("Rows cannot be null for result sets");
            }
        }

        public static SqlResult ofResultSet(List<Map<String, Object>> rows, ResultSetMetaData metaData) {
            return new SqlResult(rows, metaData, -1, true);
        }

        public static SqlResult ofUpdateCount(int updateCount) {
            return new SqlResult(null, null, updateCount, false);
        }

        public boolean isEmpty() {
            return rows == null || rows.isEmpty();
        }

        public int getRowCount() {
            return rows != null ? rows.size() : 0;
        }

        public List<String> getColumnNames() {
            if (metaData == null)
                return List.of();
            try {
                List<String> names = new ArrayList<>();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    names.add(metaData.getColumnName(i));
                }
                return names;
            } catch (Exception e) {
                return List.of();
            }
        }
    }

    public record ProcedureParam(String name, String type, Object value) {
        public static ProcedureParam fromString(String input) {
            String[] parts = input.split(":");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid parameter format: " + input);
            }
            return new ProcedureParam(parts[0], parts[1], parts[2]);
        }

        public Object getTypedValue() {
            return switch (type.toUpperCase()) {
                case "NUMBER", "INTEGER", "INT" -> Integer.parseInt(value.toString());
                case "DOUBLE" -> Double.parseDouble(value.toString());
                case "BOOLEAN" -> Boolean.parseBoolean(value.toString());
                default -> value;
            };
        }
    }

    // Factory method for CLI usage
    public static UnifiedDatabaseService create(String type, String database, String user, String password,
            String host) {
        String connectString = buildConnectionString(type, database, host);
        return new UnifiedDatabaseService(user, password, connectString);
    }

    // Connection string building logic
    private static String buildConnectionString(String type, String database, String host) {
        return switch (type) {
            case "h2" -> host != null && !host.isBlank()
                    ? buildH2JdbcUrl(database)
                    : buildH2MemoryUrl(database);
            default -> host != null && !host.isBlank()
                    ? buildOracleJdbcUrl(host, database)
                    : buildOracleLdapUrl(database);
        };
    }

    private static String buildH2JdbcUrl(String database) {
        String template = appConfig.getRawValue("databases.h2.connection-string.jdbc-thin.template");
        return String.format(template, database);
    }

    private static String buildH2MemoryUrl(String database) {
        String template = appConfig.getRawValue("databases.h2.connection-string.jdbc-thin.template");
        return String.format(template, "mem:" + database);
    }

    private static String buildOracleJdbcUrl(String host, String database) {
        String template = appConfig.getRawValue("databases.oracle.connection-string.jdbc-thin.template");
        int port = Integer.parseInt(appConfig.getRawValue("databases.oracle.connection-string.jdbc-thin.port"));
        return String.format(template, host, port, database);
    }

    private static String buildOracleLdapUrl(String database) {
        String template = appConfig.getRawValue("databases.oracle.connection-string.ldap.template");
        int port = Integer.parseInt(appConfig.getRawValue("databases.oracle.connection-string.ldap.port"));
        String context = appConfig.getRawValue("databases.oracle.connection-string.ldap.context");
        String servers = String.join(",",
                appConfig.getRawValue("databases.oracle.connection-string.ldap.servers").split(","));
        return String.format(template, servers, port, database, context);
    }

    public UnifiedDatabaseService(String user, String password, String connectString) {
        this.user = user;
        this.password = password;
        this.connectString = connectString;
        this.connectionSupplier = this::getConnection;

        log.info("UnifiedDatabaseService: user={}, connectString={}", user, connectString);
    }

    public UnifiedDatabaseService(ConnectionSupplier connectionSupplier) {
        this.user = null;
        this.password = null;
        this.connectString = null;
        this.connectionSupplier = connectionSupplier;
        log.info("UnifiedDatabaseService: using custom connection supplier");
    }

    public SqlResult executeSql(String sql) throws SQLException {
        log.info("Executing SQL: {}", sql);

        try (Connection conn = connectionSupplier.get();
                Statement stmt = conn.createStatement()) {

            boolean isResultSet = stmt.execute(sql);

            if (isResultSet) {
                return handleResultSet(stmt.getResultSet());
            } else {
                return SqlResult.ofUpdateCount(stmt.getUpdateCount());
            }
        } catch (SQLException e) {
            ExceptionUtils.handleSQLException(e, "Failed to execute SQL statement");
            throw new IllegalStateException("Unreachable");
        }
    }

    public void runSqlScript(String script, Consumer<SqlResult> resultHandler) throws SQLException {
        log.info("Executing SQL script");
        String[] statements = script.split(";");

        for (String sql : statements) {
            if (!sql.trim().isEmpty()) {
                log.info("Script Statement: {}", sql);
                resultHandler.accept(executeSql(sql));
            }
        }
    }

    public Map<String, Object> executeProcedureWithStrings(String procFullName, String inputParams, String outputParams)
            throws SQLException {
        log.info("Executing procedure with string parameters: {}", procFullName);

        List<ProcedureParam> inputs = parseStringInputParams(inputParams);
        List<ProcedureParam> outputs = parseStringOutputParams(outputParams);

        String callSql = buildCallString(procFullName, inputs.size(), outputs.size());
        log.debug("Generated call SQL: {}", callSql);

        try (Connection conn = connectionSupplier.get();
                CallableStatement call = conn.prepareCall(callSql)) {

            int idx = 1;
            Map<String, Integer> outParamIndices = new HashMap<>();

            // Set input parameters
            for (ProcedureParam input : inputs) {
                setParameter(call, idx++, input.getTypedValue());
            }

            // Register output parameters
            for (ProcedureParam output : outputs) {
                outParamIndices.put(output.name(), idx);
                call.registerOutParameter(idx++, getJdbcType(output.type()));
            }

            call.execute();

            // Retrieve output parameters
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry : outParamIndices.entrySet()) {
                out.put(entry.getKey(), call.getObject(entry.getValue()));
            }

            return out;
        } catch (SQLException e) {
            ExceptionUtils.handleSQLException(e, "Failed to execute procedure call");
            throw new IllegalStateException("Unreachable");
        }
    }

    // Helper methods - Simplified
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(connectString, user, password);
    }

    private SqlResult handleResultSet(ResultSet rs) throws SQLException {
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
    }

    private List<ProcedureParam> parseStringInputParams(String inputParams) {
        if (inputParams == null || inputParams.isBlank()) {
            return Collections.emptyList();
        }

        return Arrays.stream(inputParams.split(","))
                .map(String::trim)
                .filter(s -> s.contains(":"))
                .map(ProcedureParam::fromString)
                .toList();
    }

    private List<ProcedureParam> parseStringOutputParams(String outputParams) {
        if (outputParams == null || outputParams.isBlank()) {
            return Collections.emptyList();
        }

        return Arrays.stream(outputParams.split(","))
                .map(String::trim)
                .filter(s -> s.contains(":"))
                .map(param -> {
                    String[] parts = param.split(":");
                    return new ProcedureParam(parts[0], parts[1], null);
                })
                .toList();
    }

    private String buildCallString(String procedureName, int inputCount, int outputCount) {
        int totalParams = inputCount + outputCount;
        StringJoiner q = new StringJoiner(",", "(", ")");
        for (int i = 0; i < totalParams; i++)
            q.add("?");
        return "{call " + procedureName + q + "}";
    }

    private void setParameter(CallableStatement stmt, int index, Object value) throws SQLException {
        switch (value) {
            case Integer i -> stmt.setInt(index, i);
            case Double d -> stmt.setDouble(index, d);
            case String s -> stmt.setString(index, s);
            case Boolean b -> stmt.setBoolean(index, b);
            case null -> stmt.setNull(index, Types.NULL);
            default -> stmt.setObject(index, value);
        }
    }

    private int getJdbcType(String type) {
        return switch (type.toUpperCase()) {
            case "STRING", "VARCHAR", "VARCHAR2" -> Types.VARCHAR;
            case "INTEGER", "INT" -> Types.INTEGER;
            case "DOUBLE", "NUMBER" -> Types.DOUBLE;
            case "DATE" -> Types.DATE;
            case "TIMESTAMP" -> Types.TIMESTAMP;
            case "BOOLEAN" -> Types.BOOLEAN;
            default -> Types.OTHER;
        };
    }
}