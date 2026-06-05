package com.justbash.cli;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class JustBashCliTest {

    @Test
    void echoHello() {
        int code = JustBashCli.run(new String[]{"-c", "echo hello"});
        assertThat(code).isEqualTo(0);
    }

    @Test
    void exitCode() {
        int code = JustBashCli.run(new String[]{"-c", "exit 42"});
        assertThat(code).isEqualTo(42);
    }

    @Test
    void version() {
        int code = JustBashCli.run(new String[]{"-v"});
        assertThat(code).isEqualTo(0);
    }

    @Test
    void help() {
        int code = JustBashCli.run(new String[]{"-h"});
        assertThat(code).isEqualTo(0);
    }

    @Disabled("set -e is not yet implemented in the interpreter")
    @Test
    void errexit() {
        int code = JustBashCli.run(new String[]{"-e", "-c", "false; echo should not print"});
        assertThat(code).isEqualTo(1);
    }

    @Test
    void jsonOutput() {
        int code = JustBashCli.run(new String[]{"--json", "-c", "echo hello"});
        assertThat(code).isEqualTo(0);
    }

    @Test
    void combinedFlags() {
        int code = JustBashCli.run(new String[]{"-ec", "echo hello"});
        assertThat(code).isEqualTo(0);
    }
}
