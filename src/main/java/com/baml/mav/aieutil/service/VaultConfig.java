package com.baml.mav.aieutil.service;

public final class VaultConfig {
    private final String vaultUrl;
    private final String roleId;
    private final String secretId;
    private final String ait;

    public VaultConfig(String vaultUrl, String roleId, String secretId, String ait) {
        this.vaultUrl = vaultUrl;
        this.roleId = roleId;
        this.secretId = secretId;
        this.ait = ait;
    }

    // Getters
    public String getVaultUrl() {
        return vaultUrl;
    }

    public String getRoleId() {
        return roleId;
    }

    public String getSecretId() {
        return secretId;
    }

    public String getAit() {
        return ait;
    }

    public boolean hasDirectVaultParams() {
        return vaultUrl != null && !vaultUrl.trim().isEmpty() &&
                roleId != null && !roleId.trim().isEmpty() &&
                secretId != null && !secretId.trim().isEmpty() &&
                ait != null && !ait.trim().isEmpty();
    }

    public static VaultConfig empty() {
        return new VaultConfig(null, null, null, null);
    }
}