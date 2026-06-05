package com.justbash.commands;

import com.justbash.Bash;
import com.justbash.BashExecResult;
import com.justbash.BashOptions;
import com.justbash.commands.cat.CatCommand;
import com.justbash.commands.cp.CpCommand;
import com.justbash.commands.mkdir.MkdirCommand;
import com.justbash.commands.mv.MvCommand;
import com.justbash.commands.rm.RmCommand;
import com.justbash.commands.touch.TouchCommand;
import com.justbash.fs.IFileSystem;
import com.justbash.fs.InMemoryFs;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FileCommandTest {

    private Bash createBash() {
        InMemoryFs fs = new InMemoryFs();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new CatCommand());
        bash.registerCommand(new MkdirCommand());
        bash.registerCommand(new RmCommand());
        bash.registerCommand(new TouchCommand());
        bash.registerCommand(new CpCommand());
        bash.registerCommand(new MvCommand());
        return bash;
    }

    private Bash createBashWithFile(String path, String content) {
        InMemoryFs fs = new InMemoryFs();
        fs.writeFile(path, new IFileSystem.StringContent(content)).join();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new CatCommand());
        bash.registerCommand(new MkdirCommand());
        bash.registerCommand(new RmCommand());
        bash.registerCommand(new TouchCommand());
        bash.registerCommand(new CpCommand());
        bash.registerCommand(new MvCommand());
        return bash;
    }

    @Test
    void touchCreatesFile() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("touch /tmp/testfile").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void touchMissingOperand() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("touch").join();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr()).contains("missing file operand");
        bash.shutdown();
    }

    @Test
    void mkdirCreatesDirectory() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("mkdir /tmp/testdir").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void mkdirMissingOperand() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("mkdir").join();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr()).contains("missing operand");
        bash.shutdown();
    }

    @Test
    void catReadsFile() {
        Bash bash = createBashWithFile("/tmp/cattest", "hello world\n");
        BashExecResult result = bash.exec("cat /tmp/cattest").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).isEqualTo("hello world\n");
        bash.shutdown();
    }

    @Test
    void catFileNotFound() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("cat /tmp/nonexistent").join();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr()).contains("No such file or directory");
        bash.shutdown();
    }

    @Test
    void catWithLineNumbers() {
        Bash bash = createBashWithFile("/tmp/numtest", "line1\nline2\nline3\n");
        BashExecResult result = bash.exec("cat -n /tmp/numtest").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("     1\tline1");
        assertThat(result.stdout()).contains("     2\tline2");
        assertThat(result.stdout()).contains("     3\tline3");
        bash.shutdown();
    }

    @Test
    void catReadsStdin() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("cat").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void rmRemovesFile() {
        Bash bash = createBashWithFile("/tmp/rmtest", "content");
        BashExecResult result = bash.exec("rm /tmp/rmtest").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void rmMissingOperand() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("rm").join();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr()).contains("missing operand");
        bash.shutdown();
    }

    @Test
    void rmDirectoryWithoutRecursive() {
        Bash bash = createBash();
        bash.exec("mkdir /tmp/rmdirtest").join();
        BashExecResult result = bash.exec("rm /tmp/rmdirtest").join();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr()).contains("Is a directory");
        bash.shutdown();
    }

    @Test
    void rmForceIgnoresMissing() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("rm -f /tmp/nonexistent").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void cpCopiesFile() {
        Bash bash = createBashWithFile("/tmp/cpsrc", "content\n");
        BashExecResult result = bash.exec("cp /tmp/cpsrc /tmp/cpdest").join();
        assertThat(result.exitCode()).isEqualTo(0);

        result = bash.exec("cat /tmp/cpdest").join();
        assertThat(result.stdout()).isEqualTo("content\n");
        bash.shutdown();
    }

    @Test
    void cpMissingOperand() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("cp").join();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr()).contains("missing file operand");
        bash.shutdown();
    }

    @Test
    void cpSourceNotFound() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("cp /tmp/nonexistent /tmp/dest").join();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr()).contains("No such file or directory");
        bash.shutdown();
    }

    @Test
    void mvMovesFile() {
        Bash bash = createBashWithFile("/tmp/mvsrc", "content\n");
        BashExecResult result = bash.exec("mv /tmp/mvsrc /tmp/mvdest").join();
        assertThat(result.exitCode()).isEqualTo(0);

        result = bash.exec("cat /tmp/mvdest").join();
        assertThat(result.stdout()).isEqualTo("content\n");
        bash.shutdown();
    }

    @Test
    void mvMissingOperand() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("mv").join();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr()).contains("missing file operand");
        bash.shutdown();
    }

    @Test
    void mvSourceNotFound() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("mv /tmp/nonexistent /tmp/dest").join();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr()).contains("No such file or directory");
        bash.shutdown();
    }

    @Test
    void commandRegistryRegistersAll() {
        Bash bash = createBash();
        CommandRegistry.registerAll(bash);
        // Just verify no exception is thrown
        bash.shutdown();
    }
}
