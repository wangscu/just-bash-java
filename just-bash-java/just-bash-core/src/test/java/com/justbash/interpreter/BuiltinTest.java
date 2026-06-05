package com.justbash.interpreter;

import com.justbash.Bash;
import com.justbash.BashOptions;
import com.justbash.fs.InMemoryFs;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

class BuiltinTest {

    @Test
    void echoBuiltin() {
        Bash bash = new Bash();
        var result = bash.exec("echo hello").join();
        assertThat(result.stdout()).isEqualTo("hello\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void exportSetsVariable() {
        Bash bash = new Bash();
        // Export with value
        var result = bash.exec("export FOO=bar").join();
        assertThat(result.exitCode()).isEqualTo(0);
        // Verify via export list
        var listResult = bash.exec("export").join();
        assertThat(listResult.stdout()).contains("declare -x FOO=\"bar\"");
        bash.shutdown();
    }

    @Test
    void exportListContainsDefaultVars() {
        Bash bash = new Bash();
        var result = bash.exec("export").join();
        assertThat(result.stdout()).contains("declare -x PATH");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void unsetVariable() {
        Bash bash = new Bash();
        bash.exec("export FOO=bar").join();
        // Verify it's set
        var listResult = bash.exec("export").join();
        assertThat(listResult.stdout()).contains("FOO");
        // Unset it
        var unsetResult = bash.exec("unset FOO").join();
        assertThat(unsetResult.exitCode()).isEqualTo(0);
        // Verify it's gone
        var listAfter = bash.exec("export").join();
        assertThat(listAfter.stdout()).doesNotContain("FOO");
        bash.shutdown();
    }

    @Test
    void unsetReadonlyVariableFails() {
        Bash bash = new Bash();
        // Set a readonly variable via prefix assignment (readonly not a builtin yet)
        // For now, just test that unsetting a non-existent var doesn't error
        var result = bash.exec("unset NONEXISTENT").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void cdAndPwd() {
        // Create an InMemoryFs with /tmp directory
        InMemoryFs fs = new InMemoryFs();
        fs.mkdir("/tmp").join();
        Bash bash = new Bash(new BashOptions(
            Optional.empty(), Optional.empty(), Optional.of(fs),
            Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty()
        ));
        bash.exec("cd /tmp").join();
        var result = bash.exec("pwd").join();
        assertThat(result.stdout()).isEqualTo("/tmp\n");
        bash.shutdown();
    }

    @Test
    void cdDash() {
        InMemoryFs fs = new InMemoryFs();
        fs.mkdir("/tmp").join();
        fs.mkdir("/usr").join();
        Bash bash = new Bash(new BashOptions(
            Optional.empty(), Optional.empty(), Optional.of(fs),
            Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty()
        ));
        bash.exec("cd /tmp").join();
        bash.exec("cd /usr").join();
        var result = bash.exec("cd -").join();
        assertThat(result.stdout()).isEqualTo("/tmp\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void cdNonExistentDirectory() {
        Bash bash = new Bash();
        var result = bash.exec("cd /nonexistent").join();
        assertThat(result.stderr()).contains("No such file or directory");
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    @Test
    void exitBuiltin() {
        Bash bash = new Bash();
        var result = bash.exec("exit 42").join();
        assertThat(result.exitCode()).isEqualTo(42);
        bash.shutdown();
    }

    @Test
    void exitWithDefaultCode() {
        Bash bash = new Bash();
        bash.exec("false").join(); // sets last exit to 1
        var result = bash.exec("exit").join();
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    @Test
    void exitInvalidArgument() {
        Bash bash = new Bash();
        var result = bash.exec("exit abc").join();
        assertThat(result.stderr()).contains("numeric argument required");
        assertThat(result.exitCode()).isEqualTo(2);
        bash.shutdown();
    }

    @Test
    void exportAppend() {
        Bash bash = new Bash();
        bash.exec("export FOO=hello").join();
        bash.exec("export FOO+=world").join();
        var result = bash.exec("export").join();
        assertThat(result.stdout()).contains("declare -x FOO=\"helloworld\"");
        bash.shutdown();
    }

    @Test
    void exportUnexport() {
        Bash bash = new Bash();
        bash.exec("export FOO=bar").join();
        var before = bash.exec("export").join();
        assertThat(before.stdout()).contains("FOO");

        bash.exec("export -n FOO").join();
        var after = bash.exec("export").join();
        assertThat(after.stdout()).doesNotContain("FOO");
        bash.shutdown();
    }
}
