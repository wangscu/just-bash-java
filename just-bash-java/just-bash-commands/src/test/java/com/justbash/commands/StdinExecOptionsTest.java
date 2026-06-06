package com.justbash.commands;

import com.justbash.Bash;
import com.justbash.BashExecResult;
import com.justbash.BashOptions;
import com.justbash.ExecOptions;
import com.justbash.commands.tr.TrCommand;
import com.justbash.fs.InMemoryFs;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class StdinExecOptionsTest {

    @Test
    void execOptionsStdinPassedToCommand() {
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(new InMemoryFs()), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new TrCommand());

        ExecOptions opts = new ExecOptions(
            java.util.Optional.empty(), false, java.util.Optional.empty(),
            false, java.util.Optional.of("hello"), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        );

        BashExecResult result = bash.exec("tr a-z A-Z", opts).join();
        assertThat(result.stdout()).isEqualTo("HELLO");
        bash.shutdown();
    }
}
