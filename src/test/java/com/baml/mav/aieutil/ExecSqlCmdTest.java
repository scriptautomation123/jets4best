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
                "--password", "pass",
                "--type", "oracle");
        assertThat(exitCode).isEqualTo(0);
    }
}