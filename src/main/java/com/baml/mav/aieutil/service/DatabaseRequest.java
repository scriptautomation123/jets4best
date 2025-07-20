package com.baml.mav.aieutil.service;

import java.util.Objects;

public class DatabaseRequest {
    private final String type;
    private final String database;
    private final String user;
    private final VaultConfig vaultConfig;

    protected DatabaseRequest(Builder<?> builder) {
        this.type = Objects.requireNonNull(builder.type, "Database type cannot be null");
        this.database = Objects.requireNonNull(builder.database, "Database name cannot be null");
        this.user = Objects.requireNonNull(builder.user, "Database user cannot be null");
        this.vaultConfig = builder.vaultConfig != null ? builder.vaultConfig : VaultConfig.empty();
    }

    // Getters
    public String getType() {
        return type;
    }

    public String getDatabase() {
        return database;
    }

    public String getUser() {
        return user;
    }

    public VaultConfig getVaultConfig() {
        return vaultConfig;
    }

    public static class Builder<T extends Builder<T>> {
        protected String type;
        protected String database;
        protected String user;
        protected VaultConfig vaultConfig;

        public T type(String type) {
            this.type = type;
            return self();
        }

        public T database(String database) {
            this.database = database;
            return self();
        }

        public T user(String user) {
            this.user = user;
            return self();
        }

        public T vaultConfig(VaultConfig vaultConfig) {
            this.vaultConfig = vaultConfig;
            return self();
        }

        protected T self() {
            @SuppressWarnings("unchecked")
            T result = (T) this;
            return result;
        }

        public DatabaseRequest build() {
            return new DatabaseRequest(this);
        }
    }
}