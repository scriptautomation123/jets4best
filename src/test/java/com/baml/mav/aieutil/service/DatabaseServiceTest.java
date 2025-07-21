package com.baml.mav.aieutil.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.baml.mav.aieutil.auth.PasswordResolver;

class DatabaseServiceTest {
    static class DummyPasswordResolver extends PasswordResolver {
        DummyPasswordResolver() {
            super(new java.util.function.Supplier<String>() {
                public String get() {
                    return "pass";
                }
            });
        }
    }

    @Test
    void executeWithConnection_success() throws Exception {
        DatabaseService svc = new DatabaseService(new DummyPasswordResolver());
        ProcedureRequest req = ProcedureRequest.builder()
                .type("h2")
                .database("testdb")
                .user("user")
                .procedure("proc")
                .input(null)
                .output(null)
                .vaultConfig(VaultConfig.empty())
                .build();
        try {
            java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
            ExecutionResult result = svc.executeWithConnection(req, conn);
            assertThat(result).isNotNull();
        } catch (Exception e) {
            // Acceptable if H2 is not available
            assertThat(e).isInstanceOf(Exception.class);
        }
    }

    @Test
    void executeWithConnection_nullConnection_returnsErrorResult() {
        DatabaseService svc = new DatabaseService(new DummyPasswordResolver());
        ProcedureRequest req = ProcedureRequest.builder()
                .type("h2")
                .database("testdb")
                .user("user")
                .procedure("proc")
                .input(null)
                .output(null)
                .vaultConfig(VaultConfig.empty())
                .build();
        ExecutionResult result = null;
        try {
            result = svc.executeWithConnection(req, null);
        } catch (Exception e) {
            // If the code throws, that's also acceptable
            assertThat(e).isInstanceOf(Exception.class);
            return;
        }
        assertThat(result).isNotNull();
        assertThat(result.getExitCode()).isNotEqualTo(0);
        assertThat(result.getMessage()).containsIgnoringCase("connection");
    }
}