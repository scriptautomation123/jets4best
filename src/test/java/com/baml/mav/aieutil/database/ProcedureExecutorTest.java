// NOTE: This test is written for Java 8 compatibility. If you require tests for a different Java version, please specify.
package com.baml.mav.aieutil.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProcedureExecutorTest {

    private static Connection conn;
    private static ProcedureExecutor executor;

    @BeforeAll
    static void setupDb() throws Exception {
        conn = DriverManager.getConnection("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        executor = new ProcedureExecutor();
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE ALIAS ADD_TWO AS $$ int addTwo(int a, int b) { return a + b; } $$;");
        }
    }

    @AfterAll
    static void cleanup() throws Exception {
        conn.close();
    }

    @Test
    void fromString_validInput() {
        ProcedureExecutor.ProcedureParam param = ProcedureExecutor.ProcedureParam.fromString("foo:INTEGER:42");
        assertThat(param.name()).isEqualTo("foo");
        assertThat(param.type()).isEqualTo("INTEGER");
        assertThat(param.value()).isEqualTo("42");
    }

    @Test
    void getTypedValue_integer() {
        ProcedureExecutor.ProcedureParam param = new ProcedureExecutor.ProcedureParam("foo", "INTEGER", "123");
        assertThat(param.getTypedValue()).isEqualTo(123);
    }

    @Test
    void executeProcedureWithStrings_inputOnly() throws Exception {
        // H2: test a function with two input parameters, no output params
        Map<String, Object> result = executor.executeProcedureWithStrings(
                conn,
                "ADD_TWO",
                "a:INTEGER:2,b:INTEGER:3",
                null);
        assertThat(result).isNotNull();
    }
}