package com.baml.mav.aieutil.util;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class VaultClient implements AutoCloseable {
    private static final String CLIENT_TOKEN_PATH = "/auth/client_token";
    private static final String PASSWORD_PATH = "/data/password";
    private static final String VAULT_ERRORS_KEY = "errors";
    private static final String VAULT_CLIENT = "vault_client";
    private static final String FETCH_PASSWORD = "fetch_password";
    private static final String SUCCESS = "SUCCESS";
    private static final String STARTED = "STARTED";
    private static final String VAULT_OPERATION = "vault_operation";
    private static final String MISSING_PARAM = "MISSING_PARAM";
    private static final String FAILED = "FAILED";
    private static final String CLOSE = "close";
    private static final ObjectMapper mapper = new ObjectMapper();

    private final CloseableHttpClient client;

    public VaultClient() {
        // Configure connection pooling
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(20);
        connectionManager.setDefaultMaxPerRoute(10);

        // Configure timeouts for fast failure
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(2000)
                .setSocketTimeout(2000)
                .setConnectionRequestTimeout(2000)
                .build();

        this.client = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();

        LoggingUtils.logStructuredError(VAULT_CLIENT, "initialize", SUCCESS,
                "VaultClient initialized with connection pooling and 2s timeouts", null);
    }

    private String buildVaultBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new ExceptionUtils.ConfigurationException("Base URL cannot be null or empty");
        }

        if (baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            return baseUrl;
        }

        // For security, require full URLs to be provided
        throw new ExceptionUtils.ConfigurationException(
                "Vault URL must be a complete URL with protocol (e.g., https://vault.example.com). " +
                        "Received: " + baseUrl);
    }

    public String fetchOraclePassword(String user, String database) {
        LoggingUtils.logVaultOperation(FETCH_PASSWORD, user, STARTED);

        try {
            Map<String, Object> params = getVaultParamsForUser(user, database);
            if (params.isEmpty()) {
                LoggingUtils.logVaultOperation(FETCH_PASSWORD, user, "NO_CONFIG");
                return null;
            }

            // Extract and validate required parameters
            String vaultBaseUrl = (String) params.get("base-url");
            String roleId = (String) params.get("role-id");
            String secretId = (String) params.get("secret-id");
            String ait = (String) params.get("ait");

            // Validate all required parameters are present
            if (vaultBaseUrl == null || vaultBaseUrl.trim().isEmpty()) {
                LoggingUtils.logStructuredError(VAULT_OPERATION, FETCH_PASSWORD, MISSING_PARAM,
                        "Missing required vault parameter 'base-url' for user: " + user, null);
                return null;
            }
            if (roleId == null || roleId.trim().isEmpty()) {
                LoggingUtils.logStructuredError(VAULT_OPERATION, FETCH_PASSWORD, MISSING_PARAM,
                        "Missing required vault parameter 'role-id' for user: " + user, null);
                return null;
            }
            if (secretId == null || secretId.trim().isEmpty()) {
                LoggingUtils.logStructuredError(VAULT_OPERATION, FETCH_PASSWORD, MISSING_PARAM,
                        "Missing required vault parameter 'secret-id' for user: " + user, null);
                return null;
            }
            if (ait == null || ait.trim().isEmpty()) {
                LoggingUtils.logStructuredError(VAULT_OPERATION, FETCH_PASSWORD, MISSING_PARAM,
                        "Missing required vault parameter 'ait' for user: " + user, null);
                return null;
            }

            return fetchOraclePassword(vaultBaseUrl, roleId, secretId, database, ait, user);

        } catch (Exception e) {
            LoggingUtils.logStructuredError(VAULT_OPERATION, FETCH_PASSWORD, FAILED,
                    "Failed to fetch Oracle password for user: " + user, e);
            return null;
        }
    }

    public String fetchOraclePassword(String vaultBaseUrl, String roleId, String secretId, String dbName, String ait,
            String username) {
        LoggingUtils.logVaultOperation(FETCH_PASSWORD, username, STARTED);

        try {
            // Parse and format the base URL
            String fullVaultUrl = buildVaultBaseUrl(vaultBaseUrl);
            LoggingUtils.logVaultAuthentication(fullVaultUrl, SUCCESS);

            String clientToken = authenticateToVault(fullVaultUrl, roleId, secretId);
            String oraclePasswordResponse = fetchOraclePasswordSync(fullVaultUrl, clientToken, dbName, ait, username);
            return parsePasswordFromResponse(oraclePasswordResponse);
        } catch (Exception e) {
            LoggingUtils.logStructuredError(VAULT_OPERATION, FETCH_PASSWORD, FAILED,
                    "Failed to fetch Oracle password", e);
            throw new ExceptionUtils.ConfigurationException("Failed to fetch Oracle password", e);
        }
    }

    public static Map<String, Object> getVaultParamsForUser(String user, String database) {
        String vaultConfigPath = System.getProperty("vault.config");
        if (vaultConfigPath == null || vaultConfigPath.trim().isEmpty()) {
            throw new ExceptionUtils.ConfigurationException(
                    "vault.config system property must be specified. Use -Dvault.config=/path/to/vaults.yaml");
        }
        LoggingUtils.logConfigLoading(vaultConfigPath);

        YamlConfig config = new YamlConfig(vaultConfigPath);
        Object vaultsObj = config.getAll().get("vaults");
        if (vaultsObj instanceof List<?>) {
            List<?> vaultsList = (List<?>) vaultsObj;
            for (Object entry : vaultsList) {
                Map<String, Object> result = tryGetVaultEntry(entry, user, database);
                if (!result.isEmpty()) {
                    LoggingUtils.logStructuredError(VAULT_CLIENT, "get_vault_params", SUCCESS,
                            "return vault.json successful lookup: " + result, null);
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
                // Optimized check for string keys - avoid stream for simple iteration
                for (Object key : map.keySet()) {
                    if (!(key instanceof String)) {
                        return Collections.emptyMap();
                    }
                }
                return (Map<String, Object>) map;
            }
        }
        return Collections.emptyMap();
    }

    private String authenticateToVault(String vaultBaseUrl, String roleId, String secretId)
            throws IOException {
        String authUrl = vaultBaseUrl + "/v1/auth/approle/login";
        String authBody = String.format("{\"role_id\":\"%s\",\"secret_id\":\"%s\"}", roleId, secretId);

        LoggingUtils.logVaultAuthentication(authUrl, STARTED);
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        String response = httpPost(authUrl, authBody, headers);

        String clientToken = parseJsonField(response, CLIENT_TOKEN_PATH);
        if (clientToken == null || clientToken.isEmpty()) {
            LoggingUtils.logVaultAuthentication(authUrl, FAILED);
            throw new ExceptionUtils.ConfigurationException("No client token received from Vault");

        }
        LoggingUtils.logVaultAuthentication(authUrl, SUCCESS);
        return clientToken;
    }

    private String fetchOraclePasswordSync(String vaultBaseUrl, String clientToken, String dbName, String ait,
            String username) throws IOException {
        String secretPath = String.format("%s/v1/secrets/database/oracle/static-creds/%s-%s-%s",
                vaultBaseUrl, ait, dbName, username).toLowerCase();

        LoggingUtils.logVaultOperation(FETCH_PASSWORD, username, STARTED);
        Map<String, String> headers = new HashMap<>();
        headers.put("x-vault-token", clientToken);
        String response = httpGet(secretPath, headers);
        LoggingUtils.logVaultOperation(FETCH_PASSWORD, username, SUCCESS);
        return response;
    }

    private String httpGet(String url, Map<String, String> headers) throws IOException {
        HttpGet request = new HttpGet(url);

        // Add headers
        for (Map.Entry<String, String> header : headers.entrySet()) {
            request.addHeader(header.getKey(), header.getValue());
        }

        try (CloseableHttpResponse response = client.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode >= 400) {
                String responseBody = EntityUtils.toString(response.getEntity());
                String detailedError = parseVaultError(responseBody, statusCode);
                LoggingUtils.logStructuredError(VAULT_CLIENT, "http_get", FAILED,
                        "Vault HTTP GET failed: " + statusCode + " - " + detailedError, null);
                throw new ExceptionUtils.ConfigurationException(
                        "Vault HTTP GET failed: " + statusCode + " - " + detailedError);
            }

            return EntityUtils.toString(response.getEntity());
        }
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

    private String httpPost(String url, String body, Map<String, String> headers) throws IOException {
        HttpPost request = new HttpPost(url);

        // Add headers
        for (Map.Entry<String, String> header : headers.entrySet()) {
            request.addHeader(header.getKey(), header.getValue());
        }

        // Set request body
        request.setEntity(new StringEntity(body, "UTF-8"));

        try (CloseableHttpResponse response = client.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode >= 400) {
                String responseBody = EntityUtils.toString(response.getEntity());
                String detailedError = parseVaultError(responseBody, statusCode);
                LoggingUtils.logStructuredError(VAULT_CLIENT, "http_post", FAILED,
                        "Vault HTTP POST failed: " + statusCode + " - " + detailedError, null);
                throw new ExceptionUtils.ConfigurationException(
                        "Vault HTTP POST failed: " + statusCode + " - " + detailedError);
            }

            return EntityUtils.toString(response.getEntity());
        }
    }

    private String parsePasswordFromResponse(String secretResponseBody) {
        try {
            String password = parseJsonField(secretResponseBody, PASSWORD_PATH);
            if (password == null || password.isEmpty()) {
                throw new ExceptionUtils.ConfigurationException("No password found in Vault secret");
            }
            return password;
        } catch (Exception e) {
            LoggingUtils.logStructuredError(VAULT_CLIENT, "parse_password", FAILED,
                    "Failed to parse Vault response", e);
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
            LoggingUtils.logStructuredError(VAULT_CLIENT, "parse_json_field", FAILED,
                    "Failed to parse JSON field: " + path, e);
            return null;
        }
    }

    @Override
    public void close() throws IOException {
        if (client != null) {
            LoggingUtils.logStructuredError(VAULT_CLIENT, CLOSE, STARTED,
                    "Closing VaultClient HTTP client", null);
            try {
                client.close();
                LoggingUtils.logStructuredError(VAULT_CLIENT, CLOSE, SUCCESS,
                        "VaultClient HTTP client closed successfully", null);
            } catch (IOException e) {
                LoggingUtils.logStructuredError(VAULT_CLIENT, CLOSE, FAILED,
                        "Error closing HTTP client: " + e.getMessage(), e);
                throw e;
            }
        }
    }
}
