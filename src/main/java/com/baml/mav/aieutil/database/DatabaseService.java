package com.baml.mav.aieutil.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;

import com.baml.mav.aieutil.util.ExceptionUtils;
import com.baml.mav.aieutil.util.LoggingUtils;

public class DatabaseService {
    private final String user;
    private final String password;
    private final String connectString;
    private final Logger log = LoggingUtils.getLogger(DatabaseService.class);

    private final SqlExecutor sqlExecutor;
    private final ProcedureExecutor procedureExecutor;

    // Private constructor - use factory methods
    private DatabaseService(String user, String password, String connectString) {
        this.user = user;
        this.password = password;
        this.connectString = connectString;

        // Load H2 driver if needed

        this.sqlExecutor = new SqlExecutor(this::getConnection);
        this.procedureExecutor = new ProcedureExecutor(this::getConnection);

        log.info("DatabaseService: user={}, connectString={}", user, connectString);
    }

    // Factory method for CLI usage - uses ConnectionManager
    public static DatabaseService create(String type, String database, String user, String password, String host) {
        ConnectionManager.ConnInfo connInfo = ConnectionManager.createConnection(type, database, user, password, host);
        return new DatabaseService(connInfo.user(), connInfo.password(), connInfo.url());
    }

    // High-level convenience methods - NO parameter parsing
    public SqlExecutor.SqlResult runSql(String sql, List<Object> params) throws SQLException {
        try {
            return sqlExecutor.executeSql(sql, params);
        } catch (Exception e) {
            ExceptionUtils.logAndRethrow(e, "Failed to run SQL: " + sql);
            throw new IllegalStateException("Unreachable");
        }
    }

    public void runSqlScript(String script, java.util.function.Consumer<SqlExecutor.SqlResult> resultHandler)
            throws SQLException {
        try {
            sqlExecutor.executeSqlScript(script, resultHandler::accept);
        } catch (Exception e) {
            ExceptionUtils.logAndRethrow(e, "Failed to run SQL script");
        }
    }

    // Core execution methods
    public Map<String, Object> executeProcedure(String procFullName,
            Map<String, Object> inputParams,
            Map<String, Integer> outputParams) throws SQLException {
        try {
            return procedureExecutor.executeProcedure(procFullName, inputParams, outputParams);
        } catch (Exception e) {
            ExceptionUtils.logAndRethrow(e, "Failed to execute procedure: " + procFullName);
            throw new IllegalStateException("Unreachable");
        }
    }

    // Helper methods
    private Connection getConnection() throws SQLException {
        try {
            return DriverManager.getConnection(connectString, user, password);
        } catch (Exception e) {
            ExceptionUtils.logAndRethrow(e, "Failed to get database connection for user=" + user);
            throw new IllegalStateException("Unreachable");
        }
    }
}