package com.baml.mav.aieutil.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.Logger;

public final class VaultClient {
    private static final String CLIENT_TOKEN_PATH = "/auth/client_token";
    private static final String PASSWORD_PATH = "/data/password";

    public record VaultConfig(String baseUrl, String roleId, String secretId) {
        public VaultConfig {
            Objects.requireNonNull(baseUrl, "Base URL cannot be null");
            Objects.requireNonNull(roleId, "Role ID cannot be null");
            Objects.requireNonNull(secretId, "Secret ID cannot be null");
        }
    }

    private final YamlConfig configManager;
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final Logger logger = LoggingUtils.getLogger(VaultClient.class);

    public VaultClient() {
        this.configManager = null;
        this.client = createDefaultClient();
        this.mapper = new ObjectMapper();
        logger.debug("VaultClient initialized");
    }

    public static VaultClient withConfig(YamlConfig configManager) {
        return new VaultClient(configManager);
    }

    private VaultClient(YamlConfig configManager) {
        this.configManager = Objects.requireNonNull(configManager, "Config manager cannot be null");
        this.client = createDefaultClient();
        this.mapper = new ObjectMapper();
        logger.debug("VaultClient initialized with config manager");
    }

    private HttpClient createDefaultClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    public String fetchOraclePassword(String vaultBaseUrl, String roleId, String secretId, String dbName, String ait, String username) {
        logger.info("Fetching Oracle password for database: {}, AIT: {}, username: {}", dbName, ait, username);
        
        try {
            String clientToken = authenticateToVault(vaultBaseUrl, roleId, secretId);
            String oraclePasswordResponse = fetchOraclePasswordSync(vaultBaseUrl, clientToken, dbName, ait, username);
            return parsePasswordFromResponse(oraclePasswordResponse);
        } catch (Exception e) {
            logger.error("Failed to fetch Oracle password", e);
            throw new RuntimeException("Failed to fetch Oracle password", e);
        }
    }

    public PasswordFetcher fetchPassword() {
        return new PasswordFetcher();
    }

    public final class PasswordFetcher {
        private String databaseName;
        private String username;

        public PasswordFetcher forDatabase(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        public PasswordFetcher forUser(String username) {
            this.username = username;
            return this;
        }

        public String execute() {
            Objects.requireNonNull(databaseName, "Database name cannot be null");
            Objects.requireNonNull(username, "Username cannot be null");

            logger.debug("Fetching password from Vault for database: {}, username: {}", databaseName, username);

            try {
                String clientToken = authenticateToVault(
                    configManager.getRawValue("vault.base-url"),
                    configManager.getRawValue("vault.role-id"),
                    configManager.getRawValue("vault.secret-id"));
                
                String secretPath = String.format(
                    "%s/v1/secrets/database/oracle/static-creds/%s-%s-%s",
                    configManager.getRawValue("vault.base-url"), 
                    configManager.getRawValue("vault.ait"), 
                    databaseName, 
                    username).toLowerCase();
                
                String response = httpGet(secretPath, Map.of("x-vault-token", clientToken));
                
                String password = parseJsonField(response, PASSWORD_PATH);
                if (password == null || password.isEmpty()) {
                    throw new RuntimeException("No password found in Vault response");
                }
                
                logger.debug("Successfully retrieved password from Vault for user: {}", username);
                return password;
            } catch (Exception e) {
                logger.error("Failed to fetch password from Vault", e);
                throw new RuntimeException("Failed to fetch password from Vault", e);
            }
        }
    }

    private String authenticateToVault(String vaultBaseUrl, String roleId, String secretId) throws IOException, InterruptedException {
        String authUrl = vaultBaseUrl + "/v1/auth/approle/login";
        String authBody = String.format("{\"role_id\":\"%s\",\"secret_id\":\"%s\"}", roleId, secretId);
        
        String response = httpPost(authUrl, authBody, Map.of("Content-Type", "application/json"));
        
        String clientToken = parseJsonField(response, CLIENT_TOKEN_PATH);
        if (clientToken == null || clientToken.isEmpty()) {
            throw new RuntimeException("No client token received from Vault");
        }
        return clientToken;
    }

    private String fetchOraclePasswordSync(String vaultBaseUrl, String clientToken, String dbName, String ait, String username) throws IOException, InterruptedException {
        String secretPath = String.format("%s/v1/secrets/database/oracle/static-creds/%s-%s-%s", 
            vaultBaseUrl, ait, dbName, username).toLowerCase();
        
        return httpGet(secretPath, Map.of("x-vault-token", clientToken));
    }

    private String parsePasswordFromResponse(String secretResponseBody) {
        try {
            String password = parseJsonField(secretResponseBody, PASSWORD_PATH);
            if (password == null || password.isEmpty()) {
                throw new RuntimeException("No password found in Vault secret");
            }
            return password;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Vault response", e);
        }
    }

    private String httpGet(String url, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30));
        
        headers.forEach(requestBuilder::header);
        
        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP GET failed: " + response.statusCode() + " - " + response.body());
        }
        
        return response.body();
    }

    private String httpPost(String url, String body, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(30));
        
        headers.forEach(requestBuilder::header);
        
        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP POST failed: " + response.statusCode() + " - " + response.body());
        }
        
        return response.body();
    }

    private String parseJsonField(String json, String path) {
        try {
            String[] parts = path.split("/");
            var node = mapper.readTree(json);
            for (String part : parts) {
                if (part.isEmpty()) continue;
                node = node.get(part);
                if (node == null) return null;
            }
            return node.asText();
        } catch (Exception e) {
            logger.error("Failed to parse JSON field: {}", path, e);
            return null;
        }
    }

    public boolean isLdapConnection(String connectionType) {
        return "ldap".equalsIgnoreCase(connectionType);
    }

    public boolean isJdbcConnection(String connectionType) {
        return "jdbc".equalsIgnoreCase(connectionType);
    }
} 
