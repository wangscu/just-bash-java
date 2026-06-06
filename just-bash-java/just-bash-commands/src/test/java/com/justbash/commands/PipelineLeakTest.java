package com.justbash.commands;

import com.justbash.Bash;
import com.justbash.BashExecResult;
import com.justbash.BashOptions;
import com.justbash.commands.cat.CatCommand;
import com.justbash.commands.sort.SortCommand;
import com.justbash.fs.IFileSystem;
import com.justbash.fs.InMemoryFs;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PipelineLeakTest {

    @Test
    void catFilePipedToSort() {
        InMemoryFs fs = new InMemoryFs();
        fs.writeFile("/tmp/file", new IFileSystem.StringContent("c\na\nb\n")).join();

        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new CatCommand());
        bash.registerCommand(new SortCommand());

        BashExecResult result = bash.exec("cat /tmp/file | sort").join();

        System.out.println("=== PIPELINE LEAK TEST ===");
        System.out.println("stdout: [" + result.stdout().replace("\n", "\\n") + "]");
        System.out.println("stderr: [" + result.stderr().replace("\n", "\\n") + "]");
        System.out.println("exitCode: " + result.exitCode());
        System.out.println("==========================");

        // Should only have sorted output, not raw file content + sorted
        assertThat(result.stdout()).isEqualTo("a\nb\nc\n");
        bash.shutdown();
    }

    @Test
    void echoPipedToCat() {
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(new InMemoryFs()), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new CatCommand());

        BashExecResult result = bash.exec("echo hello | cat").join();

        System.out.println("=== ECHO PIPE TEST ===");
        System.out.println("stdout: [" + result.stdout().replace("\n", "\\n") + "]");
        System.out.println("stderr: [" + result.stderr().replace("\n", "\\n") + "]");
        System.out.println("exitCode: " + result.exitCode());
        System.out.println("======================");

        assertThat(result.stdout()).isEqualTo("hello\n");
        bash.shutdown();
    }
}
