package com.baml.mav.aieutil;

import static com.github.stefanbirkner.systemlambda.SystemLambda.catchSystemExit;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AieUtilMainTest {
    @Test
    void mainHelpAndVersion_exitZero() throws Exception {
        int exitCodeHelp = catchSystemExit(new com.github.stefanbirkner.systemlambda.Statement() {
            public void execute() throws Exception {
                AieUtilMain.main(new String[] { "--help" });
            }
        });
        assertThat(exitCodeHelp).isEqualTo(0);
        int exitCodeVersion = catchSystemExit(new com.github.stefanbirkner.systemlambda.Statement() {
            public void execute() throws Exception {
                AieUtilMain.main(new String[] { "--version" });
            }
        });
        assertThat(exitCodeVersion).isEqualTo(0);
    }
}