package com.baml.mav.aieutil.util;

public final class CliMessages {
    private CliMessages() {
    }

    public static final String HELP_EXAMPLES = """
            ========================================
            AIE Util CLI - Usage Examples
            ========================================

            BASIC USAGE:
            ------------
            # Show help
            aieutil --help

            # Show examples
            aieutil --help-examples

            # Print configuration
            aieutil --print-config

            # Show sample connection string
            aieutil --sample-connect

            ORACLE CONNECTIONS:
            -------------------
            # Oracle JDBC (with host)
            aieutil --sql "SELECT 1 FROM DUAL" \
                    -u system -p oracle123 -d XE --host localhost

            # Oracle LDAP (no host = uses LDAP)
            aieutil --sql "SELECT * FROM users" \
                    -u myuser -p mypass -d ORCL

            H2 DATABASE:
            ------------
            # H2 Memory database
            aieutil -t h2 --sql "SELECT 1" \
                    -u sa -p "" -d testdb

            # H2 JDBC database
            aieutil -t h2 --sql "SELECT * FROM users" \
                    -u sa -p "" -d testdb --host localhost

            SQL EXECUTION:
            --------------
            # Simple query
            aieutil --sql "SELECT * FROM employees WHERE dept_id = 10" \
                    -u hr -p hr123 -d ORCL

            # Update statement
            aieutil --sql "UPDATE users SET status = 'active' WHERE id = 1" \
                    -u app_user -p app123 -d ORCL

            SQL SCRIPTS:
            ------------
            # Execute SQL script file
            aieutil --script /path/to/script.sql \
                    -u system -p oracle123 -d XE --host localhost

            # H2 script execution
            aieutil -t h2 --script src/main/resources/h2_test.sql \
                    -u sa -p "" -d testdb

            STORED PROCEDURES:
            ------------------
            # Simple procedure call
            aieutil --procedure "TEST_PROC" \
                    -u system -p oracle123 -d XE --host localhost

            # Procedure with input parameters
            aieutil --procedure "CALCULATE_SALARY" \
                    --input "emp_id:INTEGER:1001,salary:NUMBER:50000" \
                    -u hr -p hr123 -d ORCL

            # Procedure with output parameters
            aieutil --procedure "GET_EMPLOYEE_INFO" \
                    --input "emp_id:INTEGER:1001" \
                    --output "name:VARCHAR,salary:NUMBER" \
                    -u hr -p hr123 -d ORCL

            PARAMETER TYPES:
            ----------------
            Supported input parameter types:
            - STRING, VARCHAR, VARCHAR2
            - INTEGER, INT
            - DOUBLE, NUMBER
            - DATE
            - TIMESTAMP
            - BOOLEAN

            Parameter format: name:type:value
            Multiple parameters: name1:type1:value1,name2:type2:value2

            OUTPUT PARAMETER TYPES:
            -----------------------
            Supported output parameter types:
            - STRING, VARCHAR, VARCHAR2
            - INTEGER, INT
            - DOUBLE, NUMBER
            - DATE
            - TIMESTAMP
            - BOOLEAN

            Output format: name:type
            Multiple outputs: name1:type1,name2:type2

            ========================================
            For more help: aieutil --help
            ========================================
            """;
}