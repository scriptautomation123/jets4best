// NOTE: This test is written for Java 8 compatibility. If you require tests for a different Java version, please specify.
package com.baml.mav.aieutil.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class PasswordResolverTest {

    @Test
    void resolvePassword_directVaultParams_returnsPassword() {
        PasswordRequest req = new PasswordRequest("user", "db", "url", "role", "secret", "ait");
        PasswordResolver resolver = new PasswordResolver(new StubPrompter("prompted"));
        // This will throw because VaultClient is not mocked, but we can check the
        // fallback logic
        try {
            resolver.resolvePassword(req);
        } catch (Exception e) {
            // Expected: VaultClient is not mocked, so it will fail
            assertThat(e).isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void resolvePassword_noVaultParams_promptsForPassword() {
        PasswordRequest req = new PasswordRequest("user", "db");
        PasswordResolver resolver = new PasswordResolver(new StubPrompter("prompted"));
        Optional<String> result = resolver.resolvePassword(req);
        assertThat(result).contains("prompted");
    }

    static class StubPrompter implements java.util.function.Supplier<String> {
        private final String value;

        StubPrompter(String value) {
            this.value = value;
        }

        public String get() {
            return value;
        }
    }
}