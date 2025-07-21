package com.baml.mav.aieutil.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordRequestTest {

    @Test
    void hasDirectVaultParams_allPresent_true() {
        PasswordRequest req = new PasswordRequest("user", "db", "url", "role", "secret", "ait");
        assertThat(req.hasDirectVaultParams()).isTrue();
    }

    @Test
    void hasDirectVaultParams_missing_false_throws() {
        assertThatThrownBy(() -> new PasswordRequest("user", "db", null, "role", "secret", "ait"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid vault parameter combination");
    }

    @Test
    void constructor_invalidCombination_throws() {
        assertThatThrownBy(() -> new PasswordRequest("user", "db", "url", null, "secret", "ait"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}