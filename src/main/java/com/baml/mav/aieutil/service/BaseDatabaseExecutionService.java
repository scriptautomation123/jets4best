package com.baml.mav.aieutil.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

import org.apache.logging.log4j.Logger;

import com.baml.mav.aieutil.auth.PasswordRequest;
import com.baml.mav.aieutil.auth.PasswordResolver;
import com.baml.mav.aieutil.util.ExceptionUtils;
import com.baml.mav.aieutil.util.LoggingUtils;

public abstract class BaseDatabaseExecutionService {
    private static final Logger logger = LoggingUtils.getLogger(BaseDatabaseExecutionService.class);

    private final PasswordResolver passwordResolver;

    protected BaseDatabaseExecutionService(PasswordResolver passwordResolver) {
        this.passwordResolver = passwordResolver;

    }

    // Common password resolution logic
    protected Optional<String> resolvePassword(DatabaseRequest request) {
        try {
            PasswordRequest passwordRequest = new PasswordRequest(
                    request.getUser(),
                    request.getDatabase(),
                    request.getVaultConfig().getVaultUrl(),
                    request.getVaultConfig().getRoleId(),
                    request.getVaultConfig().getSecretId(),
                    request.getVaultConfig().getAit());

            return passwordResolver.resolvePassword(passwordRequest);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid password request parameters", e);
            throw e;
        }
    }

    // Common connection creation
    protected Connection createConnection(DatabaseRequest request, String password) throws SQLException {
        return DatabaseService.createConnection(
                request.getType(),
                request.getDatabase(),
                request.getUser(),
                password);
    }

    // Abstract method for specific execution logic
    protected abstract ExecutionResult executeWithConnection(DatabaseRequest request, Connection conn)
            throws SQLException;

    // Template method pattern
    public ExecutionResult execute(DatabaseRequest request) {
        try {
            Optional<String> password = resolvePassword(request);
            if (!password.isPresent()) {
                return ExecutionResult.failure(1, "[ERROR] Failed to resolve password for user: " + request.getUser());
            }

            try (Connection conn = createConnection(request, password.get())) {
                return executeWithConnection(request, conn);
            }

        } catch (Exception e) {
            logger.error("Failed to execute database operation", e);
            throw ExceptionUtils.wrap(e, "Failed to execute database operation");
        }
    }
}