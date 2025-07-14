package com.baml.mav.aieutil.database;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

import org.apache.logging.log4j.Logger;

import com.baml.mav.aieutil.util.ExceptionUtils;
import com.baml.mav.aieutil.util.LoggingUtils;

public class ProcedureExecutor {

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

    private final Logger log = LoggingUtils.getLogger(ProcedureExecutor.class);
    private final SqlExecutor.ConnectionSupplier connectionSupplier;

    public ProcedureExecutor(SqlExecutor.ConnectionSupplier connectionSupplier) {
        this.connectionSupplier = connectionSupplier;
    }

    // Keep existing API for backward compatibility
    public Map<String, Object> executeProcedure(String procFullName,
            Map<String, Object> inputParams,
            Map<String, Integer> outputParams) throws SQLException {
        log.info("Executing procedure: {}", procFullName);
        try {
            List<ProcedureParam> inputs = parseInputParams(inputParams);
            List<ProcedureParam> outputs = parseOutputParams(outputParams);
            String callSql = buildCallString(procFullName, inputs.size(), outputs.size());
            log.debug("Generated call SQL: {}", callSql);
            try (Connection conn = connectionSupplier.get();
                    CallableStatement call = conn.prepareCall(callSql)) {
                int idx = 1;
                Map<String, Integer> outParamIndices = new HashMap<>();
                for (ProcedureParam input : inputs) {
                    setParameter(call, idx++, input.getTypedValue());
                }
                for (ProcedureParam output : outputs) {
                    outParamIndices.put(output.name(), idx);
                    call.registerOutParameter(idx++, getJdbcType(output.type()));
                }
                call.execute();
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> entry : outParamIndices.entrySet()) {
                    out.put(entry.getKey(), call.getObject(entry.getValue()));
                }
                return out;
            }
        } catch (Exception e) {
            ExceptionUtils.logAndRethrow(e, "Failed to execute procedure: " + procFullName);
            throw new IllegalStateException("Unreachable");
        }
    }

    // Modern parsing with Optional
    private List<ProcedureParam> parseInputParams(Map<String, Object> inputParams) {
        try {
            return Optional.ofNullable(inputParams)
                    .map(params -> params.values().stream()
                            .map(Object::toString)
                            .filter(str -> str.contains(":"))
                            .map(ProcedureParam::fromString)
                            .toList())
                    .orElse(Collections.emptyList());
        } catch (Exception e) {
            ExceptionUtils.logAndRethrow(e, "Failed to parse input parameters");
            throw new IllegalStateException("Unreachable");
        }
    }

    private List<ProcedureParam> parseOutputParams(Map<String, Integer> outputParams) {
        try {
            return Optional.ofNullable(outputParams)
                    .map(params -> params.entrySet().stream()
                            .map(entry -> new ProcedureParam(entry.getKey(), "VARCHAR", null))
                            .toList())
                    .orElse(Collections.emptyList());
        } catch (Exception e) {
            ExceptionUtils.logAndRethrow(e, "Failed to parse output parameters");
            throw new IllegalStateException("Unreachable");
        }
    }

    private String buildCallString(String procedureName, int inputCount, int outputCount) {
        int totalParams = inputCount + outputCount;
        StringJoiner q = new StringJoiner(",", "(", ")");
        for (int i = 0; i < totalParams; i++)
            q.add("?");
        return "{call " + procedureName + q + "}";
    }

    // Modern pattern matching
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

    // Simplified JDBC type mapping
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

    // New method for CLI string parameter parsing
    public Map<String, Object> executeProcedureWithStrings(String procFullName, String inputParams, String outputParams)
            throws SQLException {
        log.info("Executing procedure with string parameters: {}", procFullName);
        try {
            List<ProcedureParam> inputs = parseStringInputParams(inputParams);
            List<ProcedureParam> outputs = parseStringOutputParams(outputParams);
            String callSql = buildCallString(procFullName, inputs.size(), outputs.size());
            log.debug("Generated call SQL: {}", callSql);
            try (Connection conn = connectionSupplier.get();
                    CallableStatement call = conn.prepareCall(callSql)) {
                int idx = 1;
                Map<String, Integer> outParamIndices = new HashMap<>();
                for (ProcedureParam input : inputs) {
                    setParameter(call, idx++, input.getTypedValue());
                }
                for (ProcedureParam output : outputs) {
                    outParamIndices.put(output.name(), idx);
                    call.registerOutParameter(idx++, getJdbcType(output.type()));
                }
                call.execute();
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> entry : outParamIndices.entrySet()) {
                    out.put(entry.getKey(), call.getObject(entry.getValue()));
                }
                return out;
            }
        } catch (Exception e) {
            ExceptionUtils.logAndRethrow(e, "Failed to execute procedure with string parameters: " + procFullName);
            throw new IllegalStateException("Unreachable");
        }
    }

    // Parse CLI input parameter strings
    private List<ProcedureParam> parseStringInputParams(String inputParams) {
        try {
            if (inputParams == null || inputParams.isBlank()) {
                return Collections.emptyList();
            }
            return Arrays.stream(inputParams.split(","))
                    .map(String::trim)
                    .filter(s -> s.contains(":"))
                    .map(ProcedureParam::fromString)
                    .toList();
        } catch (Exception e) {
            ExceptionUtils.logAndRethrow(e, "Failed to parse string input parameters");
            throw new IllegalStateException("Unreachable");
        }
    }

    // Parse CLI output parameter strings
    private List<ProcedureParam> parseStringOutputParams(String outputParams) {
        try {
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
        } catch (Exception e) {
            ExceptionUtils.logAndRethrow(e, "Failed to parse string output parameters");
            throw new IllegalStateException("Unreachable");
        }
    }
}