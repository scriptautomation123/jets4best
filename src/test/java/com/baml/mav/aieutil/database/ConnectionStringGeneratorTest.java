// NOTE: This test is written for Java 8 compatibility. If you require tests for a different Java version, please specify.
package com.baml.mav.aieutil.database;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ConnectionStringGeneratorTest {

    @BeforeAll
    static void setupConfig() {
        String configPath = System.getProperty("user.dir") + "/src/test/resources/application.yaml";
        System.out.println("[DEBUG] Using config path: " + configPath);
        System.out.println("[DEBUG] Config file exists: " + new java.io.File(configPath).exists());
        ConnectionStringGenerator.setConfigPath(configPath);
    }

    @Test
    void createConnectionString_h2Memory() {
        ConnectionStringGenerator.ConnInfo conn = ConnectionStringGenerator.createConnectionString("h2", "testdb",
                "user", "pass", null);
        assertThat(conn.getUrl()).contains("mem:testdb");
        assertThat(conn.getUser()).isEqualTo("user");
        assertThat(conn.getPassword()).isEqualTo("pass");
    }

    @Test
    void createConnectionString_oracleJdbc() {
        ConnectionStringGenerator.ConnInfo conn = ConnectionStringGenerator.createConnectionString("oracle", "db",
                "user", "pass", "localhost");
        assertThat(conn.getUrl()).contains("localhost");
        assertThat(conn.getUser()).isEqualTo("user");
        assertThat(conn.getPassword()).isEqualTo("pass");
    }
}