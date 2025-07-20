# Java CLI Command Architecture: ExecProcedureCmd Deep Dive

## Table of Contents

1. [Overview](#overview)
2. [Architecture Overview](#architecture-overview)
3. [Constructor Chain Flow](#constructor-chain-flow)
4. [PicoCLI Framework Integration](#picocli-framework-integration)
5. [Execution Flow](#execution-flow)
6. [Code Analysis](#code-analysis)
7. [Design Patterns](#design-patterns)
8. [Identifying Parent vs Child Code](#identifying-parent-vs-child-code)
9. [Principal Engineer Recommendations](#principal-engineer-recommendations)
10. [Best Practices](#best-practices)

---

## Overview

This document provides a comprehensive analysis of the `ExecProcedureCmd` class and its relationship with the `BaseDatabaseCliCommand` base class. It explains the constructor chaining, dependency injection, PicoCLI framework integration, and execution flow.

## Architecture Overview

### Class Hierarchy
```
BaseDatabaseCliCommand (abstract)
    ↓ extends
ExecProcedureCmd (concrete)
```

### Key Components
- **BaseDatabaseCliCommand**: Abstract base class with common CLI options and service injection
- **ExecProcedureCmd**: Concrete implementation for procedure execution
- **ProcedureExecutionService**: Service that handles the actual database operations
- **PasswordResolver**: Strategy for password resolution
- **PicoCLI Framework**: Handles command-line parsing and execution

### Core Design Principles
- **Dependency Injection**: Service is injected via constructor
- **Template Method Pattern**: Base class defines structure, subclasses customize
- **Strategy Pattern**: Different services can be injected
- **Inheritance**: Subclasses inherit fields and methods from base class

## Constructor Chain Flow

### Step 1: ExecProcedureCmd Instantiation
**When:** `new ExecProcedureCmd()` is called
**Where:** In `ExecProcedureCmd.main()` or when PicoCLI creates the command 

```java
// In ExecProcedureCmd.java
public ExecProcedureCmd() {
    super(createService());  // Calls parent constructor, passing the service as a parameter
}
```

**What "with service" means:**
- `createService()` returns a `ProcedureExecutionService` object
- `super(createService())` calls the parent constructor and passes that service object as an argument
- The parent constructor receives the service and stores it in the `service` field

### Step 2: Service Creation
**What:** `createService()` is a private static method in `ExecProcedureCmd` class that creates the specific service implementation

```java
// In ExecProcedureCmd.java
private static ProcedureExecutionService createService() {
    return new ProcedureExecutionService(
            new PasswordResolver(new ExecProcedureCmd()::promptForPassword));
}
```

**What this method does:**
- **Method Type**: `private static` method in `ExecProcedureCmd` class
- **Returns**: `ProcedureExecutionService` instance
- **Creates**: A `ProcedureExecutionService` (which extends `BaseDatabaseExecutionService`)
- **Injects**: A `PasswordResolver` with a method reference to `promptForPassword()`
- **Purpose**: Factory method to create the specific service for procedure execution

### Step 3: Parent Constructor Call
**What:** `super(createService())` invokes the parent constructor

```java
// In BaseDatabaseCliCommand.java
protected BaseDatabaseCliCommand(BaseDatabaseExecutionService service) {
    this.service = service;  // Stores service in protected final field
}
```

**What this does:**
- Receives the `ProcedureExecutionService` instance
- Stores it in the `protected final BaseDatabaseExecutionService service` field
- Makes it available to all subclasses

### Step 4: Complete Constructor Flow Diagram
```
1. new ExecProcedureCmd()
   ↓
2. ExecProcedureCmd() constructor
   ↓ calls super(createService())
3. createService() method
   ↓ creates
4. new ProcedureExecutionService(new PasswordResolver(...))
   ↓ passes to
5. BaseDatabaseCliCommand(BaseDatabaseExecutionService service)
   ↓ stores in
6. protected final BaseDatabaseExecutionService service field
   ↓ available to
7. ExecProcedureCmd.call() method
   ↓ uses
8. service.execute(request)
```

## PicoCLI Framework Integration

### Why the call() Method Executes

**When PicoCLI calls the command:**
```java
// In ExecProcedureCmd.main()
CommandLine cmd = new CommandLine(new ExecProcedureCmd());  // Constructor chain completes here
cmd.execute(args);  // This triggers the call() method
```

### The Callable Interface
```java
// BaseDatabaseCliCommand implements Callable<Integer>
public abstract class BaseDatabaseCliCommand implements Callable<Integer> {
    // This means it must have a call() method
}
```

### PicoCLI's Execution Flow
```
cmd.execute(args)
    ↓
PicoCLI parses arguments and populates fields
    ↓
PicoCLI checks if command implements Callable
    ↓
PicoCLI automatically calls command.call()
    ↓
Your call() method executes
```

### The Callable Contract
```java
// From java.util.concurrent.Callable interface
public interface Callable<V> {
    V call() throws Exception;  // This is what PicoCLI calls
}
```

### Why Callable?
- **Standard Java interface** for tasks that return a result
- **PicoCLI integration** - PicoCLI knows to call `call()` when it sees `Callable`
- **Return value** - The `Integer` return type becomes the exit code
- **Exception handling** - PicoCLI handles exceptions from `call()` method

## Execution Flow

### The call() Method Execution
```java
// In ExecProcedureCmd.call()
@Override
public Integer call() {
    try {
        // 1. Build the request using inherited fields
        ProcedureRequest request = ProcedureRequest.builder()
                .type(type)           // Inherited from BaseDatabaseCliCommand
                .database(database)   // Inherited from BaseDatabaseCliCommand  
                .user(user)           // Inherited from BaseDatabaseCliCommand
                .procedure(procedure) // Specific to ExecProcedureCmd
                .input(input)         // Specific to ExecProcedureCmd
                .output(output)       // Specific to ExecProcedureCmd
                .vaultConfig(createVaultConfig())  // Inherited method
                .build();

        // 2. Use the injected service to execute
        ExecutionResult result = service.execute(request);  // Uses the service from constructor
        result.formatOutput(System.out);
        return result.getExitCode();

    } catch (Exception e) {
        return ExceptionUtils.handleCliException(e, "execute procedure", System.err);
    }
}
```

### The service.execute() Flow
```
service.execute(request)
    ↓
BaseDatabaseExecutionService.execute() (Template Method)
    ↓
1. resolvePassword(request) - Uses the PasswordResolver from constructor
    ↓
2. createConnection(request, password) - Creates database connection
    ↓
3. executeWithConnection(request, conn) - Executes the procedure
    ↓
4. Returns ExecutionResult
```

### Key Points
- **Constructor chain** sets up the object with all dependencies
- **PicoCLI** calls `call()` method when user executes the command
- **Inherited fields** (type, database, user) are populated by PicoCLI from command line arguments
- **Injected service** handles the actual database operations
- **Password resolution** happens automatically using the PasswordResolver from constructor

## Code Analysis

### Actual Code from ExecProcedureCmd.java

```java
// Constructor with service injection
public ExecProcedureCmd() {
    super(createService());  // Calls parent constructor with service
}

// Service creation method
private static ProcedureExecutionService createService() {
    return new ProcedureExecutionService(
            new PasswordResolver(new ExecProcedureCmd()::promptForPassword));
}

// Main method - CLI entry point
public static void main(String[] args) {
    try {
        LoggingUtils.logCliStartup(System.getProperty("java.home"));

        CommandLine cmd = new CommandLine(new ExecProcedureCmd());  // Creates instance
        cmd.setExecutionExceptionHandler(new ExceptionUtils.ExecutionExceptionHandler());
        cmd.setParameterExceptionHandler(new ExceptionUtils.ParameterExceptionHandler());

        System.exit(cmd.execute(args)); // NOSONAR - CLI application must exit with appropriate code
    } catch (Exception e) {
        LoggingUtils.logMinimalError(e);
        System.exit(CommandLine.ExitCode.SOFTWARE); // NOSONAR - CLI application must exit with appropriate code
    }
}

// Core execution method
@Override
public Integer call() {
    try {
        // Build request using inherited fields (type, database, user) and specific fields (procedure, input, output)
        ProcedureRequest request = ProcedureRequest.builder()
                .type(type)           // Inherited from BaseDatabaseCliCommand
                .database(database)   // Inherited from BaseDatabaseCliCommand  
                .user(user)           // Inherited from BaseDatabaseCliCommand
                .procedure(procedure) // Specific to ExecProcedureCmd
                .input(input)         // Specific to ExecProcedureCmd
                .output(output)       // Specific to ExecProcedureCmd
                .vaultConfig(createVaultConfig())  // Inherited method from BaseDatabaseCliCommand
                .build();

        // Use the injected service (inherited field) to execute the request
        ExecutionResult result = service.execute(request);  // service is inherited from BaseDatabaseCliCommand
        result.formatOutput(System.out); // NOSONAR
        return result.getExitCode();

    } catch (Exception e) {
        return ExceptionUtils.handleCliException(e, "execute procedure", System.err); // NOSONAR
    }
}
```

### Code Analysis Summary

**Constructor:** `super(createService())` - This is where the constructor chain begins
**Service Creation:** `createService()` - Creates the specific service with password resolver
**Main Method:** `new ExecProcedureCmd()` - This triggers the constructor chain
**Execution Method:** `@Override public Integer call()` - This is the main execution method
**Request Building:** Uses both inherited and specific fields
**Service Usage:** `service.execute(request)` - Uses the inherited service field
**Output Handling:** `result.formatOutput(System.out)` - Handles output formatting
**Exit Code:** `return result.getExitCode()` - Returns appropriate exit code

### Key Observations

1. **Inheritance in Action**: The `call()` method uses `type`, `database`, `user` (inherited) and `procedure`, `input`, `output` (specific)
2. **Service Usage**: `service.execute(request)` uses the service injected via constructor
3. **Method Inheritance**: `createVaultConfig()` is inherited from the base class
4. **Error Handling**: Consistent exception handling pattern
5. **CLI Integration**: PicoCLI integration with proper exit codes

## Design Patterns

### Key Design Patterns Demonstrated

1. **Dependency Injection**: Service is injected via constructor, not created internally
2. **Template Method**: Base class defines structure, subclasses provide specific implementations
3. **Strategy Pattern**: Different services (ProcedureExecutionService, SqlExecutionService) can be injected
4. **Inheritance**: Subclasses inherit fields and methods from base class
5. **Method Reference**: `new ExecProcedureCmd()::promptForPassword` provides password prompting strategy

### Why This Design Works

- **Testability**: Can inject mock services for unit testing
- **Flexibility**: Different commands can use different service implementations
- **Consistency**: All database commands follow the same pattern
- **Separation of Concerns**: CLI parsing vs business logic are separated
- **Reusability**: Base class can be extended for new command types

## Identifying Parent vs Child Code

**Inheritance can be confusing! Here are ways to identify what comes from where:**

### Method 1: Visual Indicators in Code Comments
```java
// In ExecProcedureCmd.call() - CLEARLY MARKED
@Override
public Integer call() {
    try {
        // === INHERITED FROM PARENT (BaseDatabaseCliCommand) ===
        ProcedureRequest request = ProcedureRequest.builder()
                .type(type)           // ← Inherited field
                .database(database)   // ← Inherited field  
                .user(user)           // ← Inherited field
                .vaultConfig(createVaultConfig())  // ← Inherited method
                .build();

        // === SPECIFIC TO CHILD (ExecProcedureCmd) ===
        ProcedureRequest request = ProcedureRequest.builder()
                .procedure(procedure) // ← Child-specific field
                .input(input)         // ← Child-specific field
                .output(output)       // ← Child-specific field
                .build();

        // === INHERITED SERVICE USAGE ===
        ExecutionResult result = service.execute(request);  // ← Inherited field
        return result.getExitCode();

    } catch (Exception e) {
        return ExceptionUtils.handleCliException(e, "execute procedure", System.err);
    }
}
```

### Method 2: IDE Features
- **Ctrl+Click** (or Cmd+Click) on any field/method to see where it's defined
- **"Go to Declaration"** shows the exact class where something is defined
- **"Find Usages"** shows where inherited members are used

### Method 3: Code Documentation Pattern
```java
/**
 * ExecProcedureCmd - Child class for procedure execution
 * 
 * INHERITED FROM BaseDatabaseCliCommand:
 * - Fields: type, database, user, vaultUrl, roleId, secretId, ait
 * - Methods: createVaultConfig(), promptForPassword()
 * - Service: protected final BaseDatabaseExecutionService service
 * 
 * SPECIFIC TO ExecProcedureCmd:
 * - Fields: procedure, input, output
 * - Methods: createService()
 * - Constructor: ExecProcedureCmd()
 */
public class ExecProcedureCmd extends BaseDatabaseCliCommand {
    // Child-specific fields
    String procedure;  // ← CHILD ONLY
    String input;      // ← CHILD ONLY
    String output;     // ← CHILD ONLY
    
    // Inherited fields (not visible here but available)
    // String type;        ← INHERITED FROM PARENT
    // String database;    ← INHERITED FROM PARENT
    // String user;        ← INHERITED FROM PARENT
    // BaseDatabaseExecutionService service; ← INHERITED FROM PARENT
}
```

### Method 4: Clear Naming Conventions
```java
// Parent class - clear naming
public abstract class BaseDatabaseCliCommand {
    // Common database fields
    protected String databaseType;      // ← Clearly common
    protected String databaseName;      // ← Clearly common
    protected String databaseUser;      // ← Clearly common
    
    // Common service
    protected final BaseDatabaseExecutionService databaseService;  // ← Clearly common
}

// Child class - specific naming
public class ExecProcedureCmd extends BaseDatabaseCliCommand {
    // Procedure-specific fields
    private String procedureName;       // ← Clearly specific
    private String procedureInput;      // ← Clearly specific
    private String procedureOutput;     // ← Clearly specific
}
```

### Method 5: Interface Segregation
```java
// Define clear interfaces
public interface DatabaseCommand {
    String getType();      // Common
    String getDatabase();  // Common
    String getUser();      // Common
}

public interface ProcedureCommand extends DatabaseCommand {
    String getProcedure(); // Specific
    String getInput();     // Specific
    String getOutput();    // Specific
}

// Implementation makes it clear
public class ExecProcedureCmd implements ProcedureCommand {
    // Implementation shows what's common vs specific
}
```

### Method 6: Composition Over Inheritance
```java
// Instead of inheritance, use composition
public class ExecProcedureCmd {
    private final DatabaseCommandOptions options;  // ← Common options
    private final ProcedureOptions procedureOptions;  // ← Specific options
    private final DatabaseService service;  // ← Common service
    
    public ExecProcedureCmd() {
        this.options = new DatabaseCommandOptions();
        this.procedureOptions = new ProcedureOptions();
        this.service = new ProcedureExecutionService(new PasswordResolver(this::promptForPassword));
    }
}
```

## Principal Engineer Recommendations

**Based on Context7 Java Design Patterns and industry best practices:**

### For This Specific CLI Architecture: KEEP INHERITANCE

**Why inheritance is better here:**

1. **Framework Integration**: PicoCLI expects `Callable<Integer>` interface - inheritance is the natural fit
2. **Template Method Pattern**: The base class provides a clear execution template that subclasses customize
3. **Minimal Complexity**: The inheritance hierarchy is shallow (only 2 levels) and well-defined
4. **Standard Pattern**: This follows established CLI framework patterns

### However, Composition Would Improve Readability in These Areas:

**1. Service Injection (Already Good)**
```java
// Current approach is already composition-based
protected final BaseDatabaseExecutionService service;  // ← Composition
```

**2. Password Resolution (Could Be Better)**
```java
// Instead of inheritance, use composition
public class ExecProcedureCmd {
    private final DatabaseOptions options;           // ← Composition
    private final ProcedureOptions procedureOptions; // ← Composition  
    private final PasswordResolver passwordResolver; // ← Composition
    private final DatabaseService databaseService;   // ← Composition
}
```

**3. Request Building (Could Be Better)**
```java
// Instead of mixing inherited + specific fields
public class ExecProcedureCmd {
    private final DatabaseRequest.Builder dbRequest;    // ← Composition
    private final ProcedureRequest.Builder procRequest; // ← Composition
}
```

### My Recommendation: Hybrid Approach

**Keep the current inheritance for:**
- PicoCLI integration (`Callable<Integer>`)
- Common CLI options (type, database, user)
- Template method execution flow

**Add composition for:**
- Service dependencies (already done)
- Request building components
- Configuration objects

### Why This Hybrid Approach:

1. **Leverages strengths of both patterns**
2. **Maintains framework compatibility**
3. **Improves testability and readability**
4. **Follows "composition over inheritance" where it makes sense**
5. **Keeps inheritance where it's the natural fit**

### Implementation Priority:

1. **High Priority**: Keep current inheritance (it works well)
2. **Medium Priority**: Add composition for request building
3. **Low Priority**: Consider composition for password resolution

### Bottom Line:

The current inheritance approach is actually quite good for this use case. The confusion comes from not having clear documentation, not from the inheritance itself. Adding better comments and documentation would solve most readability issues without the complexity of refactoring to composition.

**Key Design Patterns from Context7 that support this decision:**

1. **Template Method Pattern**: Perfect for CLI command execution flow
2. **Strategy Pattern**: Service injection via composition
3. **Delegation Pattern**: Service delegation for business logic
4. **Factory Method**: `createService()` method for service creation

**Industry Best Practices:**
- Use inheritance for "is-a" relationships (ExecProcedureCmd IS-A BaseDatabaseCliCommand)
- Use composition for "has-a" relationships (ExecProcedureCmd HAS-A DatabaseService)
- Keep inheritance hierarchies shallow (2-3 levels max)
- Document inheritance relationships clearly
- Consider composition when inheritance becomes complex

This hybrid approach follows the principle of "use the right tool for the job" rather than dogmatically following "composition over inheritance" in all cases.

## Best Practices

### Methods to Identify Parent vs Child Code:

1. **Visual Indicators**
   - Use clear comments with arrows (←) to mark inherited vs specific code
   - Use section headers like "=== INHERITED FROM PARENT ==="

2. **IDE Features**
   - Ctrl+Click to see where fields/methods are defined
   - Go to Declaration shows the exact class
   - Find Usages shows inheritance relationships

3. **Documentation Pattern**
   - Document inheritance in JavaDoc comments
   - List inherited vs specific members clearly

4. **Clear Naming**
   - Use descriptive names that indicate scope
   - `databaseType` vs `procedureName` makes it obvious

5. **Interface Segregation**
   - Define clear interfaces for common vs specific functionality
   - Makes inheritance relationships explicit

6. **Composition Alternative**
   - Consider composition over inheritance for clarity
   - Each component has a clear, single responsibility

### Why This Matters:

Inheritance can be confusing because:
- Inherited code isn't visible in the child class
- Multiple inheritance levels make it hard to trace
- Mixed responsibilities blur the line between parent and child

These methods help make the code more readable and maintainable by clearly showing what comes from where.

### Best Practices for Clarity:

1. **Use clear comments** to mark inherited vs specific code
2. **Use IDE features** to navigate to declarations
3. **Use descriptive naming** that indicates scope
4. **Document inheritance** in class JavaDoc
5. **Consider composition** for complex inheritance hierarchies
6. **Use interfaces** to define clear contracts