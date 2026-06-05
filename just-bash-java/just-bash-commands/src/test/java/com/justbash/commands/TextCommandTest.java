package com.justbash.commands;

import com.justbash.Bash;
import com.justbash.BashExecResult;
import com.justbash.BashOptions;
import com.justbash.commands.head.HeadCommand;
import com.justbash.commands.ls.LsCommand;
import com.justbash.commands.printf.PrintfCommand;
import com.justbash.commands.tail.TailCommand;
import com.justbash.commands.wc.WcCommand;
import com.justbash.fs.IFileSystem;
import com.justbash.fs.InMemoryFs;
import com.justbash.fs.MkdirOptions;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TextCommandTest {

    private Bash createBash() {
        InMemoryFs fs = new InMemoryFs();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new LsCommand());
        bash.registerCommand(new PrintfCommand());
        bash.registerCommand(new HeadCommand());
        bash.registerCommand(new TailCommand());
        bash.registerCommand(new WcCommand());
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
        bash.registerCommand(new LsCommand());
        bash.registerCommand(new PrintfCommand());
        bash.registerCommand(new HeadCommand());
        bash.registerCommand(new TailCommand());
        bash.registerCommand(new WcCommand());
        return bash;
    }

    @Test
    void printfSimple() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("printf %s hello").join();
        assertThat(result.stdout()).isEqualTo("hello");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void printfMultipleArgs() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("printf %s-%s hello world").join();
        assertThat(result.stdout()).isEqualTo("hello-world");
        bash.shutdown();
    }

    @Test
    void printfNumber() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("printf %d 42").join();
        assertThat(result.stdout()).isEqualTo("42");
        bash.shutdown();
    }

    @Test
    void printfNewline() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("printf line1\\nline2\\n").join();
        assertThat(result.stdout()).isEqualTo("line1\nline2\n");
        bash.shutdown();
    }

    @Test
    void printfHex() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("printf %x 255").join();
        assertThat(result.stdout()).isEqualTo("ff");
        bash.shutdown();
    }

    @Test
    void printfOctal() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("printf %o 8").join();
        assertThat(result.stdout()).isEqualTo("10");
        bash.shutdown();
    }

    @Test
    void printfChar() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("printf %c abc").join();
        assertThat(result.stdout()).isEqualTo("a");
        bash.shutdown();
    }

    @Test
    void printfPercentEscape() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("printf %%").join();
        assertThat(result.stdout()).isEqualTo("%");
        bash.shutdown();
    }

    @Test
    void printfNoArgs() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("printf").join();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr()).contains("usage");
        bash.shutdown();
    }

    @Test
    void lsCurrentDirectory() {
        InMemoryFs fs = new InMemoryFs();
        fs.mkdir("/home/user", new MkdirOptions(true)).join();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new LsCommand());
        BashExecResult result = bash.exec("ls").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void lsListsFile() {
        InMemoryFs fs = new InMemoryFs();
        fs.mkdir("/tmp", MkdirOptions.defaults()).join();
        fs.writeFile("/tmp/lsfile", new IFileSystem.StringContent("content")).join();
        fs.mkdir("/tmp/lsdir", MkdirOptions.defaults()).join();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new LsCommand());
        BashExecResult result = bash.exec("ls /tmp").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("lsfile");
        assertThat(result.stdout()).contains("lsdir");
        bash.shutdown();
    }

    @Test
    void lsLongFormat() {
        Bash bash = createBashWithFile("/tmp/lsfile", "content");
        BashExecResult result = bash.exec("ls -l /tmp/lsfile").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("-rw-r--r--");
        assertThat(result.stdout()).contains("lsfile");
        bash.shutdown();
    }

    @Test
    void lsAllFiles() {
        InMemoryFs fs = new InMemoryFs();
        fs.mkdir("/tmp/lsall", MkdirOptions.defaults()).join();
        fs.writeFile("/tmp/lsall/.hidden", new IFileSystem.StringContent("")).join();
        fs.writeFile("/tmp/lsall/visible", new IFileSystem.StringContent("")).join();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new LsCommand());
        BashExecResult result = bash.exec("ls -a /tmp/lsall").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains(".hidden");
        assertThat(result.stdout()).contains("visible");
        bash.shutdown();
    }

    @Test
    void lsLaCombined() {
        InMemoryFs fs = new InMemoryFs();
        fs.mkdir("/tmp/lsla", MkdirOptions.defaults()).join();
        fs.writeFile("/tmp/lsla/.hidden", new IFileSystem.StringContent("")).join();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new LsCommand());
        BashExecResult result = bash.exec("ls -la /tmp/lsla").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains(".hidden");
        bash.shutdown();
    }

    @Test
    void lsNonExistent() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("ls /nonexistent").join();
        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.stderr()).contains("No such file or directory");
        bash.shutdown();
    }

    @Test
    void headDefaultLines() {
        Bash bash = createBashWithFile("/tmp/headtest",
            "line1\nline2\nline3\nline4\nline5\nline6\nline7\nline8\nline9\nline10\nline11\n");
        BashExecResult result = bash.exec("head /tmp/headtest").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout().split("\n").length).isEqualTo(10);
        bash.shutdown();
    }

    @Test
    void headNOption() {
        Bash bash = createBashWithFile("/tmp/headn", "a\nb\nc\nd\n");
        BashExecResult result = bash.exec("head -n 2 /tmp/headn").join();
        assertThat(result.stdout()).isEqualTo("a\nb\n");
        bash.shutdown();
    }

    @Test
    void headFileNotFound() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("head /nonexistent").join();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr()).contains("No such file or directory");
        bash.shutdown();
    }

    @Test
    void tailDefaultLines() {
        Bash bash = createBashWithFile("/tmp/tailtest",
            "line1\nline2\nline3\nline4\nline5\nline6\nline7\nline8\nline9\nline10\nline11\n");
        BashExecResult result = bash.exec("tail /tmp/tailtest").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout().split("\n").length).isEqualTo(10);
        bash.shutdown();
    }

    @Test
    void tailNOption() {
        Bash bash = createBashWithFile("/tmp/tailn", "a\nb\nc\nd\n");
        BashExecResult result = bash.exec("tail -n 2 /tmp/tailn").join();
        assertThat(result.stdout()).isEqualTo("c\nd\n");
        bash.shutdown();
    }

    @Test
    void tailFileNotFound() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("tail /nonexistent").join();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr()).contains("No such file or directory");
        bash.shutdown();
    }

    @Test
    void wcFile() {
        Bash bash = createBashWithFile("/tmp/wctest", "hello world\nsecond line\n");
        BashExecResult result = bash.exec("wc /tmp/wctest").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("2");
        assertThat(result.stdout()).contains("4");
        bash.shutdown();
    }

    @Test
    void wcLinesOnly() {
        Bash bash = createBashWithFile("/tmp/wclines", "line1\nline2\nline3\n");
        BashExecResult result = bash.exec("wc -l /tmp/wclines").join();
        assertThat(result.stdout().trim()).isEqualTo("3 /tmp/wclines");
        bash.shutdown();
    }

    @Test
    void wcWordsOnly() {
        Bash bash = createBashWithFile("/tmp/wcwords", "one two three\n");
        BashExecResult result = bash.exec("wc -w /tmp/wcwords").join();
        assertThat(result.stdout().trim()).isEqualTo("3 /tmp/wcwords");
        bash.shutdown();
    }

    @Test
    void wcBytesOnly() {
        Bash bash = createBashWithFile("/tmp/wcbytes", "hello\n");
        BashExecResult result = bash.exec("wc -c /tmp/wcbytes").join();
        assertThat(result.stdout().trim()).startsWith("6");
        bash.shutdown();
    }

    @Test
    void wcMultipleFiles() {
        InMemoryFs fs = new InMemoryFs();
        fs.writeFile("/tmp/wc1", new IFileSystem.StringContent("a b\n")).join();
        fs.writeFile("/tmp/wc2", new IFileSystem.StringContent("x y z\n")).join();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new WcCommand());
        BashExecResult result = bash.exec("wc /tmp/wc1 /tmp/wc2").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("total");
        bash.shutdown();
    }

    @Test
    void wcFileNotFound() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("wc /nonexistent").join();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr()).contains("No such file or directory");
        bash.shutdown();
    }
}
