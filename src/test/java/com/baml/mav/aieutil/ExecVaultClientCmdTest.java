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
                "--vault-url", "https://vault.example.com",
                "--role-id", "roleid",
                "--secret-id", "secretid",
                "--ait", "ait",
                "--type", "oracle");
        assertThat(exitCode).isEqualTo(0);
    }
}