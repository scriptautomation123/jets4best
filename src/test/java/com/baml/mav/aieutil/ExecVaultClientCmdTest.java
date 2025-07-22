package com.baml.mav.aieutil;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;

class ExecVaultClientCmdTest {
    @Test
    void parsesArgumentsAndCalls() {
        ExecVaultClientCmd cmd = new ExecVaultClientCmd();
        CommandLine cli = new CommandLine(cmd);
        int exitCode = cli.execute(
                "--database", "testdb",
                "--user", "user",
                "--vault-id", "vaultid",
                "--type", "oracle");
        // Test that arguments are parsed correctly (exit code 1 indicates execution
        // error, not parsing error)
        assertThat(exitCode).isEqualTo(1);
    }
}