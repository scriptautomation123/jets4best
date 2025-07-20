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
import java.util.StringJoiner;
import java.util.stream.Collectors;

import com.baml.mav.aieutil.util.ExceptionUtils;
import com.baml.mav.aieutil.util.LoggingUtils;

public class ProcedureExecutor {
    private static final String FAILED = "FAILED";

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
                throw new IllegalArgumentException(
                        "Invalid parameter format. Expected 'name:type:value', got: " + input);
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

    private String buildCallString(String procedureName, int inputCount, int outputCount) {
        int totalParams = inputCount + outputCount;
        StringJoiner q = new StringJoiner(",", "(", ")");
        for (int i = 0; i < totalParams; i++)
            q.add("?");
        return "{call " + procedureName + q + "}";
    }

    // New method for CLI string parameter parsing
    public Map<String, Object> executeProcedureWithStrings(Connection conn, String procFullName, String inputParams,
            String outputParams)
            throws SQLException {
        LoggingUtils.logProcedureExecution(procFullName, inputParams, outputParams);
        try {
            List<ProcedureParam> inputs = parseStringInputParams(inputParams);
            List<ProcedureParam> outputs = parseStringOutputParams(outputParams);
            String callSql = buildCallString(procFullName, inputs.size(), outputs.size());

            try (CallableStatement call = conn.prepareCall(callSql)) {
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
            LoggingUtils.logStructuredError("procedure_execution", "execute", FAILED,
                    "Failed to execute procedure with string parameters: " + procFullName, e);
            throw ExceptionUtils.wrap(e, "Failed to execute procedure with string parameters: " + procFullName);
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
            LoggingUtils.logStructuredError("parameter_parsing", "parse_input", FAILED,
                    "Failed to parse string input parameters", e);
            throw ExceptionUtils.wrap(e, "Failed to parse string input parameters");
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
            LoggingUtils.logStructuredError("parameter_parsing", "parse_output", FAILED,
                    "Failed to parse string output parameters", e);
            throw ExceptionUtils.wrap(e, "Failed to parse string output parameters");
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