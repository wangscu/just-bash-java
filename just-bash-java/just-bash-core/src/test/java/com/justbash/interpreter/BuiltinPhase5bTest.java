package com.justbash.interpreter;

import com.justbash.Bash;
import com.justbash.BashOptions;
import com.justbash.fs.IFileSystem;
import com.justbash.fs.InMemoryFs;
import com.justbash.fs.WriteFileOptions;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

class BuiltinPhase5bTest {

    // ── eval ────────────────────────────────────────────────────────────────
    // NOTE: The current lexer/parser does not handle quotes or variable
    // expansion, so eval tests use unquoted strings only.

    @Test
    void evalSimple() {
        Bash bash = new Bash();
        var result = bash.exec("eval echo hello").join();
        assertThat(result.stdout()).isEqualTo("hello\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void evalMultipleWords() {
        Bash bash = new Bash();
        var result = bash.exec("eval echo hello world").join();
        assertThat(result.stdout()).isEqualTo("hello world\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void evalExit() {
        Bash bash = new Bash();
        var result = bash.exec("eval exit 42").join();
        assertThat(result.exitCode()).isEqualTo(42);
        bash.shutdown();
    }

    @Test
    void evalSetsVariable() {
        Bash bash = new Bash();
        // Set variable via eval, then verify via declare output
        bash.exec("eval FOO=bar").join();
        var result = bash.exec("declare").join();
        assertThat(result.stdout()).contains("FOO=\"bar\"");
        bash.shutdown();
    }

    @Test
    void evalEmptyArgs() {
        Bash bash = new Bash();
        var result = bash.exec("eval").join();
        assertThat(result.stdout()).isEqualTo("");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void evalInvalidOption() {
        Bash bash = new Bash();
        var result = bash.exec("eval -x").join();
        assertThat(result.stderr()).contains("invalid option");
        assertThat(result.exitCode()).isEqualTo(2);
        bash.shutdown();
    }

    @Test
    void evalDoubleDash() {
        Bash bash = new Bash();
        var result = bash.exec("eval -- echo hello").join();
        assertThat(result.stdout()).isEqualTo("hello\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    // ── source ──────────────────────────────────────────────────────────────

    @Test
    void sourceFile() {
        InMemoryFs fs = new InMemoryFs();
        fs.mkdir("/tmp").join();
        fs.writeFile("/tmp/test.sh",
            new IFileSystem.StringContent("echo hello from sourced file"),
            WriteFileOptions.utf8()).join();

        Bash bash = new Bash(new BashOptions(
            Optional.empty(), Optional.empty(), Optional.of(fs),
            Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty()
        ));
        var result = bash.exec("source /tmp/test.sh").join();
        assertThat(result.stdout()).isEqualTo("hello from sourced file\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void sourceDotAlias() {
        InMemoryFs fs = new InMemoryFs();
        fs.mkdir("/tmp").join();
        fs.writeFile("/tmp/test.sh",
            new IFileSystem.StringContent("echo dot alias works"),
            WriteFileOptions.utf8()).join();

        Bash bash = new Bash(new BashOptions(
            Optional.empty(), Optional.empty(), Optional.of(fs),
            Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty()
        ));
        var result = bash.exec(". /tmp/test.sh").join();
        assertThat(result.stdout()).isEqualTo("dot alias works\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void sourceMissingFile() {
        Bash bash = new Bash();
        var result = bash.exec("source /nonexistent.sh").join();
        assertThat(result.stderr()).contains("No such file or directory");
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    @Test
    void sourceNoArgs() {
        Bash bash = new Bash();
        var result = bash.exec("source").join();
        assertThat(result.stderr()).contains("filename argument required");
        assertThat(result.exitCode()).isEqualTo(2);
        bash.shutdown();
    }

    @Test
    void sourceSetsVariable() {
        InMemoryFs fs = new InMemoryFs();
        fs.mkdir("/tmp").join();
        fs.writeFile("/tmp/test.sh",
            new IFileSystem.StringContent("SOURCED_VAR=42"),
            WriteFileOptions.utf8()).join();

        Bash bash = new Bash(new BashOptions(
            Optional.empty(), Optional.empty(), Optional.of(fs),
            Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty()
        ));
        bash.exec("source /tmp/test.sh").join();
        // Verify via declare since $VAR expansion is not yet implemented
        var result = bash.exec("declare").join();
        assertThat(result.stdout()).contains("SOURCED_VAR=\"42\"");
        bash.shutdown();
    }

    // ── declare ─────────────────────────────────────────────────────────────

    @Test
    void declareAndPrint() {
        Bash bash = new Bash();
        bash.exec("declare FOO=bar").join();
        var result = bash.exec("declare").join();
        assertThat(result.stdout()).contains("FOO=\"bar\"");
        bash.shutdown();
    }

    @Test
    void declareExport() {
        Bash bash = new Bash();
        bash.exec("declare -x FOO=bar").join();
        var result = bash.exec("export").join();
        assertThat(result.stdout()).contains("FOO");
        bash.shutdown();
    }

    @Test
    void declareReadonly() {
        Bash bash = new Bash();
        bash.exec("declare -r RO_VAR=locked").join();
        var result = bash.exec("declare").join();
        assertThat(result.stdout()).contains("RO_VAR");
        assertThat(result.stdout()).contains("-r");
        bash.shutdown();
    }

    @Test
    void declareInteger() {
        Bash bash = new Bash();
        bash.exec("declare -i INT_VAR=5").join();
        var result = bash.exec("declare").join();
        assertThat(result.stdout()).contains("INT_VAR");
        assertThat(result.stdout()).contains("-i");
        bash.shutdown();
    }

    @Test
    void declareCombinedFlags() {
        Bash bash = new Bash();
        bash.exec("declare -xr COMBINED=val").join();
        var result = bash.exec("declare").join();
        assertThat(result.stdout()).contains("COMBINED");
        assertThat(result.stdout()).contains("-r");
        assertThat(result.stdout()).contains("-x");
        bash.shutdown();
    }

    @Test
    void declarePrintSkipsQuestionMark() {
        Bash bash = new Bash();
        var result = bash.exec("declare").join();
        assertThat(result.stdout()).doesNotContain("?=\"");
        bash.shutdown();
    }

    // ── read ────────────────────────────────────────────────────────────────

    @Test
    void readSimple() {
        Bash bash = new Bash();
        // Without stdin, read returns empty value for the variable
        var result = bash.exec("read VAR").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void readSetsVariable() {
        Bash bash = new Bash();
        // Set stdin on state before executing read
        bash.exec("read VAR").join();
        // Verify via declare since $VAR expansion is not yet implemented
        var result = bash.exec("declare").join();
        assertThat(result.stdout()).contains("VAR=\"\"");
        bash.shutdown();
    }

    @Test
    void readRawFlag() {
        Bash bash = new Bash();
        var result = bash.exec("read -r VAR").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void readDefaultReplyVariable() {
        Bash bash = new Bash();
        var result = bash.exec("read").join();
        assertThat(result.exitCode()).isEqualTo(0);
        // Verify REPLY was set (to empty since no stdin)
        var declareResult = bash.exec("declare").join();
        assertThat(declareResult.stdout()).contains("REPLY=\"\"");
        bash.shutdown();
    }

    // ── local ───────────────────────────────────────────────────────────────

    @Test
    void localOutsideFunctionFails() {
        Bash bash = new Bash();
        var result = bash.exec("local VAR=val").join();
        assertThat(result.stderr()).contains("can only be used in a function");
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }
}
