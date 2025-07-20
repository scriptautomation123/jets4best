package com.baml.mav.aieutil.util;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

/**
 * HTTP client for interacting with HashiCorp Vault to retrieve Oracle database
 * passwords.
 * Provides authentication, secret retrieval, and structured error handling
 * capabilities.
 * Implements AutoCloseable for proper resource management.
 */
public final class VaultClient implements AutoCloseable {

  /** JSON path for client token in authentication response */
  private static final String CLIENT_TOKEN_PATH = "/auth/client_token";

  /** JSON path for password in secret response */
  private static final String PASSWORD_PATH = "/data/password";

  /** Key for errors in Vault response */
  private static final String VAULT_ERRORS_KEY = "errors";

  /** Client identifier for logging */
  private static final String VAULT_CLIENT = "vault_client";

  /** Operation identifier for password fetching */
  private static final String FETCH_PASSWORD = "fetch_password";

  /** Success status for operations */
  private static final String SUCCESS = "SUCCESS";

  /** Started status for operations */
  private static final String STARTED = "STARTED";

  /** Vault operation identifier */
  private static final String VAULT_OPERATION = "vault_operation";

  /** Missing parameter error type */
  private static final String MISSING_PARAM = "MISSING_PARAM";

  /** Failed status for operations */
  private static final String FAILED = "FAILED";

  /** Close operation identifier */
  private static final String CLOSE_OPERATION = "close";

  /** HTTP status code for client errors */
  private static final int HTTP_CLIENT_ERROR = 400;

  /** Separator for error messages */
  private static final String ERROR_SEPARATOR = " - ";

  /** Request ID key in Vault response */
  private static final String REQUEST_ID_KEY = "request_id";

  /** Content type for JSON requests */
  private static final String CONTENT_TYPE_JSON = "application/json";

  /** Vault token header name */
  private static final String VAULT_TOKEN_HEADER = "x-vault-token";

  /** UTF-8 encoding */
  private static final String UTF_8_ENCODING = "UTF-8";

  /** Maximum total connections for connection pool */
  private static final int MAX_TOTAL_CONNECTIONS = 20;

  /** Default maximum connections per route */
  private static final int DEFAULT_MAX_PER_ROUTE = 10;

  /** Connection timeout in milliseconds */
  private static final int CONNECTION_TIMEOUT_MS = 2000;

  /** Socket timeout in milliseconds */
  private static final int SOCKET_TIMEOUT_MS = 2000;

  /** Connection request timeout in milliseconds */
  private static final int CONNECTION_REQUEST_TIMEOUT_MS = 2000;

  /** JSON object mapper for parsing responses */
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /** HTTP client for making requests */
  private final CloseableHttpClient client;

  /**
   * Constructs a new VaultClient with connection pooling and timeout
   * configuration.
   * Initializes HTTP client with optimized settings for Vault API interactions.
   */
  public VaultClient() {
    final PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
    connectionManager.setMaxTotal(MAX_TOTAL_CONNECTIONS);
    connectionManager.setDefaultMaxPerRoute(DEFAULT_MAX_PER_ROUTE);

    final RequestConfig requestConfig = RequestConfig.custom()
        .setConnectTimeout(CONNECTION_TIMEOUT_MS)
        .setSocketTimeout(SOCKET_TIMEOUT_MS)
        .setConnectionRequestTimeout(CONNECTION_REQUEST_TIMEOUT_MS)
        .build();

    this.client = HttpClients.custom()
        .setConnectionManager(connectionManager)
        .setDefaultRequestConfig(requestConfig)
        .build();

    LoggingUtils.logStructuredError(
        VAULT_CLIENT,
        "initialize",
        SUCCESS,
        "VaultClient initialized with connection pooling and 2s timeouts",
        null);
  }

  /**
   * Builds and validates the Vault base URL.
   * 
   * @param baseUrl the base URL to validate and format
   * @return the validated base URL
   * @throws ExceptionUtils.ConfigurationException if URL is invalid
   */
  private String buildVaultBaseUrl(final String baseUrl) {
    if (baseUrl == null || baseUrl.isEmpty()) {
      throw new ExceptionUtils.ConfigurationException("Base URL cannot be null or empty");
    }

    if (baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
      return baseUrl;
    }

    throw new ExceptionUtils.ConfigurationException(
        "Vault URL must be a complete URL with protocol (e.g., https://vault.example.com). "
            + "Received: "
            + baseUrl);
  }

  /**
   * Fetches Oracle password for the specified user and database.
   * 
   * @param user     the username
   * @param database the database name
   * @return the password or null if not found
   */
  public String fetchOraclePassword(final String user, final String database) {
    LoggingUtils.logVaultOperation(FETCH_PASSWORD, user, STARTED);

    try {
      final Map<String, Object> params = getVaultParamsForUser(user, database);
      if (params.isEmpty()) {
        LoggingUtils.logVaultOperation(FETCH_PASSWORD, user, "NO_CONFIG");
        return null;
      }

      final String vaultBaseUrl = (String) params.get("base-url");
      final String roleId = (String) params.get("role-id");
      final String secretId = (String) params.get("secret-id");
      final String ait = (String) params.get("ait");

      if (!isValidParameter(vaultBaseUrl, "base-url", user)) {
        return null;
      }
      if (!isValidParameter(roleId, "role-id", user)) {
        return null;
      }
      if (!isValidParameter(secretId, "secret-id", user)) {
        return null;
      }
      if (!isValidParameter(ait, "ait", user)) {
        return null;
      }

      return fetchOraclePassword(vaultBaseUrl, roleId, secretId, database, ait, user);

    } catch (Exception exception) {
      LoggingUtils.logStructuredError(
          VAULT_OPERATION,
          FETCH_PASSWORD,
          FAILED,
          "Failed to fetch Oracle password for user: " + user,
          exception);
      return null;
    }
  }

  /**
   * Validates if a parameter is not null and not empty.
   * 
   * @param paramName  the parameter name for logging
   * @param paramValue the parameter value to validate
   * @param user       the username for logging
   * @return true if parameter is valid, false otherwise
   */
  private boolean isValidParameter(final String paramValue, final String paramName, final String user) {
    if (paramValue == null || paramValue.trim().isEmpty()) {
      LoggingUtils.logStructuredError(
          VAULT_OPERATION,
          FETCH_PASSWORD,
          MISSING_PARAM,
          "Missing required vault parameter '" + paramName + "' for user: " + user,
          null);
      return false;
    }
    return true;
  }

  /**
   * Fetches Oracle password using direct Vault parameters.
   * 
   * @param vaultBaseUrl the Vault base URL
   * @param roleId       the role ID for authentication
   * @param secretId     the secret ID for authentication
   * @param dbName       the database name
   * @param ait          the AIT identifier
   * @param username     the username
   * @return the password
   * @throws ExceptionUtils.ConfigurationException if password fetch fails
   */
  public String fetchOraclePassword(
      final String vaultBaseUrl,
      final String roleId,
      final String secretId,
      final String dbName,
      final String ait,
      final String username) {
    LoggingUtils.logVaultOperation(FETCH_PASSWORD, username, STARTED);

    try {
      final String fullVaultUrl = buildVaultBaseUrl(vaultBaseUrl);
      LoggingUtils.logVaultAuthentication(fullVaultUrl, SUCCESS);

      final String clientToken = authenticateToVault(fullVaultUrl, roleId, secretId);
      final String passwordResponse = fetchOraclePasswordSync(fullVaultUrl, clientToken, dbName, ait, username);
      return parsePasswordFromResponse(passwordResponse);
    } catch (Exception exception) {
      LoggingUtils.logStructuredError(
          VAULT_OPERATION, FETCH_PASSWORD, FAILED, "Failed to fetch Oracle password", exception);
      throw new ExceptionUtils.ConfigurationException("Failed to fetch Oracle password", exception);
    }
  }

  /**
   * Retrieves Vault parameters for a specific user and database.
   * 
   * @param user     the username
   * @param database the database name
   * @return map of Vault parameters or empty map if not found
   */
  public static Map<String, Object> getVaultParamsForUser(final String user, final String database) {
    final String vaultConfigPath = System.getProperty("vault.config");
    if (vaultConfigPath == null || vaultConfigPath.trim().isEmpty()) {
      throw new ExceptionUtils.ConfigurationException(
          "vault.config system property must be specified. Use -Dvault.config=/path/to/vaults.yaml");
    }
    LoggingUtils.logConfigLoading(vaultConfigPath);

    final YamlConfig config = new YamlConfig(vaultConfigPath);
    final Object vaultsObj = config.getAll().get("vaults");
    if (vaultsObj instanceof List<?>) {
      final List<?> vaultsList = (List<?>) vaultsObj;
      for (final Object entry : vaultsList) {
        final Map<String, Object> result = tryGetVaultEntry(entry, user, database);
        if (!result.isEmpty()) {
          LoggingUtils.logStructuredError(
              VAULT_CLIENT,
              "get_vault_params",
              SUCCESS,
              "return vault.json successful lookup: " + result,
              null);
          return result;
        }
      }
    }
    return Collections.emptyMap();
  }

  /**
   * Attempts to find a matching Vault entry for the given user and database.
   * 
   * @param entry    the Vault entry to check
   * @param user     the username
   * @param database the database name
   * @return matching entry map or empty map if no match
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> tryGetVaultEntry(final Object entry, final String user, final String database) {
    if (entry instanceof Map<?, ?>) {
      final Map<?, ?> map = (Map<?, ?>) entry;
      final Object entryId = map.get("id");
      final Object entryDb = map.get("db");

      if (isMatchingEntry(entryId, entryDb, user, database) && hasStringKeys(map)) {
        return (Map<String, Object>) map;
      }
    }
    return Collections.emptyMap();
  }

  /**
   * Checks if the entry matches the user and database.
   * 
   * @param entryId  the entry ID
   * @param entryDb  the entry database
   * @param user     the username
   * @param database the database name
   * @return true if entry matches
   */
  private static boolean isMatchingEntry(final Object entryId, final Object entryDb, final String user,
      final String database) {
    return entryId != null
        && entryId.toString().equals(user)
        && entryDb != null
        && entryDb.toString().equals(database);
  }

  /**
   * Checks if all keys in the map are strings.
   * 
   * @param map the map to check
   * @return true if all keys are strings
   */
  private static boolean hasStringKeys(final Map<?, ?> map) {
    for (final Object key : map.keySet()) {
      if (!(key instanceof String)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Authenticates to Vault using role ID and secret ID.
   * 
   * @param vaultBaseUrl the Vault base URL
   * @param roleId       the role ID
   * @param secretId     the secret ID
   * @return the client token
   * @throws IOException if authentication fails
   */
  private String authenticateToVault(final String vaultBaseUrl, final String roleId, final String secretId)
      throws IOException {
    final String authUrl = vaultBaseUrl + "/v1/auth/approle/login";
    final String authBody = String.format("{\"role_id\":\"%s\",\"secret_id\":\"%s\"}", roleId, secretId);

    LoggingUtils.logVaultAuthentication(authUrl, STARTED);
    final Map<String, String> headers = new ConcurrentHashMap<>();
    headers.put("Content-Type", CONTENT_TYPE_JSON);
    final String response = httpPost(authUrl, authBody, headers);

    final String clientToken = parseJsonField(response, CLIENT_TOKEN_PATH);
    if (clientToken == null || clientToken.isEmpty()) {
      LoggingUtils.logVaultAuthentication(authUrl, FAILED);
      throw new ExceptionUtils.ConfigurationException("No client token received from Vault");
    }
    LoggingUtils.logVaultAuthentication(authUrl, SUCCESS);
    return clientToken;
  }

  /**
   * Fetches Oracle password synchronously from Vault.
   * 
   * @param vaultBaseUrl the Vault base URL
   * @param clientToken  the client token
   * @param dbName       the database name
   * @param ait          the AIT identifier
   * @param username     the username
   * @return the password response
   * @throws IOException if the request fails
   */
  private String fetchOraclePasswordSync(
      final String vaultBaseUrl, final String clientToken, final String dbName, final String ait, final String username)
      throws IOException {
    final String secretPath = String.format(
        "%s/v1/secrets/database/oracle/static-creds/%s-%s-%s",
        vaultBaseUrl, ait, dbName, username)
        .toLowerCase(Locale.ROOT);

    LoggingUtils.logVaultOperation(FETCH_PASSWORD, username, STARTED);
    final Map<String, String> headers = new ConcurrentHashMap<>();
    headers.put(VAULT_TOKEN_HEADER, clientToken);
    final String response = httpGet(secretPath, headers);
    LoggingUtils.logVaultOperation(FETCH_PASSWORD, username, SUCCESS);
    return response;
  }

  /**
   * Performs HTTP GET request to the specified URL.
   * 
   * @param url     the URL to request
   * @param headers the headers to include
   * @return the response body
   * @throws IOException if the request fails
   */
  private String httpGet(final String url, final Map<String, String> headers) throws IOException {
    final HttpGet request = new HttpGet(url);

    for (final Map.Entry<String, String> header : headers.entrySet()) {
      request.addHeader(header.getKey(), header.getValue());
    }

    try (CloseableHttpResponse response = client.execute(request)) {
      final int statusCode = response.getStatusLine().getStatusCode();

      if (statusCode >= HTTP_CLIENT_ERROR) {
        final String responseBody = EntityUtils.toString(response.getEntity());
        final String detailedError = parseVaultError(responseBody, statusCode);
        final String errorMessage = "Vault HTTP GET failed: " + statusCode + ERROR_SEPARATOR + detailedError;

        LoggingUtils.logStructuredError(
            VAULT_CLIENT,
            "http_get",
            FAILED,
            errorMessage,
            null);
        throw new ExceptionUtils.ConfigurationException(errorMessage);
      }

      return EntityUtils.toString(response.getEntity());
    }
  }

  /**
   * Parses Vault error response into a readable format.
   * 
   * @param responseBody the response body
   * @param statusCode   the HTTP status code
   * @return formatted error message
   */
  private String parseVaultError(final String responseBody, final int statusCode) {
    try {
      final JsonNode errorNode = OBJECT_MAPPER.readTree(responseBody);

      if (errorNode.has(VAULT_ERRORS_KEY) && errorNode.get(VAULT_ERRORS_KEY).isArray()) {
        final StringBuilder errorMsg = new StringBuilder();
        errorMsg.append("Status: ").append(statusCode).append(" | ");

        final JsonNode errors = errorNode.get(VAULT_ERRORS_KEY);
        for (final JsonNode error : errors) {
          errorMsg.append(error.asText()).append("; ");
        }

        if (errorNode.has(REQUEST_ID_KEY)) {
          errorMsg.append("Request ID: ").append(errorNode.get(REQUEST_ID_KEY).asText());
        }

        return errorMsg.toString();
      }

      return responseBody;
    } catch (Exception exception) {
      return responseBody;
    }
  }

  /**
   * Performs HTTP POST request to the specified URL.
   * 
   * @param url     the URL to request
   * @param body    the request body
   * @param headers the headers to include
   * @return the response body
   * @throws IOException if the request fails
   */
  private String httpPost(final String url, final String body, final Map<String, String> headers) throws IOException {
    final HttpPost request = new HttpPost(url);

    for (final Map.Entry<String, String> header : headers.entrySet()) {
      request.addHeader(header.getKey(), header.getValue());
    }

    request.setEntity(new StringEntity(body, UTF_8_ENCODING));

    try (CloseableHttpResponse response = client.execute(request)) {
      final int statusCode = response.getStatusLine().getStatusCode();

      if (statusCode >= HTTP_CLIENT_ERROR) {
        final String responseBody = EntityUtils.toString(response.getEntity());
        final String detailedError = parseVaultError(responseBody, statusCode);
        final String errorMessage = "Vault HTTP POST failed: " + statusCode + ERROR_SEPARATOR + detailedError;

        LoggingUtils.logStructuredError(
            VAULT_CLIENT,
            "http_post",
            FAILED,
            errorMessage,
            null);
        throw new ExceptionUtils.ConfigurationException(errorMessage);
      }

      return EntityUtils.toString(response.getEntity());
    }
  }

  /**
   * Parses password from Vault secret response.
   * 
   * @param secretResponseBody the secret response body
   * @return the password
   * @throws ExceptionUtils.ConfigurationException if parsing fails
   */
  private String parsePasswordFromResponse(final String secretResponseBody) {
    try {
      final String password = parseJsonField(secretResponseBody, PASSWORD_PATH);
      if (password == null || password.isEmpty()) {
        throw new ExceptionUtils.ConfigurationException("No password found in Vault secret");
      }
      return password;
    } catch (Exception exception) {
      LoggingUtils.logStructuredError(
          VAULT_CLIENT, "parse_password", FAILED, "Failed to parse Vault response", exception);
      throw new ExceptionUtils.ConfigurationException("Failed to parse Vault response", exception);
    }
  }

  /**
   * Parses a JSON field from the given path.
   * 
   * @param json the JSON string
   * @param path the path to the field
   * @return the field value or null if not found
   */
  private String parseJsonField(final String json, final String path) {
    try {
      final String[] parts = path.split("/");
      JsonNode node = OBJECT_MAPPER.readTree(json);

      for (final String part : parts) {
        if (part.isEmpty()) {
          continue;
        }
        node = node.get(part);
        if (node == null) {
          return null;
        }
      }
      return node.asText();
    } catch (Exception exception) {
      LoggingUtils.logStructuredError(
          VAULT_CLIENT, "parse_json_field", FAILED, "Failed to parse JSON field: " + path, exception);
      return null;
    }
  }

  @Override
  public void close() throws IOException {
    if (client != null) {
      LoggingUtils.logStructuredError(
          VAULT_CLIENT, CLOSE_OPERATION, STARTED, "Closing VaultClient HTTP client", null);
      try {
        client.close();
        LoggingUtils.logStructuredError(
            VAULT_CLIENT, CLOSE_OPERATION, SUCCESS, "VaultClient HTTP client closed successfully", null);
      } catch (IOException exception) {
        LoggingUtils.logStructuredError(
            VAULT_CLIENT, CLOSE_OPERATION, FAILED, "Error closing HTTP client: " + exception.getMessage(), exception);
        throw exception;
      }
    }
  }
}
