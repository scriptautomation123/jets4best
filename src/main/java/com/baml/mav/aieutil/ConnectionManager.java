package com.baml.mav.aieutil;

import java.util.Objects;

import com.baml.mav.aieutil.util.ExceptionUtils;
import com.baml.mav.aieutil.util.YamlConfig;

public class ConnectionManager {
    private static final YamlConfig appConfig = new YamlConfig("application.yaml");

    public record ConnInfo(String url, String user, String password) {
        public ConnInfo {
            Objects.requireNonNull(url, "URL cannot be null");
            Objects.requireNonNull(user, "User cannot be null");
            Objects.requireNonNull(password, "Password cannot be null");
        }
    }

    private sealed interface ConnectionStrategy {
        String buildUrl();

        record H2Jdbc(String database) implements ConnectionStrategy {
            @Override
            public String buildUrl() {
                String template = appConfig.getRawValue("databases.h2.connection-string.jdbc-thin.template");
                return String.format(template, database);
            }
        }

        record H2Memory(String database) implements ConnectionStrategy {
            @Override
            public String buildUrl() {
                String template = appConfig.getRawValue("databases.h2.connection-string.jdbc-thin.template");
                return String.format(template, "mem:" + database);
            }
        }

        record OracleJdbc(String host, String database) implements ConnectionStrategy {
            @Override
            public String buildUrl() {
                try {
                    String template = appConfig.getRawValue("databases.oracle.connection-string.jdbc-thin.template");
                    int port = Integer
                            .parseInt(appConfig.getRawValue("databases.oracle.connection-string.jdbc-thin.port"));
                    return String.format(template, host, port, database);
                } catch (Exception e) {
                    throw ExceptionUtils.wrap(e,
                            "Failed to build Oracle JDBC URL for host=" + host + ", database=" + database);
                }
            }
        }

        record OracleLdap(String database) implements ConnectionStrategy {
            @Override
            public String buildUrl() {
                try {
                    String template = appConfig.getRawValue("databases.oracle.connection-string.ldap.template");
                    int port = Integer.parseInt(appConfig.getRawValue("databases.oracle.connection-string.ldap.port"));
                    String context = appConfig.getRawValue("databases.oracle.connection-string.ldap.context");
                    String servers = String.join(",",
                            appConfig.getRawValue("databases.oracle.connection-string.ldap.servers").split(","));
                    return String.format(template, servers, port, database, context);
                } catch (Exception e) {
                    throw ExceptionUtils.wrap(e, "Failed to build Oracle LDAP URL for database=" + database);
                }
            }
        }
    }

    public static ConnInfo createConnection(String type, String database, String user, String password, String host) {
        ConnectionStrategy strategy = switch (type) {
            case "h2" -> host != null && !host.isBlank()
                    ? new ConnectionStrategy.H2Jdbc(database)
                    : new ConnectionStrategy.H2Memory(database);
            default -> host != null && !host.isBlank()
                    ? new ConnectionStrategy.OracleJdbc(host, database)
                    : new ConnectionStrategy.OracleLdap(database);
        };

        return new ConnInfo(strategy.buildUrl(), user, password);
    }

    public static ConnInfo createSampleConnection() {
        return new ConnInfo(
                "jdbc:oracle:thin:@ldap://ldap.example.com:389/ORCL,cn=OracleContext,dc=example,dc=com",
                "testuser",
                "testpass");
    }
}