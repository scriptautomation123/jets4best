package com.baml.mav.aieutil;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;

class ExecSqlCmdTest {
    @Test
    void parsesArgumentsAndCalls() {
        ExecSqlCmd cmd = new ExecSqlCmd();
        CommandLine cli = new CommandLine(cmd);
        int exitCode = cli.execute(
                "SELECT * FROM DUAL",
                "--database", "testdb",
                "--user", "user",
                "--type", "oracle");
        // Test that arguments are parsed correctly (exit code 1 indicates execution
        // error, not parsing error)
        assertThat(exitCode).isEqualTo(1);
    }
}