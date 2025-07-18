package com.baml.mav.aieutil.util;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class VaultClient {
    private static final String CLIENT_TOKEN_PATH = "/auth/client_token";
    private static final String PASSWORD_PATH = "/data/password";
    private static final String VAULT_ERRORS_KEY = "errors";
    private static final ObjectMapper mapper = new ObjectMapper();

    private final HttpClient client;
    private final Logger logger = LoggingUtils.getLogger(VaultClient.class);
    private static final Logger staticLogger = LoggingUtils.getLogger(VaultClient.class);

    public VaultClient() {
        // Configure timeouts for fast failure
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(2000) // 5 seconds connection timeout
                .setSocketTimeout(2000) // 5 seconds socket timeout
                .setConnectionRequestTimeout(2000) // 5 seconds connection request timeout
                .build();

        this.client = HttpClients.custom().setDefaultRequestConfig(requestConfig).build();
        logger.debug("VaultClient initialized with 5s timeouts");
    }

    private String buildVaultBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new ExceptionUtils.ConfigurationException("Base URL cannot be null or empty");
        }

        // If it already contains protocol, return as-is
        if (baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            return baseUrl;
        }

        // Build the full URL with bankofamerica.com domain
        return String.format("https://%s.bankofamerica.com", baseUrl);
    }

    public String fetchOraclePassword(String user, String database) {
        logger.info("Fetching Oracle password for user: {} and database: {}", user, database);

        try {
            Map<String, Object> params = getVaultParamsForUser(user, database);
            if (params.isEmpty()) {
                logger.warn("No vault entry found for user: {}", user);
                return null;
            }

            // Extract and validate required parameters
            String vaultBaseUrl = (String) params.get("base-url");
            String roleId = (String) params.get("role-id");
            String secretId = (String) params.get("secret-id");
            String ait = (String) params.get("ait");

            // Validate all required parameters are present
            if (vaultBaseUrl == null || vaultBaseUrl.trim().isEmpty()) {
                logger.error("Missing required vault parameter 'base-url' for user: {}", user);
                return null;
            }
            if (roleId == null || roleId.trim().isEmpty()) {
                logger.error("Missing required vault parameter 'role-id' for user: {}", user);
                return null;
            }
            if (secretId == null || secretId.trim().isEmpty()) {
                logger.error("Missing required vault parameter 'secret-id' for user: {}", user);
                return null;
            }
            if (ait == null || ait.trim().isEmpty()) {
                logger.error("Missing required vault parameter 'ait' for user: {}", user);
                return null;
            }

            return fetchOraclePassword(vaultBaseUrl, roleId, secretId, database, ait, user);

        } catch (Exception e) {
            logger.error("Failed to fetch Oracle password for user: {}", user, e);
            return null;
        }
    }

    public String fetchOraclePassword(String vaultBaseUrl, String roleId, String secretId, String dbName, String ait,
            String username) {
        logger.info("Fetching Oracle password for database: {}, AIT: {}, username: {}", dbName, ait, username);

        try {
            // Parse and format the base URL
            String fullVaultUrl = buildVaultBaseUrl(vaultBaseUrl);
            logger.info("Using Vault URL: {}", fullVaultUrl);

            String clientToken = authenticateToVault(fullVaultUrl, roleId, secretId);
            String oraclePasswordResponse = fetchOraclePasswordSync(fullVaultUrl, clientToken, dbName, ait, username);
            return parsePasswordFromResponse(oraclePasswordResponse);
        } catch (Exception e) {
            logger.error("Failed to fetch Oracle password", e);
            throw new ExceptionUtils.ConfigurationException("Failed to fetch Oracle password", e);
        }
    }

    public static Map<String, Object> getVaultParamsForUser(String user, String database) {
        String vaultConfigPath = System.getProperty("vault.config");
        if (vaultConfigPath == null || vaultConfigPath.trim().isEmpty()) {
            throw new ExceptionUtils.ConfigurationException(
                    "vault.config system property must be specified. Use -Dvault.config=/path/to/vaults.yaml");
        }
        staticLogger.info("Loading vault config from: {} (caller: {})", vaultConfigPath, LoggingUtils.getCallerInfo());

        YamlConfig config = new YamlConfig(vaultConfigPath);
        Object vaultsObj = config.getAll().get("vaults");
        if (vaultsObj instanceof List<?>) {
            List<?> vaultsList = (List<?>) vaultsObj;
            for (Object entry : vaultsList) {
                Map<String, Object> result = tryGetVaultEntry(entry, user, database);
                if (!result.isEmpty()) {
                    staticLogger.info("return vault.json successful lookup: {}", result);
                    return result;
                }
            }
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> tryGetVaultEntry(Object entry, String user, String database) {
        if (entry instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) entry;
            Object entryId = map.get("id");
            Object entryDb = map.get("db");
            if (entryId != null && entryId.toString().equals(user) &&
                    entryDb != null && entryDb.toString().equals(database)) {
                boolean allStringKeys = map.keySet().stream().allMatch(String.class::isInstance);
                if (allStringKeys) {
                    return (Map<String, Object>) map;
                }
            }
        }
        return Collections.emptyMap();
    }

    private String authenticateToVault(String vaultBaseUrl, String roleId, String secretId)
            throws IOException {
        String authUrl = vaultBaseUrl + "/v1/auth/approle/login";
        String authBody = String.format("{\"role_id\":\"%s\",\"secret_id\":\"%s\"}", roleId, secretId);

        logger.info("[DEBUG] Vault token URL: {} (caller: {}) ", authUrl, LoggingUtils.getCallerInfo());
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        String response = httpPost(authUrl, authBody, headers);

        String clientToken = parseJsonField(response, CLIENT_TOKEN_PATH);
        if (clientToken == null || clientToken.isEmpty()) {
            staticLogger.info("[DEBUG] No client token received from Vault (caller: {}) ",
                    LoggingUtils.getCallerInfo());
            throw new ExceptionUtils.ConfigurationException("No client token received from Vault");

        }
        staticLogger.info("[DEBUG] client token {}  (caller: {}) ", clientToken, LoggingUtils.getCallerInfo());
        return clientToken;
    }

    private String fetchOraclePasswordSync(String vaultBaseUrl, String clientToken, String dbName, String ait,
            String username) throws IOException {
        String secretPath = String.format("%s/v1/secrets/database/oracle/static-creds/%s-%s-%s",
                vaultBaseUrl, ait, dbName, username).toLowerCase();

        logger.info("[DEBUG] Oracle password URL: {}", secretPath);
        Map<String, String> headers = new HashMap<>();
        headers.put("x-vault-token", clientToken);
        return httpGet(secretPath, headers);
    }

    private String httpGet(String url, Map<String, String> headers) throws IOException {
        HttpGet request = new HttpGet(url);

        // Add headers
        for (Map.Entry<String, String> header : headers.entrySet()) {
            request.addHeader(header.getKey(), header.getValue());
        }

        HttpResponse response = client.execute(request);
        int statusCode = response.getStatusLine().getStatusCode();

        if (statusCode >= 400) {
            String responseBody = EntityUtils.toString(response.getEntity());
            String detailedError = parseVaultError(responseBody, statusCode);
            staticLogger.error("Vault HTTP GET failed: {} - {}", statusCode, detailedError);
            throw new ExceptionUtils.ConfigurationException(
                    "Vault HTTP GET failed: " + statusCode + " - " + detailedError);
        }

        return EntityUtils.toString(response.getEntity());
    }

    private String parseVaultError(String responseBody, int statusCode) {
        try {
            JsonNode errorNode = mapper.readTree(responseBody);

            if (errorNode.has(VAULT_ERRORS_KEY) && errorNode.get(VAULT_ERRORS_KEY).isArray()) {
                StringBuilder errorMsg = new StringBuilder();
                errorMsg.append("Status: ").append(statusCode).append(" | ");

                JsonNode errors = errorNode.get(VAULT_ERRORS_KEY);
                for (JsonNode error : errors) {
                    errorMsg.append(error.asText()).append("; ");
                }

                // Add request ID if available
                if (errorNode.has("request_id")) {
                    errorMsg.append("Request ID: ").append(errorNode.get("request_id").asText());
                }

                return errorMsg.toString();
            }

            // Fallback to raw response if not structured
            return responseBody;
        } catch (Exception e) {
            // If JSON parsing fails, return raw response
            return responseBody;
        }
    }

    private String httpPost(String url, String body, Map<String, String> headers)
            throws IOException {
        HttpPost request = new HttpPost(url);

        // Add headers
        for (Map.Entry<String, String> header : headers.entrySet()) {
            request.addHeader(header.getKey(), header.getValue());
        }

        // Set request body
        request.setEntity(new StringEntity(body, "UTF-8"));

        HttpResponse response = client.execute(request);
        int statusCode = response.getStatusLine().getStatusCode();

        if (statusCode >= 400) {
            String responseBody = EntityUtils.toString(response.getEntity());
            String detailedError = parseVaultError(responseBody, statusCode);
            staticLogger.error("Vault HTTP POST failed: {} - {}", statusCode, detailedError);
            throw new ExceptionUtils.ConfigurationException(
                    "Vault HTTP POST failed: " + statusCode + " - " + detailedError);
        }

        return EntityUtils.toString(response.getEntity());
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
            JsonNode node = mapper.readTree(json);
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
