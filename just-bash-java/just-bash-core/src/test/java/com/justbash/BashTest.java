package com.justbash;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;

class BashTest {
    @Test
    void bashCanBeConstructed() {
        Bash bash = new Bash();
        assertThat(bash.getCwd()).isEqualTo("/home/user");
        assertThat(bash.getEnv()).containsKey("PATH");
        bash.shutdown();
    }

    @Test
    void execReturnsCompletableFuture() {
        Bash bash = new Bash();
        CompletableFuture<BashExecResult> future = bash.exec("echo hello");
        assertThat(future).isNotNull();

        BashExecResult result = future.join();
        // Currently returns empty due to skeleton interpreter
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }
}
