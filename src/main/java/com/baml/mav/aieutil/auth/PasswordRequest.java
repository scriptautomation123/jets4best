package com.baml.mav.aieutil.auth;

import java.util.Objects;

public final class PasswordRequest {
    private final String user;
    private final String database;
    private final String vaultUrl;
    private final String roleId;
    private final String secretId;
    private final String ait;

    // Constructor for vault lookup (no direct params)
    public PasswordRequest(String user, String database) {
        this(user, database, null, null, null, null);
    }

    // Constructor for direct vault params
    public PasswordRequest(String user, String database, String vaultUrl, String roleId, String secretId, String ait) {
        this.user = Objects.requireNonNull(user, "User cannot be null");
        this.database = Objects.requireNonNull(database, "Database cannot be null");
        this.vaultUrl = vaultUrl;
        this.roleId = roleId;
        this.secretId = secretId;
        this.ait = ait;

        validateVaultParameters();
    }

    private void validateVaultParameters() {
        boolean hasSomeVaultParams = vaultUrl != null || roleId != null || secretId != null || ait != null;
        boolean hasAllVaultParams = hasDirectVaultParams();

        if (hasSomeVaultParams && !hasAllVaultParams) {
            throw new IllegalArgumentException(
                    "Invalid vault parameter combination. Either provide all vault parameters or none.");
        }
    }

    public boolean hasDirectVaultParams() {
        // Optimized check - avoid stream for simple validation
        return vaultUrl != null && !vaultUrl.trim().isEmpty() &&
                roleId != null && !roleId.trim().isEmpty() &&
                secretId != null && !secretId.trim().isEmpty() &&
                ait != null && !ait.trim().isEmpty();
    }

    // Getters with validation
    public String getUser() {
        return user;
    }

    public String getDatabase() {
        return database;
    }

    public String getVaultUrl() {
        if (!hasDirectVaultParams()) {
            throw new IllegalStateException("Vault URL not available - direct vault params not provided");
        }
        return vaultUrl;
    }

    public String getRoleId() {
        if (!hasDirectVaultParams()) {
            throw new IllegalStateException("Role ID not available - direct vault params not provided");
        }
        return roleId;
    }

    public String getSecretId() {
        if (!hasDirectVaultParams()) {
            throw new IllegalStateException("Secret ID not available - direct vault params not provided");
        }
        return secretId;
    }

    public String getAit() {
        if (!hasDirectVaultParams()) {
            throw new IllegalStateException("AIT not available - direct vault params not provided");
        }
        return ait;
    }
}