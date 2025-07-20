package com.baml.mav.aieutil.auth;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.baml.mav.aieutil.util.ExceptionUtils;
import com.baml.mav.aieutil.util.LoggingUtils;
import com.baml.mav.aieutil.util.VaultClient;

public class PasswordResolver {
    private static final String DIRECT_VAULT = "direct_vault";
    private static final String PASSWORD_RESOLUTION = "password_resolution";
    private static final String FAILED = "FAILED";
    private final Supplier<String> passwordPrompter;

    public PasswordResolver(Supplier<String> passwordPrompter) {
        this.passwordPrompter = Objects.requireNonNull(passwordPrompter, "Password prompter cannot be null");
    }

    public Optional<String> resolvePassword(PasswordRequest request) {
        Objects.requireNonNull(request, "PasswordRequest cannot be null");

        try {
            // Direct vault parameters
            if (request.hasDirectVaultParams()) {
                LoggingUtils.logPasswordResolution(request.getUser(), DIRECT_VAULT);
                return resolveWithDirectVaultParams(request);
            }

            // Vault lookup
            Optional<String> vaultPassword = resolveWithVaultLookup(request);
            if (vaultPassword.isPresent()) {
                LoggingUtils.logPasswordResolutionSuccess(request.getUser(), DIRECT_VAULT);
                return vaultPassword;
            }

            // Console prompt
            LoggingUtils.logPasswordResolution(request.getUser(), DIRECT_VAULT);
            return Optional.ofNullable(passwordPrompter.get());
        } catch (Exception e) {
            LoggingUtils.logStructuredError(PASSWORD_RESOLUTION, "resolve", FAILED,
                    "Failed to resolve password for user: " + request.getUser(), e);
            throw ExceptionUtils.wrap(e, "Failed to resolve password for user: " + request.getUser());
        }
    }

    private Optional<String> resolveWithDirectVaultParams(PasswordRequest request) {
        try (VaultClient client = new VaultClient()) {
            String password = client.fetchOraclePassword(
                    request.getVaultUrl(), request.getRoleId(), request.getSecretId(),
                    request.getDatabase(), request.getAit(), request.getUser());

            Optional<String> result = Optional.ofNullable(password)
                    .filter(pwd -> !pwd.trim().isEmpty());

            if (result.isPresent()) {
                LoggingUtils.logPasswordResolutionSuccess(request.getUser(), DIRECT_VAULT);
            } else {
                LoggingUtils.logStructuredError(PASSWORD_RESOLUTION, DIRECT_VAULT, FAILED,
                        "No password returned from direct vault parameters", null);
            }

            return result;
        } catch (IOException e) {
            LoggingUtils.logStructuredError(PASSWORD_RESOLUTION, DIRECT_VAULT, FAILED,
                    "Failed to fetch password from vault with direct params for user: " + request.getUser(), e);
            throw ExceptionUtils.wrap(e, "Failed to fetch password from vault");
        }
    }

    private Optional<String> resolveWithVaultLookup(PasswordRequest request) {
        try (VaultClient client = new VaultClient()) {
            String password = client.fetchOraclePassword(request.getUser(), request.getDatabase());
            Optional<String> result = Optional.ofNullable(password)
                    .filter(pwd -> !pwd.trim().isEmpty());

            if (result.isPresent()) {
                LoggingUtils.logPasswordResolutionSuccess(request.getUser(), DIRECT_VAULT);
            } else {
                LoggingUtils.logStructuredError(PASSWORD_RESOLUTION, DIRECT_VAULT, "NO_CONFIG",
                        "No vault config found for user: " + request.getUser(), null);
            }

            return result;
        } catch (IOException e) {
            LoggingUtils.logStructuredError(PASSWORD_RESOLUTION, DIRECT_VAULT, FAILED,
                    "Failed to fetch password from vault lookup for user: " + request.getUser(), e);
            // Don't throw here - fall back to console prompt
            return Optional.empty();
        }
    }
}