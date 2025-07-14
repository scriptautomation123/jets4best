# Modern Java Patterns: Refactoring ProcedureExecutor

## Overview
This document demonstrates how to refactor traditional Java code using modern Java patterns (Java 17+) to improve type safety, immutability, and functional programming principles.

## 1. Records Instead of Complex Parameter Parsing

### Before (Traditional Approach)
```java
// Complex string parsing with manual type checking
private Map<String, Object> parseInputParameters(Map<String, Object> inputParams) {
    if (inputParams == null) return null;
    
    if (!inputParams.isEmpty() && inputParams.values().iterator().next() instanceof String) {
        String firstValue = (String) inputParams.values().iterator().next();
        if (firstValue.contains(":")) {
            Map<String, Object> parsed = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : inputParams.entrySet()) {
                String[] parts = entry.getValue().toString().split(":");
                if (parts.length == 3) {
                    parsed.put(parts[0], parseValue(parts[1], parts[2]));
                } else {
                    parsed.put(entry.getKey(), entry.getValue());
                }
            }
            return parsed;
        }
    }
    return inputParams;
}
```

### After (Modern Records Approach)
```java
// Type-safe parameter representation
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

// Clean, functional parsing
private List<ProcedureParam> parseInputParameters(Map<String, Object> inputParams) {
    return Optional.ofNullable(inputParams)
        .map(params -> params.values().stream()
            .map(Object::toString)
            .filter(str -> str.contains(":"))
            .map(ProcedureParam::fromString)
            .collect(Collectors.toList()))
        .orElse(Collections.emptyList());
}
```

### Why Better?
- **Type Safety**: Records are immutable and type-safe
- **Immutability**: No risk of modifying parameters accidentally
- **Functional**: Stream-based processing is more declarative
- **Error Handling**: Clear validation with meaningful exceptions
- **Testability**: Easy to test individual parameter parsing

## 2. Pattern Matching Instead of instanceof Chains

### Before (Traditional instanceof)
```java
private void setParameter(CallableStatement stmt, int index, Object value) throws SQLException {
    if (value instanceof Number) {
        if (value instanceof Integer) {
            stmt.setInt(index, (Integer) value);
        } else if (value instanceof Double) {
            stmt.setDouble(index, (Double) value);
        } else {
            stmt.setObject(index, value);
        }
    } else {
        stmt.setObject(index, value);
    }
}
```

### After (Pattern Matching)
```java
private void setParameter(CallableStatement stmt, int index, Object value) throws SQLException {
    switch (value) {
        case Integer i -> stmt.setInt(index, i);
        case Double d -> stmt.setDouble(index, d);
        case Number n -> stmt.setObject(index, n);
        case null -> stmt.setNull(index, Types.NULL);
        default -> stmt.setObject(index, value);
    }
}
```

### Why Better?
- **Concise**: Eliminates nested if-else chains
- **Exhaustive**: Compiler ensures all cases are handled
- **Null Safety**: Explicit null handling
- **Performance**: More efficient than instanceof chains
- **Readable**: Clear intent for each type

## 3. Sealed Classes for Type Safety

### Before (String-based Type Checking)
```java
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
```

### After (Sealed Classes)
```java
public sealed interface SqlType {
    int getJdbcType();
    
    record Varchar() implements SqlType {
        @Override public int getJdbcType() { return Types.VARCHAR; }
    }
    record Integer() implements SqlType {
        @Override public int getJdbcType() { return Types.INTEGER; }
    }
    record Double() implements SqlType {
        @Override public int getJdbcType() { return Types.DOUBLE; }
    }
    record Date() implements SqlType {
        @Override public int getJdbcType() { return Types.DATE; }
    }
    record Timestamp() implements SqlType {
        @Override public int getJdbcType() { return Types.TIMESTAMP; }
    }
    record Boolean() implements SqlType {
        @Override public int getJdbcType() { return Types.BOOLEAN; }
    }
}

// Usage
private SqlType parseSqlType(String type) {
    return switch (type.toUpperCase()) {
        case "STRING", "VARCHAR", "VARCHAR2" -> new SqlType.Varchar();
        case "INTEGER", "INT" -> new SqlType.Integer();
        case "DOUBLE", "NUMBER" -> new SqlType.Double();
        case "DATE" -> new SqlType.Date();
        case "TIMESTAMP" -> new SqlType.Timestamp();
        case "BOOLEAN" -> new SqlType.Boolean();
        default -> throw new IllegalArgumentException("Unknown type: " + type);
    };
}
```

### Why Better?
- **Compile-time Safety**: Can't miss a type case
- **Extensible**: Easy to add new types
- **Type-safe**: No string-based errors
- **IDE Support**: Better autocomplete and refactoring
- **Documentation**: Types are self-documenting

## 4. Optional for Null Safety

### Before (Explicit Null Checks)
```java
private Map<String, Object> parseInputParameters(Map<String, Object> inputParams) {
    if (inputParams == null) return null;
    
    if (!inputParams.isEmpty() && inputParams.values().iterator().next() instanceof String) {
        // ... complex logic
    }
    return inputParams;
}
```

### After (Optional Chain)
```java
private List<ProcedureParam> parseInputParameters(Map<String, Object> inputParams) {
    return Optional.ofNullable(inputParams)
        .map(params -> params.values().stream()
            .map(Object::toString)
            .filter(str -> str.contains(":"))
            .map(ProcedureParam::fromString)
            .collect(Collectors.toList()))
        .orElse(Collections.emptyList());
}
```

### Why Better?
- **Null Safety**: No null pointer exceptions
- **Functional**: Chainable operations
- **Default Values**: Easy to provide fallbacks
- **Composable**: Can combine with other operations
- **Intent**: Clear that null is a valid case

## 5. Complete Modern Refactor

### Before (Traditional ProcedureExecutor)
```java
public class ProcedureExecutor {
    private final Logger log = LoggingUtils.getLogger(ProcedureExecutor.class);
    private final SqlExecutor.ConnectionSupplier connectionSupplier;

    public Map<String, Object> executeProcedure(String procFullName,
                                              Map<String, Object> inputParams,
                                              Map<String, Integer> outputParams) throws SQLException {
        // Complex parsing logic
        Map<String, Object> parsedInputParams = parseInputParameters(inputParams);
        Map<String, Integer> parsedOutputParams = parseOutputParameters(outputParams);
        
        // Manual parameter setting
        // ... lots of boilerplate
    }
}
```

### After (Modern Approach)
```java
public class ProcedureExecutor {
    private final Logger log = LoggingUtils.getLogger(ProcedureExecutor.class);
    private final ConnectionSupplier connectionSupplier;

    public record ProcedureCall(
        String name,
        List<ProcedureParam> inputs,
        List<ProcedureParam> outputs
    ) {
        public static ProcedureCall of(String name, 
                                     List<ProcedureParam> inputs, 
                                     List<ProcedureParam> outputs) {
            return new ProcedureCall(name, inputs, outputs);
        }
    }

    public record ProcedureResult(Map<String, Object> outputs) {
        public static ProcedureResult empty() {
            return new ProcedureResult(Map.of());
        }
    }

    public ProcedureResult executeProcedure(ProcedureCall call) throws SQLException {
        log.info("Executing procedure: {}", call.name());
        
        try (Connection conn = connectionSupplier.get();
             CallableStatement stmt = buildCallStatement(conn, call)) {
            
            stmt.execute();
            return extractResults(stmt, call.outputs());
        }
    }

    private CallableStatement buildCallStatement(Connection conn, ProcedureCall call) 
            throws SQLException {
        String sql = buildCallSql(call);
        CallableStatement stmt = conn.prepareCall(sql);
        
        int index = 1;
        for (ProcedureParam input : call.inputs()) {
            setParameter(stmt, index++, input.getTypedValue());
        }
        for (ProcedureParam output : call.outputs()) {
            stmt.registerOutParameter(index++, output.getJdbcType());
        }
        
        return stmt;
    }

    private void setParameter(CallableStatement stmt, int index, Object value) 
            throws SQLException {
        switch (value) {
            case Integer i -> stmt.setInt(index, i);
            case Double d -> stmt.setDouble(index, d);
            case String s -> stmt.setString(index, s);
            case Boolean b -> stmt.setBoolean(index, b);
            case null -> stmt.setNull(index, Types.NULL);
            default -> stmt.setObject(index, value);
        }
    }
}
```

### Why Better?
- **Immutability**: Records prevent accidental mutations
- **Type Safety**: Compile-time guarantees
- **Functional**: Pure functions with clear inputs/outputs
- **Testable**: Easy to unit test individual components
- **Maintainable**: Clear separation of concerns
- **Error Handling**: Better exception handling with specific types

## 6. Usage Examples

### Before (Complex Usage)
```java
Map<String, Object> inputs = Map.of("param1", "name:INTEGER:123", "param2", "age:DOUBLE:25.5");
Map<String, Integer> outputs = Map.of("result", Types.VARCHAR);

Map<String, Object> result = executor.executeProcedure("MY_PROC", inputs, outputs);
```

### After (Clean Usage)
```java
List<ProcedureParam> inputs = List.of(
    ProcedureParam.fromString("name:INTEGER:123"),
    ProcedureParam.fromString("age:DOUBLE:25.5")
);
List<ProcedureParam> outputs = List.of(
    new ProcedureParam("result", "VARCHAR", null)
);

ProcedureCall call = ProcedureCall.of("MY_PROC", inputs, outputs);
ProcedureResult result = executor.executeProcedure(call);
```

## Summary

Modern Java patterns provide:
- **Type Safety**: Compile-time guarantees prevent runtime errors
- **Immutability**: Records prevent accidental state changes
- **Functional Programming**: Pure functions with clear contracts
- **Null Safety**: Optional and pattern matching handle null cases
- **Performance**: More efficient than traditional approaches
- **Maintainability**: Cleaner, more readable code

These patterns make the code more robust, testable, and maintainable while leveraging the full power of modern Java features. 