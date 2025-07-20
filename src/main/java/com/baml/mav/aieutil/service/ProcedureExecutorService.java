package com.baml.mav.aieutil.service;

import java.sql.Connection;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.Logger;

import com.baml.mav.aieutil.auth.PasswordRequest;
import com.baml.mav.aieutil.auth.PasswordResolver;
import com.baml.mav.aieutil.util.ExceptionUtils;
import com.baml.mav.aieutil.util.LoggingUtils;

public class ProcedureExecutorService {
    private static final Logger logger = LoggingUtils.getLogger(ProcedureExecutorService.class);

    private final PasswordResolver passwordResolver;

    public ProcedureExecutorService(PasswordResolver passwordResolver) {
        this.passwordResolver = passwordResolver;

    }

    public ExecutionResult executeProcedure(ProcedureRequest request) {
        try {
            if (request.isPasswordOnlyMode()) {
                return ExecutionResult.passwordOnlyMode();
            }

            Optional<String> password = resolvePassword(request);
            if (!password.isPresent()) {
                return ExecutionResult.failure(1, "[ERROR] Failed to resolve password for user: " + request.getUser());
            }

            Map<String, Object> result = executeProcedureWithPassword(request, password.get());
            return ExecutionResult.success(result);

        } catch (Exception e) {
            logger.error("Failed to execute procedure", e);
            throw ExceptionUtils.wrap(e, "Failed to execute procedure");
        }
    }

    private Optional<String> resolvePassword(ProcedureRequest request) {
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

    private Map<String, Object> executeProcedureWithPassword(ProcedureRequest request, String password) {
        try (Connection conn = DatabaseService.createConnection(
                request.getType(),
                request.getDatabase(),
                request.getUser(),
                password)) {

            return DatabaseService.executeProcedure(
                    conn,
                    request.getProcedure(),
                    request.getInput(),
                    request.getOutput());

        } catch (Exception e) {
            logger.error("Failed to execute procedure: {}", request.getProcedure(), e);
            throw ExceptionUtils.wrap(e, "Failed to execute procedure: " + request.getProcedure());
        }
    }
}
