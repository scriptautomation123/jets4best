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
import java.util.stream.Collectors;

import org.apache.logging.log4j.Logger;

import com.baml.mav.aieutil.util.ExceptionUtils;
import com.baml.mav.aieutil.util.LoggingUtils;

public class ProcedureExecutor {

    public static class ProcedureParam {
        private final String name;
        private final String type;
        private final Object value;

        public ProcedureParam(String name, String type, Object value) {
            this.name = name;
            this.type = type;
            this.value = value;
        }

        public String name() {
            return name;
        }

        public String type() {
            return type;
        }

        public Object value() {
            return value;
        }

        public static ProcedureParam fromString(String input) {
            String[] parts = input.split(":");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid parameter format: " + input);
            }
            return new ProcedureParam(parts[0], parts[1], parts[2]);
        }

        public Object getTypedValue() {
            String typeUpper = type.toUpperCase();
            switch (typeUpper) {
                case "NUMBER":
                case "INTEGER":
                case "INT":
                    return Integer.parseInt(value.toString());
                case "DOUBLE":
                    return Double.parseDouble(value.toString());
                case "BOOLEAN":
                    return Boolean.parseBoolean(value.toString());
                default:
                    return value;
            }
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
                            .collect(Collectors.toList()))
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
                            .collect(Collectors.toList()))
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
            if (inputParams == null || inputParams.trim().isEmpty()) {
                return Collections.emptyList();
            }
            return Arrays.stream(inputParams.split(","))
                    .map(String::trim)
                    .filter(s -> s.contains(":"))
                    .map(ProcedureParam::fromString)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            ExceptionUtils.logAndRethrow(e, "Failed to parse string input parameters");
            throw new IllegalStateException("Unreachable");
        }
    }

    // Parse CLI output parameter strings
    private List<ProcedureParam> parseStringOutputParams(String outputParams) {
        try {
            if (outputParams == null || outputParams.trim().isEmpty()) {
                return Collections.emptyList();
            }
            return Arrays.stream(outputParams.split(","))
                    .map(String::trim)
                    .filter(s -> s.contains(":"))
                    .map(param -> {
                        String[] parts = param.split(":");
                        return new ProcedureParam(parts[0], parts[1], null);
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            ExceptionUtils.logAndRethrow(e, "Failed to parse string output parameters");
            throw new IllegalStateException("Unreachable");
        }
    }

    // Modern pattern matching
    private void setParameter(CallableStatement stmt, int index, Object value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.NULL);
        } else if (value instanceof Integer) {
            stmt.setInt(index, (Integer) value);
        } else if (value instanceof Double) {
            stmt.setDouble(index, (Double) value);
        } else if (value instanceof String) {
            stmt.setString(index, (String) value);
        } else if (value instanceof Boolean) {
            stmt.setBoolean(index, (Boolean) value);
        } else {
            stmt.setObject(index, value);
        }
    }

    // Simplified JDBC type mapping
    private int getJdbcType(String type) {
        String typeUpper = type.toUpperCase();
        switch (typeUpper) {
            case "STRING":
            case "VARCHAR":
            case "VARCHAR2":
                return Types.VARCHAR;
            case "INTEGER":
            case "INT":
                return Types.INTEGER;
            case "DOUBLE":
            case "NUMBER":
                return Types.DOUBLE;
            case "DATE":
                return Types.DATE;
            case "TIMESTAMP":
                return Types.TIMESTAMP;
            case "BOOLEAN":
                return Types.BOOLEAN;
            default:
                return Types.OTHER;
        }
    }

}