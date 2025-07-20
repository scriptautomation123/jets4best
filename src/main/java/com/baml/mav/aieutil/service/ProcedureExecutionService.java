package com.baml.mav.aieutil.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import com.baml.mav.aieutil.auth.PasswordResolver;

public class ProcedureExecutionService extends BaseDatabaseExecutionService {

    public ProcedureExecutionService(PasswordResolver passwordResolver) {
        super(passwordResolver);
    }

    @Override
    protected ExecutionResult executeWithConnection(DatabaseRequest request, Connection conn) throws SQLException {
        // Safe cast since this service only handles ProcedureRequest

        ProcedureRequest procRequest;
        if (request instanceof ProcedureRequest) {
            procRequest = (ProcedureRequest) request;
        } else {
            throw new IllegalArgumentException("Invalid request type: " + request.getClass().getName());
        }

        Map<String, Object> result = DatabaseService.executeProcedure(
                conn,
                procRequest.getProcedure(),
                procRequest.getInput(),
                procRequest.getOutput());

        return ExecutionResult.success(result);
    }
}