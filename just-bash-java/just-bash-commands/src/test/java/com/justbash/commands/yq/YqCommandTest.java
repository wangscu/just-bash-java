package com.justbash.commands.yq;

import com.justbash.Bash;
import com.justbash.BashExecResult;
import com.justbash.BashOptions;
import com.justbash.ExecOptions;
import com.justbash.fs.IFileSystem;
import com.justbash.fs.InMemoryFs;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class YqCommandTest {

    private Bash createBash() {
        InMemoryFs fs = new InMemoryFs();
        Bash bash = new Bash(new BashOptions(
            Optional.empty(), Optional.empty(),
            Optional.of(fs), Optional.empty(),
            Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty()
        ));
        bash.registerCommand(new YqCommand());
        return bash;
    }

    private Bash createBashWithFile(String path, String content) {
        InMemoryFs fs = new InMemoryFs();
        fs.writeFile(path, new IFileSystem.StringContent(content)).join();
        Bash bash = new Bash(new BashOptions(
            Optional.empty(), Optional.empty(),
            Optional.of(fs), Optional.empty(),
            Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty()
        ));
        bash.registerCommand(new YqCommand());
        return bash;
    }

    @Test
    void helpText() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("yq --help").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("Usage: yq");
        bash.shutdown();
    }

    @Test
    void basicYamlQuery() {
        Bash bash = createBashWithFile("/tmp/test.yaml", "name: Alice\nage: 30\n");
        BashExecResult result = bash.exec("yq '.name' /tmp/test.yaml").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("Alice");
        bash.shutdown();
    }

    @Test
    void yamlArrayIteration() {
        Bash bash = createBashWithFile("/tmp/test.yaml", "items:\n  - foo\n  - bar\n  - baz\n");
        BashExecResult result = bash.exec("yq '.items[]' /tmp/test.yaml").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("foo");
        assertThat(result.stdout()).contains("bar");
        assertThat(result.stdout()).contains("baz");
        bash.shutdown();
    }

    @Test
    void jsonInputOutput() {
        Bash bash = createBashWithFile("/tmp/test.json", "{\"name\": \"Bob\", \"score\": 42}");
        BashExecResult result = bash.exec("yq -p json -o json '.name' /tmp/test.json").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("Bob");
        bash.shutdown();
    }

    @Test
    void jsonCompactOutput() {
        Bash bash = createBashWithFile("/tmp/test.json", "{\"a\": 1, \"b\": 2}");
        BashExecResult result = bash.exec("yq -p json -o json -c '.' /tmp/test.json").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout().trim()).doesNotContain("\n");
        bash.shutdown();
    }

    @Test
    void rawOutput() {
        Bash bash = createBashWithFile("/tmp/test.json", "{\"msg\": \"hello\"}");
        BashExecResult result = bash.exec("yq -p json -o json -r '.msg' /tmp/test.json").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout().trim()).isEqualTo("hello");
        bash.shutdown();
    }

    @Test
    void pipeFilter() {
        Bash bash = createBashWithFile("/tmp/test.yaml", "data:\n  value: 123\n");
        BashExecResult result = bash.exec("yq '.data | .value' /tmp/test.yaml").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("123");
        bash.shutdown();
    }

    @Test
    void nullInput() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("yq -n '1 + 2'").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("3");
        bash.shutdown();
    }

    @Test
    void slurpMode() {
        Bash bash = createBashWithFile("/tmp/test.yaml", "- a\n- b\n");
        BashExecResult result = bash.exec("yq -s '. | length' /tmp/test.yaml").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("1");
        bash.shutdown();
    }

    @Test
    void stdinInput() {
        Bash bash = createBash();
        ExecOptions opts = new ExecOptions(
            Optional.empty(), false, Optional.empty(), false,
            Optional.of("name: Carol\n"), Optional.of(ExecOptions.StdinKind.TEXT),
            Optional.empty(), Optional.empty()
        );
        BashExecResult result = bash.exec("yq '.name'", opts).join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("Carol");
        bash.shutdown();
    }

    @Test
    void inplaceEditing() {
        Bash bash = createBashWithFile("/tmp/test.yaml", "version: 1\n");
        BashExecResult result = bash.exec("yq -i '.version = \"2\"' /tmp/test.yaml").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).isEmpty();

        String content = bash.readFile("/tmp/test.yaml").join();
        assertThat(content).contains("version");
        assertThat(content).contains("2");
        bash.shutdown();
    }

    @Test
    void badFilterError() {
        Bash bash = createBashWithFile("/tmp/test.yaml", "x: 1\n");
        BashExecResult result = bash.exec("yq '!!!' /tmp/test.yaml").join();
        assertThat(result.exitCode()).isNotEqualTo(0);
        assertThat(result.stderr()).isNotEmpty();
        bash.shutdown();
    }

    @Test
    void missingFileError() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("yq '.' /tmp/nonexistent.yaml").join();
        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.stderr()).contains("No such file");
        bash.shutdown();
    }

    @Test
    void exitStatusTrue() {
        Bash bash = createBashWithFile("/tmp/test.yaml", "false\n");
        BashExecResult result = bash.exec("yq -e '.' /tmp/test.yaml").join();
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    @Test
    void exitStatusFalse() {
        Bash bash = createBashWithFile("/tmp/test.yaml", "true\n");
        BashExecResult result = bash.exec("yq -e '.' /tmp/test.yaml").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void xmlInput() {
        Bash bash = createBashWithFile("/tmp/test.xml", "<root><item>1</item><item>2</item></root>");
        BashExecResult result = bash.exec("yq -p xml '.' /tmp/test.xml").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).isNotEmpty();
        bash.shutdown();
    }

    @Test
    void autoDetectJsonExtension() {
        Bash bash = createBashWithFile("/tmp/test.json", "{\"key\": \"val\"}");
        BashExecResult result = bash.exec("yq '.key' /tmp/test.json").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("val");
        bash.shutdown();
    }

    @Test
    void autoDetectYamlExtension() {
        Bash bash = createBashWithFile("/tmp/test.yml", "key: value\n");
        BashExecResult result = bash.exec("yq '.key' /tmp/test.yml").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("value");
        bash.shutdown();
    }

    @Test
    void tomlInputOutput() {
        Bash bash = createBashWithFile("/tmp/test.toml", "[section]\nkey = \"value\"\n");
        BashExecResult result = bash.exec("yq -p toml '.section.key' /tmp/test.toml").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("value");
        bash.shutdown();
    }

    @Test
    void csvInput() {
        Bash bash = createBashWithFile("/tmp/test.csv", "name,age\nAlice,30\nBob,25\n");
        BashExecResult result = bash.exec("yq -p csv '.[0].name' /tmp/test.csv").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("Alice");
        bash.shutdown();
    }

    @Test
    void iniInput() {
        Bash bash = createBashWithFile("/tmp/test.ini", "[section]\nkey=value\n");
        BashExecResult result = bash.exec("yq -p ini '.section.key' /tmp/test.ini").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("value");
        bash.shutdown();
    }
}
