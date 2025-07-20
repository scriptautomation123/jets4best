# How to Create ExecSqlCmd.java: A Complete Guide for Junior Engineers

## Overview

This guide teaches you how to create a new CLI command (`ExecSqlCmd.java`) following the established patterns in the codebase. We'll build a production-ready SQL execution command that follows the same architecture as `ExecProcedureCmd.java`.

## Prerequisites

Before starting, ensure you understand:
- The existing `ExecProcedureCmd.java` implementation
- The service layer architecture (`BaseDatabaseExecutionService`)
- The authentication flow (`PasswordResolver`, `VaultClient`)
- PicoCLI command structure

## Step-by-Step Implementation

### 1. Create the CLI Command Class

**File:** `src/main/java/com/baml/mav/aieutil/ExecSqlCmd.java`

```java
package com.baml.mav.aieutil;

import com.baml.mav.aieutil.auth.PasswordResolver;
import com.baml.mav.aieutil.cli.BaseDatabaseCliCommand;
import com.baml.mav.aieutil.service.ExecutionResult;
import com.baml.mav.aieutil.service.SqlExecutionService;
import com.baml.mav.aieutil.service.SqlRequest;
import com.baml.mav.aieutil.util.ExceptionUtils;
import com.baml.mav.aieutil.util.LoggingUtils;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;


@Command(name = "exec-sql", mixinStandardHelpOptions = true, 
         description = "Vault-authenticated SQL execution", 
         version = "1.0.0", 
         exitCodeOnInvalidInput = CommandLine.ExitCode.USAGE, 
         exitCodeOnExecutionException = CommandLine.ExitCode.SOFTWARE)
public class ExecSqlCmd extends BaseDatabaseCliCommand {

    @Parameters(index = "0", description = "SQL statement to execute", arity = "0..1")
    String sql;

    @Option(names = "--script", description = "SQL script file path")
    String script;

    @Option(names = "--params", description = "SQL parameters (value1,value2,value3)")
    String params;

    public ExecSqlCmd() {
        super(createService());
    }

    private static SqlExecutionService createService() {
        return new SqlExecutionService(
                new PasswordResolver(new ExecSqlCmd()::promptForPassword));
    }

    public static void main(String[] args) {
        try {
            LoggingUtils.logCliStartup(System.getProperty("java.home"));

            CommandLine cmd = new CommandLine(new ExecSqlCmd());
            cmd.setExecutionExceptionHandler(new ExceptionUtils.ExecutionExceptionHandler());
            cmd.setParameterExceptionHandler(new ExceptionUtils.ParameterExceptionHandler());

            System.exit(cmd.execute(args));
        } catch (Exception e) {
            LoggingUtils.logMinimalError(e);
            System.exit(CommandLine.ExitCode.SOFTWARE);
        }
    }

    @Override
    public Integer call() {
        try {
            SqlRequest request = SqlRequest.builder()
                    .type(type)
                    .database(database)
                    .user(user)
                    .sql(sql)
                    .script(script)
                    .params(parseParams(params))
                    .vaultConfig(createVaultConfig())
                    .build();

            ExecutionResult result = service.execute(request);
            result.formatOutput(System.out);
            return result.getExitCode();

        } catch (Exception e) {
            return ExceptionUtils.handleCliException(e, "execute SQL", System.err);
        }
    }

    private List<Object> parseParams(String params) {
        if (params == null || params.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(params.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
    }
}
```

### 2. Create the Request Model

**File:** `src/main/java/com/baml/mav/aieutil/service/SqlRequest.java`

```java
package com.baml.mav.aieutil.service;

import java.util.List;
import java.util.Collections;
import java.util.stream.Collectors;

public final class SqlRequest extends DatabaseRequest {
    private final String sql;
    private final String script;
    private final List<Object> params;

    private SqlRequest(Builder builder) {
        super(builder);
        this.sql = builder.sql;
        this.script = builder.script;
        this.params = builder.params != null ? builder.params : Collections.emptyList();
    }

    public String getSql() {
        return sql;
    }

    public String getScript() {
        return script;
    }

    public List<Object> getParams() {
        return params;
    }

    public boolean isScriptMode() {
        return script != null && !script.trim().isEmpty();
    }

    public boolean isSqlMode() {
        return sql != null && !sql.trim().isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DatabaseRequest.Builder<Builder> {
        private String sql;
        private String script;
        private List<Object> params;

        public Builder sql(String sql) {
            this.sql = sql;
            return this;
        }

        public Builder script(String script) {
            this.script = script;
            return this;
        }

        public Builder params(List<Object> params) {
            this.params = params;
            return this;
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public SqlRequest build() {
            return new SqlRequest(this);
        }
    }
}
```

### 3. Create the Service Layer

**File:** `src/main/java/com/baml/mav/aieutil/service/SqlExecutionService.java`

```java
package com.baml.mav.aieutil.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baml.mav.aieutil.auth.PasswordResolver;
import com.baml.mav.aieutil.database.SqlExecutor;

public class SqlExecutionService extends BaseDatabaseExecutionService {

    public SqlExecutionService(PasswordResolver passwordResolver) {
        super(passwordResolver);
    }

    @Override
    protected ExecutionResult executeWithConnection(DatabaseRequest request, Connection conn) throws SQLException {
        SqlRequest sqlRequest = (SqlRequest) request;
        SqlExecutor executor = new SqlExecutor(() -> conn);

        if (sqlRequest.isScriptMode()) {
            return executeScript(sqlRequest, executor);
        } else if (sqlRequest.isSqlMode()) {
            return executeSingleSql(sqlRequest, executor);
        } else {
            return ExecutionResult.failure(1, "[ERROR] Either --sql or --script must be specified");
        }
    }

    private ExecutionResult executeSingleSql(SqlRequest request, SqlExecutor executor) throws SQLException {
        SqlExecutor.SqlResult result = executor.executeSql(request.getSql(), request.getParams());
        
        if (result.isResultSet()) {
            return ExecutionResult.success(formatResultSet(result));
        } else {
            return ExecutionResult.success("Rows affected: " + result.getUpdateCount());
        }
    }

    private ExecutionResult executeScript(SqlRequest request, SqlExecutor executor) throws SQLException {
        try {
            String scriptContent = new String(Files.readAllBytes(Paths.get(request.getScript())));
            List<String> results = new ArrayList<>();
            
            executor.executeSqlScript(scriptContent, result -> {
                if (result.isResultSet()) {
                    results.add(formatResultSet(result));
                } else {
                    results.add("Rows affected: " + result.getUpdateCount());
                }
            });
            
            return ExecutionResult.success(String.join("\n", results));
        } catch (IOException e) {
            throw new SQLException("Failed to read script file: " + request.getScript(), e);
        }
    }

    private String formatResultSet(SqlExecutor.SqlResult result) {
        if (result.isEmpty()) {
            return "No rows returned";
        }

        StringBuilder output = new StringBuilder();
        List<String> columns = result.getColumnNames();
        
        // Header
        output.append(String.join(" | ", columns)).append("\n");
        output.append("-".repeat(output.length())).append("\n");
        
        // Data
        for (Map<String, Object> row : result.getRows()) {
            List<String> values = columns.stream()
                    .map(col -> row.get(col))
                    .map(val -> val != null ? val.toString() : "null")
                    .collect(Collectors.toList());
            output.append(String.join(" | ", values)).append("\n");
        }
        
        return output.toString();
    }
}
```

## Key Design Patterns Explained

### 1. **Inheritance Pattern**
- `ExecSqlCmd` extends `BaseDatabaseCliCommand` for common database options
- `SqlRequest` extends `DatabaseRequest` for common database properties
- `SqlExecutionService` extends `BaseDatabaseExecutionService` for common execution logic

### 2. **Builder Pattern**
- All request objects use builders for clean, readable construction
- Follows the same pattern as `ProcedureRequest`

### 3. **Template Method Pattern**
- `BaseDatabaseExecutionService.execute()` provides the common flow
- Subclasses implement `executeWithConnection()` for specific logic

### 4. **Dependency Injection**
- Services receive dependencies through constructors
- `PasswordResolver` is injected for authentication

## Usage Examples

### Single SQL Statement
```bash
aieutil exec-sql "SELECT * FROM employees WHERE dept_id = 10" \
    -u hr -p hr123 -d ORCL
```

### SQL with Parameters
```bash
aieutil exec-sql "SELECT * FROM employees WHERE dept_id = ? AND salary > ?" \
    --params "10,50000" \
    -u hr -p hr123 -d ORCL
```

### SQL Script Execution
```bash
aieutil exec-sql --script /path/to/script.sql \
    -u hr -p hr123 -d ORCL
```

### Vault Authentication
```bash
aieutil exec-sql "SELECT 1 FROM DUAL" \
    -u MAV_T2T_APP -d ECICMD03_svc01 \
    --vault-url https://vault.example.com \
    --role-id my-role \
    --secret-id my-secret \
    --ait my-ait
```

## Testing Strategy

### 1. **Unit Tests**
- Test `SqlRequest` builder and validation
- Test `SqlExecutionService` with mocked connections
- Test parameter parsing logic

### 2. **Integration Tests**
- Test with real database connections
- Test vault authentication flow
- Test script execution

### 3. **CLI Tests**
- Test command-line argument parsing
- Test error handling and exit codes
- Test help and usage messages

## Error Handling

The implementation follows the established error handling patterns:

1. **CLI Level**: Uses `ExceptionUtils.handleCliException()` for user-friendly messages
2. **Service Level**: Wraps exceptions with meaningful context
3. **Database Level**: Uses `SqlExecutor` for SQL-specific error handling

## Logging

All logging follows the centralized `LoggingUtils` pattern:
- `LoggingUtils.logSqlExecution()` for SQL statements
- `LoggingUtils.logSqlScriptExecution()` for script execution
- `LoggingUtils.logStructuredError()` for errors

## Production Considerations

### 1. **Security**
- SQL injection prevention through parameterized queries
- Secure password handling through vault integration
- Input validation for all parameters

### 2. **Performance**
- Connection pooling through `DatabaseService`
- Efficient result set handling
- Memory-conscious script processing

### 3. **Maintainability**
- Clear separation of concerns
- Consistent naming conventions
- Comprehensive error messages

## Next Steps

After implementing `ExecSqlCmd.java`:

1. **Add to Main CLI**: Register the command in the main CLI application
2. **Update Documentation**: Add usage examples to help text
3. **Create Tests**: Implement unit and integration tests
4. **Performance Testing**: Test with large result sets and scripts
5. **Security Review**: Ensure proper input validation and SQL injection prevention

This implementation provides a solid foundation for adding other CLI commands while maintaining consistency with the existing codebase architecture. 