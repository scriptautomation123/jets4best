# AIE Utility - Database CLI Tool

A Java-based CLI tool for executing database procedures with Vault authentication support.

## Prerequisites

### Java Development Environment

**VS Code Setup:**
```
Ctrl + Shift + P → Java: Configure Java Runtime
```
Select the Java version you're developing with.

## Building the Project

### Linux/WSL
```bash
# Set Java version
source jdk.sh  # Choose the JDK

# Build with Java 8 (default)
mvn clean package

# Build with Java 21
mvn clean package -Pjava21
```

### Windows
```bash
# Set Java version
jdk.bat  # Double click to choose the JDK

# Build with Java 8 (default)
mvn clean package

# Build with Java 21
mvn clean package -Pjava21
```

## Running the Application

### Vault Client Testing
```bash
# Build the project first
mvn clean package

# Run vault client integration tests
 java -cp target/aieutil-1.0.0.jar com.baml.mav.aieutil.validate.VaultClientTest
```

### Executing Stored Procedures

The application supports executing Oracle stored procedures with Vault authentication:

```bash
./run.sh \
  -d ECICMD03_svc01 \
  -u MAV_T2T_APP \
  "MAV_OWNER.TempTable_Onehadoop_proc" \
  --input "in_schema:VARCHAR2:MAV_OWNER,in_src_table_nm:VARCHAR2:CUST_INSGHT_DLY,in_typ_cd:INTEGER:136,in_prant_grp:VARCHAR2:HADOOP_DML_ROLE" \
  --output "p_outmsg:STRING"
```

#### Command Parameter Mapping

**BaseDatabaseCliCommand Options:**
- `-d ECICMD03_svc01` → `database = "ECICMD03_svc01"`
- `-u MAV_T2T_APP` → `user = "MAV_T2T_APP"`
- `-t oracle` → `type = "oracle"` (default, not specified)

**ExecProcedureCmd Options:**
- `"MAV_OWNER.TempTable_Onehadoop_proc"` → `procedure = "MAV_OWNER.TempTable_Onehadoop_proc"`
- `--input "..."` → `input = "in_schema:VARCHAR2:MAV_OWNER,in_src_table_nm:VARCHAR2:CUST_INSGHT_DLY,in_typ_cd:INTEGER:136,in_prant_grp:VARCHAR2:HADOOP_DML_ROLE"`
- `--output "..."` → `output = "p_outmsg:STRING"`

#### Execution Flow

1. **CLI Parsing**: PicoCLI parses your command arguments
2. **Request Building**: Creates a `ProcedureRequest` with all parameters
3. **Service Execution**: Calls `ProcedureExecutionService.execute()`
4. **Password Resolution**: Uses `PasswordResolver` to get password from vault
5. **Database Connection**: Creates connection using `DatabaseService`
6. **Procedure Execution**: Executes the stored procedure via `ProcedureExecutor`
7. **Output Formatting**: Returns results via `ExecutionResult`

#### Vault Integration

The command automatically:
- Looks up vault parameters for `MAV_T2T_APP` and `ECICMD03_svc01`
- Authenticates with Vault using the configured parameters
- Retrieves the Oracle password
- Uses LDAP connection string (since no host is specified)

#### Expected Output

The command outputs procedure results in a clean format:
```
p_outmsg: [procedure output value]
```

## Architecture

The application follows a layered architecture:
- **CLI Layer**: PicoCLI command parsing and user interaction
- **Service Layer**: Business logic and orchestration
- **Database Layer**: Connection management and procedure execution
- **Vault Layer**: Authentication and password retrieval
- **Utility Layer**: Logging, configuration, and error handling

## Configuration

### Vault Configuration
Set the vault configuration file path:
```bash
-Dvault.config=/path/to/vaults.yaml
```

### Application Configuration
The application uses `application.yaml` for database connection templates and other settings.
