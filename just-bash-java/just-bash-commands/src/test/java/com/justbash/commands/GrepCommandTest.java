package com.justbash.commands;

import com.justbash.Bash;
import com.justbash.BashExecResult;
import com.justbash.BashOptions;
import com.justbash.commands.grep.GrepCommand;
import com.justbash.fs.IFileSystem;
import com.justbash.fs.InMemoryFs;
import com.justbash.fs.MkdirOptions;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GrepCommandTest {

    private Bash createBashWithFile(String path, String content) {
        InMemoryFs fs = new InMemoryFs();
        fs.writeFile(path, new IFileSystem.StringContent(content)).join();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new GrepCommand());
        return bash;
    }

    @Test
    void grepBasicMatch() {
        Bash bash = createBashWithFile("/tmp/test.txt", "hello world\nfoo bar\nhello again\n");
        BashExecResult result = bash.exec("grep hello /tmp/test.txt").join();
        assertThat(result.stdout()).contains("hello world");
        assertThat(result.stdout()).contains("hello again");
        assertThat(result.stdout()).doesNotContain("foo bar");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void grepNoMatch() {
        Bash bash = createBashWithFile("/tmp/test.txt", "hello world\n");
        BashExecResult result = bash.exec("grep xyz /tmp/test.txt").join();
        assertThat(result.stdout()).isEmpty();
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    @Test
    void grepIgnoreCase() {
        Bash bash = createBashWithFile("/tmp/test.txt", "Hello World\n");
        BashExecResult result = bash.exec("grep -i hello /tmp/test.txt").join();
        assertThat(result.stdout()).contains("Hello World");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void grepInvertMatch() {
        Bash bash = createBashWithFile("/tmp/test.txt", "hello\nworld\nhello\n");
        BashExecResult result = bash.exec("grep -v hello /tmp/test.txt").join();
        assertThat(result.stdout()).contains("world");
        assertThat(result.stdout()).doesNotContain("hello");
        bash.shutdown();
    }

    @Test
    void grepLineNumbers() {
        Bash bash = createBashWithFile("/tmp/test.txt", "line1\nline2\nhello\n");
        BashExecResult result = bash.exec("grep -n hello /tmp/test.txt").join();
        assertThat(result.stdout()).isEqualTo("3:hello\n");
        bash.shutdown();
    }

    @Test
    void grepCount() {
        Bash bash = createBashWithFile("/tmp/test.txt", "hello\nworld\nhello\nhello\n");
        BashExecResult result = bash.exec("grep -c hello /tmp/test.txt").join();
        assertThat(result.stdout().trim()).isEqualTo("3");
        bash.shutdown();
    }

    @Test
    void grepFixedString() {
        Bash bash = createBashWithFile("/tmp/test.txt", "a.b.c\nabc\n");
        BashExecResult result = bash.exec("grep -F a.b.c /tmp/test.txt").join();
        assertThat(result.stdout()).contains("a.b.c");
        bash.shutdown();
    }

    @Test
    void grepMultipleFiles() {
        InMemoryFs fs = new InMemoryFs();
        fs.writeFile("/tmp/a.txt", new IFileSystem.StringContent("hello from a\n")).join();
        fs.writeFile("/tmp/b.txt", new IFileSystem.StringContent("hello from b\n")).join();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new GrepCommand());
        BashExecResult result = bash.exec("grep hello /tmp/a.txt /tmp/b.txt").join();
        assertThat(result.stdout()).contains("a.txt:hello from a");
        assertThat(result.stdout()).contains("b.txt:hello from b");
        bash.shutdown();
    }

    @Test
    void grepStdin() {
        InMemoryFs fs = new InMemoryFs();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new GrepCommand());
        BashExecResult result = bash.exec("grep hello").join();
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    @Test
    void grepListFilesOnly() {
        InMemoryFs fs = new InMemoryFs();
        fs.writeFile("/tmp/match.txt", new IFileSystem.StringContent("hello world\n")).join();
        fs.writeFile("/tmp/nomatch.txt", new IFileSystem.StringContent("foo bar\n")).join();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new GrepCommand());
        BashExecResult result = bash.exec("grep -l hello /tmp/match.txt /tmp/nomatch.txt").join();
        assertThat(result.stdout()).contains("match.txt");
        assertThat(result.stdout()).doesNotContain("nomatch.txt");
        bash.shutdown();
    }

    @Test
    void grepRecursive() {
        InMemoryFs fs = new InMemoryFs();
        fs.mkdir("/tmp/recdir", MkdirOptions.defaults()).join();
        fs.writeFile("/tmp/recdir/file1.txt", new IFileSystem.StringContent("hello from file1\n")).join();
        fs.writeFile("/tmp/recdir/file2.txt", new IFileSystem.StringContent("goodbye from file2\n")).join();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new GrepCommand());
        BashExecResult result = bash.exec("grep -r hello /tmp/recdir").join();
        assertThat(result.stdout()).contains("file1.txt:hello from file1");
        assertThat(result.stdout()).doesNotContain("file2.txt");
        bash.shutdown();
    }

    @Test
    void grepDirectoryWithoutRecursive() {
        InMemoryFs fs = new InMemoryFs();
        fs.mkdir("/tmp/grepdir", MkdirOptions.defaults()).join();
        fs.writeFile("/tmp/grepdir/file.txt", new IFileSystem.StringContent("hello\n")).join();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new GrepCommand());
        BashExecResult result = bash.exec("grep hello /tmp/grepdir").join();
        assertThat(result.stderr()).contains("Is a directory");
        bash.shutdown();
    }

    @Test
    void grepCombinedFlags() {
        Bash bash = createBashWithFile("/tmp/test.txt", "Hello\nWORLD\nhello\n");
        BashExecResult result = bash.exec("grep -iv hello /tmp/test.txt").join();
        assertThat(result.stdout()).contains("WORLD");
        assertThat(result.stdout()).doesNotContain("Hello");
        assertThat(result.stdout()).doesNotContain("hello");
        bash.shutdown();
    }

    @Test
    void grepFileNotFound() {
        Bash bash = createBashWithFile("/tmp/test.txt", "hello\n");
        BashExecResult result = bash.exec("grep hello /tmp/nonexistent.txt").join();
        assertThat(result.stderr()).contains("No such file or directory");
        bash.shutdown();
    }

    @Test
    void grepExtendedRegex() {
        Bash bash = createBashWithFile("/tmp/test.txt", "hello world\nfoo bar\n");
        BashExecResult result = bash.exec("grep -E h.llo /tmp/test.txt").join();
        assertThat(result.stdout()).contains("hello world");
        bash.shutdown();
    }

    @Test
    void grepCountMultipleFiles() {
        InMemoryFs fs = new InMemoryFs();
        fs.writeFile("/tmp/ca.txt", new IFileSystem.StringContent("hello\nhello\n")).join();
        fs.writeFile("/tmp/cb.txt", new IFileSystem.StringContent("hello\n")).join();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new GrepCommand());
        BashExecResult result = bash.exec("grep -c hello /tmp/ca.txt /tmp/cb.txt").join();
        assertThat(result.stdout()).contains("ca.txt:2");
        assertThat(result.stdout()).contains("cb.txt:1");
        bash.shutdown();
    }
}
