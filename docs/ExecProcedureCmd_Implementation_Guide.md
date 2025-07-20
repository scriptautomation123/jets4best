# ExecProcedureCmd.java Implementation Guide: A Principal Engineer's Perspective

## Overview

This document explains how `ExecProcedureCmd.java` was implemented from scratch, following enterprise-grade software engineering principles. It demonstrates a layered architecture with clear separation of concerns, dependency injection, and robust error handling.

## Architecture Overview

The implementation follows a **layered architecture** with these key components:

```
┌─────────────────────────────────────────────────────────────┐
│                    CLI Layer (PicoCLI)                      │
│                    ExecProcedureCmd.java                    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   Service Layer                             │
│  ProcedureExecutionService → BaseDatabaseExecutionService   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                 Database Layer                              │
│  DatabaseService → ProcedureExecutor → ConnectionStringGen  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                Authentication Layer                         │
│  PasswordResolver → VaultClient → PasswordRequest          │
└─────────────────────────────────────────────────────────────┘
```

This comprehensive learning document explains the implementation from a principal engineer's perspective, covering the architectural decisions, design patterns, and implementation order with clear rationale for each choice.

## Implementation Order and Rationale

### Phase 1: Foundation Classes (Bottom-Up Approach)

#### 1.1 Authentication Foundation

**Why start here?** Authentication is the foundation - without it, nothing else works. We need to establish how passwords are resolved before building database connections.

**File:** `src/main/java/com/baml/mav/aieutil/auth/PasswordRequest.java`

```java
public final class PasswordRequest {
    private final String user;
    private final String database;
    private final String vaultUrl;
    private final String roleId;
    private final String secretId;
    private final String ait;

    // Two constructors for different authentication modes
    public PasswordRequest(String user, String database) // Vault lookup mode
    public PasswordRequest(String user, String database, String vaultUrl, String roleId, String secretId, String ait) // Direct vault mode
}
```

**Key Design Decisions:**
- **Immutable objects**: Prevents state corruption during authentication flow
- **Two authentication modes**: Supports both vault lookup and direct vault parameters
- **Validation in constructor**: Ensures valid state from creation

**File:** `src/main/java/com/baml/mav/aieutil/auth/PasswordResolver.java`

```java
public class PasswordResolver {
    private final Supplier<String> passwordPrompter;

    public Optional<String> resolvePassword(PasswordRequest request) {
        // 1. Try direct vault parameters
        // 2. Try vault lookup
        // 3. Fall back to console prompt
    }
}
```

**Key Design Decisions:**
- **Strategy pattern**: Multiple authentication strategies
- **Fallback mechanism**: Graceful degradation from vault to console
- **Dependency injection**: `Supplier<String>` allows flexible password prompting

#### 1.2 Database Foundation

**Why this order?** Database operations need connection strings and execution logic before services can use them.

**File:** `src/main/java/com/baml/mav/aieutil/database/ConnectionStringGenerator.java`

```java
public class ConnectionStringGenerator {
    public static ConnInfo createConnectionString(String type, String database, String user, String password, String host) {
        // Strategy pattern for different database types and connection methods
    }
}
```

**Key Design Decisions:**
- **Strategy pattern**: Different connection strategies for Oracle JDBC, Oracle LDAP, H2
- **Configuration-driven**: Uses YAML configuration for connection templates
- **Type safety**: Strong typing prevents connection string errors

**File:** `src/main/java/com/baml/mav/aieutil/database/ProcedureExecutor.java`

```java
public class ProcedureExecutor {
    public Map<String, Object> executeProcedureWithStrings(Connection conn, String procFullName, String inputParams, String outputParams) {
        // Parse parameters → Build call string → Execute → Return results
    }
}
```

**Key Design Decisions:**
- **Parameter parsing**: Handles CLI-style parameter strings (`name:type:value`)
- **Type conversion**: Converts string parameters to appropriate Java types
- **Result mapping**: Maps database results to structured output

### Phase 2: Service Layer (Business Logic)

#### 2.1 Request/Response Models

**Why models first?** Services need well-defined contracts before implementation.

**File:** `src/main/java/com/baml/mav/aieutil/service/DatabaseRequest.java`

```java
public class DatabaseRequest {
    private final String type;
    private final String database;
    private final String user;
    private final VaultConfig vaultConfig;
    
    // Builder pattern for clean construction
}
```

**Key Design Decisions:**
- **Builder pattern**: Fluent API for request construction
- **Immutable**: Prevents request modification during execution
- **Common base**: Shared by all database operations

**File:** `src/main/java/com/baml/mav/aieutil/service/ProcedureRequest.java`

```java
public final class ProcedureRequest extends DatabaseRequest {
    private final String procedure;
    private final String input;
    private final String output;
}
```

**Key Design Decisions:**
- **Inheritance**: Extends `DatabaseRequest` for common properties
- **Procedure-specific**: Adds procedure name and parameter strings
- **Validation**: Ensures required fields are present

**File:** `src/main/java/com/baml/mav/aieutil/service/ExecutionResult.java`

```java
public final class ExecutionResult {
    private final int exitCode;
    private final Map<String, Object> data;
    private final String message;
    
    // Factory methods for different result types
    public static ExecutionResult success(Map<String, Object> data)
    public static ExecutionResult failure(int exitCode, String message)
}
```

**Key Design Decisions:**
- **Factory methods**: Clean creation of different result types
- **Structured output**: Consistent format for CLI responses
- **Exit codes**: Standard CLI exit code conventions

#### 2.2 Base Service Architecture

**File:** `src/main/java/com/baml/mav/aieutil/service/BaseDatabaseExecutionService.java`

```java
public abstract class BaseDatabaseExecutionService {
    private final PasswordResolver passwordResolver;

    // Template method pattern
    public ExecutionResult execute(DatabaseRequest request) {
        // 1. Resolve password
        // 2. Create connection
        // 3. Execute specific logic (abstract method)
        // 4. Handle cleanup
    }

    protected abstract ExecutionResult executeWithConnection(DatabaseRequest request, Connection conn) throws SQLException;
}
```

**Key Design Decisions:**
- **Template method pattern**: Common flow, specific implementations
- **Dependency injection**: `PasswordResolver` injected via constructor
- **Resource management**: Automatic connection cleanup with try-with-resources
- **Error handling**: Centralized exception handling and logging

#### 2.3 Specific Service Implementation

**File:** `src/main/java/com/baml/mav/aieutil/service/ProcedureExecutionService.java`

```java
public class ProcedureExecutionService extends BaseDatabaseExecutionService {
    @Override
    protected ExecutionResult executeWithConnection(DatabaseRequest request, Connection conn) throws SQLException {
        ProcedureRequest procRequest = (ProcedureRequest) request;
        
        Map<String, Object> result = DatabaseService.executeProcedure(
                conn,
                procRequest.getProcedure(),
                procRequest.getInput(),
                procRequest.getOutput());

        return ExecutionResult.success(result);
    }
}
```

**Key Design Decisions:**
- **Single responsibility**: Only handles procedure execution
- **Type safety**: Safe casting with validation
- **Delegation**: Delegates to `DatabaseService` for actual execution

### Phase 3: CLI Layer (User Interface)

#### 3.1 Base CLI Command

**File:** `src/main/java/com/baml/mav/aieutil/cli/BaseDatabaseCliCommand.java`

```java
public abstract class BaseDatabaseCliCommand implements Callable<Integer> {
    @Option(names = { "-t", "--type" }, description = "Database type", defaultValue = "oracle")
    protected String type;

    @Option(names = { "-d", "--database" }, description = "Database name", required = true)
    protected String database;

    @Option(names = { "-u", "--user" }, description = "Database username", required = true)
    protected String user;

    // Vault options for authentication
    @Option(names = "--vault-url", description = "Vault base URL")
    protected String vaultUrl;
    
    // ... other vault options
}
```

**Key Design Decisions:**
- **PicoCLI integration**: Standard CLI framework with annotations
- **Common options**: Shared across all database commands
- **Vault support**: Built-in vault authentication options
- **Abstract class**: Template for specific commands

#### 3.2 Specific CLI Command

**File:** `src/main/java/com/baml/mav/aieutil/ExecProcedureCmd.java`

```java
@Command(name = "exec-proc", mixinStandardHelpOptions = true, 
         description = "Vault-authenticated procedure execution")
public class ExecProcedureCmd extends BaseDatabaseCliCommand {

    @Parameters(index = "0", description = "Stored procedure name")
    String procedure;

    @Option(names = "--input", description = "Input parameters (name:type:value,name:type:value)")
    String input;

    @Option(names = "--output", description = "Output parameters (name:type,name:type)")
    String output;

    public ExecProcedureCmd() {
        super(createService());
    }

    private static ProcedureExecutionService createService() {
        return new ProcedureExecutionService(
                new PasswordResolver(new ExecProcedureCmd()::promptForPassword));
    }

    @Override
    public Integer call() {
        try {
            ProcedureRequest request = ProcedureRequest.builder()
                    .type(type)
                    .database(database)
                    .user(user)
                    .procedure(procedure)
                    .input(input)
                    .output(output)
                    .vaultConfig(createVaultConfig())
                    .build();

            ExecutionResult result = service.execute(request);
            result.formatOutput(System.out);
            return result.getExitCode();

        } catch (Exception e) {
            return ExceptionUtils.handleCliException(e, "execute procedure", System.err);
        }
    }
}
```

**Key Design Decisions:**
- **Command pattern**: Clear separation of CLI parsing and execution
- **Builder pattern**: Clean request construction
- **Error handling**: Consistent CLI error reporting
- **Service injection**: Dependency injection through constructor

## Key Design Patterns Explained

### 1. **Template Method Pattern**
**Where:** `BaseDatabaseExecutionService.execute()`
**Why:** Provides common execution flow while allowing specific implementations
**Benefit:** Reduces code duplication and ensures consistent behavior

### 2. **Strategy Pattern**
**Where:** `PasswordResolver.resolvePassword()` and `ConnectionStringGenerator`
**Why:** Multiple authentication and connection strategies
**Benefit:** Easy to add new authentication methods or database types

### 3. **Builder Pattern**
**Where:** `ProcedureRequest.builder()` and `DatabaseRequest.builder()`
**Why:** Complex object construction with optional parameters
**Benefit:** Readable, fluent API for request creation

### 4. **Dependency Injection**
**Where:** Constructor injection throughout the service layer
**Why:** Loose coupling and testability
**Benefit:** Easy to mock dependencies for testing

### 5. **Factory Pattern**
**Where:** `ExecutionResult.success()` and `ExecutionResult.failure()`
**Why:** Clean creation of different result types
**Benefit:** Encapsulates result creation logic

## Error Handling Strategy

### 1. **Layered Error Handling**
- **CLI Layer**: User-friendly error messages
- **Service Layer**: Business logic error context
- **Database Layer**: Technical error details

### 2. **Exception Wrapping**
```java
// Low-level exceptions wrapped with business context
throw ExceptionUtils.wrap(e, "Failed to execute procedure: " + procedure);
```

### 3. **Structured Logging**
```java
// Consistent error logging format
LoggingUtils.logStructuredError("procedure_execution", "execute", "FAILED", message, exception);
```

## Security Considerations

### 1. **Password Security**
- Passwords never logged
- Secure password prompting
- Vault integration for credential management

### 2. **SQL Injection Prevention**
- Parameterized queries in `ProcedureExecutor`
- Input validation at CLI level
- Type-safe parameter conversion

### 3. **Connection Security**
- Secure connection string generation
- LDAP integration for enterprise authentication
- Connection cleanup with try-with-resources

## Testing Strategy

### 1. **Unit Testing**
- Test each layer independently
- Mock dependencies for isolation
- Test error conditions and edge cases

### 2. **Integration Testing**
- Test complete authentication flow
- Test database connection and execution
- Test vault integration

### 3. **CLI Testing**
- Test argument parsing
- Test help and usage messages
- Test error handling and exit codes

## Performance Considerations

### 1. **Connection Management**
- Connection pooling through `DatabaseService`
- Automatic resource cleanup
- Efficient connection string generation

### 2. **Memory Management**
- Immutable objects reduce garbage collection
- Efficient parameter parsing
- Structured result formatting

### 3. **Logging Performance**
- Structured logging for monitoring
- Minimal console output
- Debug logging only when needed

## Maintenance and Extensibility

### 1. **Adding New Commands**
- Extend `BaseDatabaseCliCommand`
- Create new service extending `BaseDatabaseExecutionService`
- Follow established patterns

### 2. **Adding New Database Types**
- Implement new strategy in `ConnectionStringGenerator`
- Update configuration files
- Add type validation

### 3. **Adding New Authentication Methods**
- Implement new strategy in `PasswordResolver`
- Update `PasswordRequest` if needed
- Maintain backward compatibility

## Conclusion

The `ExecProcedureCmd.java` implementation demonstrates enterprise-grade software engineering principles:

1. **Clear separation of concerns** across layers
2. **Robust error handling** with user-friendly messages
3. **Security-first design** with vault integration
4. **Testable architecture** with dependency injection
5. **Maintainable code** with established patterns
6. **Extensible design** for future enhancements

This architecture provides a solid foundation for building additional CLI commands while maintaining consistency, security, and performance.

 