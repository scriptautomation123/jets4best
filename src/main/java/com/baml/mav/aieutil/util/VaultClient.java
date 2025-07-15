package com.baml.mav.aieutil.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class VaultClient {
    private static final String CLIENT_TOKEN_PATH = "/auth/client_token";
    private static final String PASSWORD_PATH = "/data/password";
    private static final ObjectMapper mapper = new ObjectMapper();

    private final HttpClient client;
    private final Logger logger = LoggingUtils.getLogger(VaultClient.class);

    public VaultClient() {
        this.client = createDefaultClient();
        logger.debug("VaultClient initialized");
    }

    private HttpClient createDefaultClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public String fetchOraclePassword(String user) {
        logger.info("Fetching Oracle password for user: {}", user);

        try {

            Map<String, Object> params = getVaultParamsForUser(user);
            if (params.isEmpty()) {
                logger.warn("No vault entry found for user: {}", user);
                return null;
            }

            String vaultBaseUrl = (String) params.get("base-url");
            String roleId = (String) params.get("role-id");
            String secretId = (String) params.get("secret-id");
            String ait = (String) params.get("ait");
            String dbName = (String) params.get("db");

            return fetchOraclePassword(vaultBaseUrl, roleId, secretId, dbName, ait, user);

        } catch (Exception e) {
            logger.error("Failed to fetch Oracle password for user: {}", user, e);
            return null;
        }
    }

    public String fetchOraclePassword(String vaultBaseUrl, String roleId, String secretId, String dbName, String ait,
            String username) {
        logger.info("Fetching Oracle password for database: {}, AIT: {}, username: {}", dbName, ait, username);

        try {
            String clientToken = authenticateToVault(vaultBaseUrl, roleId, secretId);
            String oraclePasswordResponse = fetchOraclePasswordSync(vaultBaseUrl, clientToken, dbName, ait, username);
            return parsePasswordFromResponse(oraclePasswordResponse);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExceptionUtils.ConfigurationException("Thread interrupted while fetching Oracle password", e);
        } catch (Exception e) {
            logger.error("Failed to fetch Oracle password", e);
            throw new ExceptionUtils.ConfigurationException("Failed to fetch Oracle password", e);
        }
    }

    public static Map<String, Object> getVaultParamsForUser(String user) {
        YamlConfig config = new YamlConfig(System.getProperty("vault.config", "vaults.yaml"));
        Object vaultsObj = config.getAll().get("vaults");
        if (vaultsObj instanceof java.util.List<?> vaultsList) {
            for (Object entry : vaultsList) {
                Map<String, Object> result = tryGetVaultEntry(entry, user);
                if (!result.isEmpty()) {
                    return result;
                }
            }
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> tryGetVaultEntry(Object entry, String user) {
        if (entry instanceof Map<?, ?> map) {
            Object entryId = map.get("id");
            if (entryId != null && entryId.toString().equals(user)) {
                boolean allStringKeys = map.keySet().stream().allMatch(String.class::isInstance);
                if (allStringKeys) {
                    return (Map<String, Object>) map;
                }
            }
        }
        return Collections.emptyMap();
    }

    private String authenticateToVault(String vaultBaseUrl, String roleId, String secretId)
            throws IOException, InterruptedException {
        String authUrl = vaultBaseUrl + "/v1/auth/approle/login";
        String authBody = String.format("{\"role_id\":\"%s\",\"secret_id\":\"%s\"}", roleId, secretId);

        logger.info("[DEBUG] Vault token URL: {}", authUrl);
        String response = httpPost(authUrl, authBody, Map.of("Content-Type", "application/json"));

        String clientToken = parseJsonField(response, CLIENT_TOKEN_PATH);
        if (clientToken == null || clientToken.isEmpty()) {
            throw new ExceptionUtils.ConfigurationException("No client token received from Vault");
        }
        return clientToken;
    }

    private String fetchOraclePasswordSync(String vaultBaseUrl, String clientToken, String dbName, String ait,
            String username) throws IOException, InterruptedException {
        String secretPath = String.format("%s/v1/secrets/database/oracle/static-creds/%s-%s-%s",
                vaultBaseUrl, ait, dbName, username).toLowerCase();

        logger.info("[DEBUG] Oracle password URL: {}", secretPath);
        return httpGet(secretPath, Map.of("x-vault-token", clientToken));
    }

    private String httpGet(String url, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30));

        headers.forEach(requestBuilder::header);

        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new ExceptionUtils.ConfigurationException(
                    "HTTP GET failed: " + response.statusCode() + " - " + response.body());
        }

        return response.body();
    }

    private String httpPost(String url, String body, Map<String, String> headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30));

        headers.forEach(requestBuilder::header);

        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new ExceptionUtils.ConfigurationException(
                    "HTTP POST failed: " + response.statusCode() + " - " + response.body());
        }

        return response.body();
    }

    private String parsePasswordFromResponse(String secretResponseBody) {
        try {
            String password = parseJsonField(secretResponseBody, PASSWORD_PATH);
            if (password == null || password.isEmpty()) {
                throw new ExceptionUtils.ConfigurationException("No password found in Vault secret");
            }
            return password;
        } catch (Exception e) {
            throw new ExceptionUtils.ConfigurationException("Failed to parse Vault response", e);
        }
    }

    private String parseJsonField(String json, String path) {
        try {
            String[] parts = path.split("/");
            var node = mapper.readTree(json);
            for (String part : parts) {
                if (part.isEmpty())
                    continue;
                node = node.get(part);
                if (node == null)
                    return null;
            }
            return node.asText();
        } catch (Exception e) {
            logger.error("Failed to parse JSON field: {}", path, e);
            return null;
        }
    }
}
