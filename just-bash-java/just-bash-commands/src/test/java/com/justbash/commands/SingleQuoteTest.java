package com.justbash.commands;

import com.justbash.Bash;
import com.justbash.BashExecResult;
import com.justbash.BashOptions;
import com.justbash.commands.printf.PrintfCommand;
import com.justbash.fs.InMemoryFs;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SingleQuoteTest {

    @Test
    void echoSingleQuotes() {
        Bash bash = new Bash();
        BashExecResult result = bash.exec("echo 'hello'").join();
        assertThat(result.stdout()).isEqualTo("hello\n");
        bash.shutdown();
    }

    @Test
    void echoSingleQuotesWithSpaces() {
        Bash bash = new Bash();
        BashExecResult result = bash.exec("echo 'hello world'").join();
        assertThat(result.stdout()).isEqualTo("hello world\n");
        bash.shutdown();
    }

    @Test
    void printfWithSingleQuotes() {
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(new InMemoryFs()), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new PrintfCommand());
        BashExecResult result = bash.exec("printf '%s' 'test'").join();
        assertThat(result.stdout()).isEqualTo("test");
        bash.shutdown();
    }

    @Test
    void echoAdjacentQuotesAndLiteral() {
        Bash bash = new Bash();
        BashExecResult result = bash.exec("echo 'hello'world").join();
        assertThat(result.stdout()).isEqualTo("helloworld\n");
        bash.shutdown();
    }
}
