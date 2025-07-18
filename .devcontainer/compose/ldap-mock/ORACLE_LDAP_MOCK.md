# Oracle LDAP Mock Implementation

This implementation provides a comprehensive mock for Oracle LDAP authentication that supports the original connection string format used in your production environment.

## Original Production Connection String

```
jdbc:oracle:thin:@ldap://oid1puser.bankofamerica.com:389/ECICMD03_SVC01,cn=oracleContext,dc=bankofamerica,dc=com ldap://oid2puser.bankofamerica.com:389/ECICMD03_SVC01,cn=oracleContext,dc=bankofamerica,dc=com ldap://oid3puser.bankofamerica.com:389/ECICMD03_SVC01,cn=oracleContext,dc=bankofamerica,dc=com
```

## Mock Development Connection String

```
jdbc:oracle:thin:@ldap://localhost:1389/ECICMD03_SVC01,cn=oracleContext,dc=bankofamerica,dc=com ldap://localhost:1390/ECICMD03_SVC01,cn=oracleContext,dc=bankofamerica,dc=com ldap://localhost:1391/ECICMD03_SVC01,cn=oracleContext,dc=bankofamerica,dc=com
```

## Components

### 1. Mock LDAP Servers
- **mock-ldap-1**: Port 1389 (simulates oid1puser.bankofamerica.com)
- **mock-ldap-2**: Port 1390 (simulates oid2puser.bankofamerica.com)
- **mock-ldap-3**: Port 1391 (simulates oid3puser.bankofamerica.com)

### 2. Oracle Services
- **ECICMD03_SVC01**: Maps to localhost:1522/XE
- **ORACLE_SVC01**: Maps to localhost:1521/XE

### 3. Mock Users
- **MAV_T2T_APP**: Access to both ECICMD03_SVC01 and ORACLE_SVC01
- **testuser**: Access to ORACLE_SVC01 only
- **ECICMD03_USER**: Access to ECICMD03_SVC01 only

## Usage

### Start Services
```bash
cd .devcontainer
docker-compose up -d
```

### Test the Mock Implementation
```bash
./test-oracle-ldap.sh
```

### Java Usage Example
```java
import com.baml.mav.aieutil.util.OracleLDAPConnectionHandler;

// Parse LDAP connection string
String ldapConnection = "jdbc:oracle:thin:@ldap://localhost:1389/ECICMD03_SVC01,cn=oracleContext,dc=bankofamerica,dc=com";
OracleLDAPConnectionHandler.ParsedLDAPConnection parsed = 
    OracleLDAPConnectionHandler.parseLDAPConnection(ldapConnection);

// Get Oracle connection info for user
OracleLDAPConnectionHandler.OracleConnectionInfo connInfo = 
    OracleLDAPConnectionHandler.getOracleConnectionInfo("ECICMD03_SVC01", "ECICMD03_USER");

// Use connection info
String jdbcUrl = connInfo.getJdbcUrl();
String username = connInfo.getUsername();
String password = connInfo.getPassword();
```

### Configuration Files Updated
- `application.yaml`: LDAP servers point to localhost:1389
- `docker-compose.yaml`: Added 3 mock LDAP servers
- `MockLDAPClient.java`: Updated to use service names instead of database URLs
- `OracleLDAPConnectionHandler.java`: New class to handle Oracle LDAP parsing

## Testing

The mock implementation supports:
1. **LDAP Service Lookup**: Returns TNS entries for Oracle services
2. **Multiple LDAP Servers**: Simulates production redundancy
3. **User Access Control**: Different users have access to different services
4. **Connection String Parsing**: Handles complex Oracle LDAP format
5. **Local Database Mapping**: Maps services to local Oracle instances

## Benefits

1. **Development Environment**: Full LDAP simulation without production dependencies
2. **Testing**: Comprehensive test coverage for LDAP scenarios
3. **Debugging**: Clear logging and error handling
4. **Flexibility**: Easy to add new users and services
5. **Production Compatibility**: Same connection string format as production

## Troubleshooting

1. **Check Services**: `docker-compose ps`
2. **Test LDAP Connectivity**: `nc -z localhost 1389`
3. **View Logs**: `docker-compose logs mock-ldap-1`
4. **Test Oracle Direct**: `docker exec -i ecicmd03-svc01 sqlplus sys/oracle123@//localhost:1521/XE as sysdba`

This mock implementation provides a complete Oracle LDAP authentication simulation that matches your production environment's connection string format while using local Oracle database instances.
