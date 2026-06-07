package com.justbash.commands.xan;

import com.justbash.Bash;
import com.justbash.BashExecResult;
import com.justbash.BashOptions;
import com.justbash.fs.IFileSystem;
import com.justbash.fs.InMemoryFs;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class XanCommandTest {

    private Bash createBash() {
        InMemoryFs fs = new InMemoryFs();
        Bash bash = new Bash(new BashOptions(
            Optional.empty(), Optional.empty(),
            Optional.of(fs), Optional.empty(),
            Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty()
        ));
        bash.registerCommand(new XanCommand());
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
        bash.registerCommand(new XanCommand());
        return bash;
    }

    @Test
    void helpText() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("xan --help").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("xan - CSV toolkit");
        bash.shutdown();
    }

    @Test
    void countRows() {
        Bash bash = createBashWithFile("/users.csv", "name,age\nalice,30\nbob,25\ncharlie,35\ndiana,28\n");
        BashExecResult result = bash.exec("xan count /users.csv").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout().trim()).isEqualTo("4");
        bash.shutdown();
    }

    @Test
    void countEmptyFile() {
        Bash bash = createBashWithFile("/empty.csv", "name,age\n");
        BashExecResult result = bash.exec("xan count /empty.csv").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout().trim()).isEqualTo("0");
        bash.shutdown();
    }

    @Test
    void headersWithIndices() {
        Bash bash = createBashWithFile("/users.csv", "name,age,email,active\nalice,30,alice@example.com,true\n");
        BashExecResult result = bash.exec("xan headers /users.csv").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).isEqualTo("0   name\n1   age\n2   email\n3   active\n");
        bash.shutdown();
    }

    @Test
    void headersJustNames() {
        Bash bash = createBashWithFile("/users.csv", "name,age\nalice,30\n");
        BashExecResult result = bash.exec("xan headers -j /users.csv").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).isEqualTo("name\nage\n");
        bash.shutdown();
    }

    @Test
    void headDefault() {
        Bash bash = createBashWithFile("/users.csv", "name,age\nalice,30\nbob,25\ncharlie,35\ndiana,28\n");
        BashExecResult result = bash.exec("xan head /users.csv").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).isEqualTo("name,age\nalice,30\nbob,25\ncharlie,35\ndiana,28\n");
        bash.shutdown();
    }

    @Test
    void headWithLimit() {
        Bash bash = createBashWithFile("/users.csv", "name,age\nalice,30\nbob,25\ncharlie,35\n");
        BashExecResult result = bash.exec("xan head -l 2 /users.csv").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).isEqualTo("name,age\nalice,30\nbob,25\n");
        bash.shutdown();
    }

    @Test
    void tailWithLimit() {
        Bash bash = createBashWithFile("/users.csv", "name,age\nalice,30\nbob,25\ncharlie,35\ndiana,28\n");
        BashExecResult result = bash.exec("xan tail -l 2 /users.csv").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).isEqualTo("name,age\ncharlie,35\ndiana,28\n");
        bash.shutdown();
    }

    @Test
    void slice() {
        Bash bash = createBashWithFile("/users.csv", "name,age\nalice,30\nbob,25\ncharlie,35\ndiana,28\n");
        BashExecResult result = bash.exec("xan slice -s 1 -e 3 /users.csv").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).isEqualTo("name,age\nbob,25\ncharlie,35\n");
        bash.shutdown();
    }

    @Test
    void reverse() {
        Bash bash = createBashWithFile("/numbers.csv", "n\n1\n2\n3\n4\n5\n");
        BashExecResult result = bash.exec("xan reverse /numbers.csv").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).isEqualTo("n\n5\n4\n3\n2\n1\n");
        bash.shutdown();
    }

    @Test
    void behead() {
        Bash bash = createBashWithFile("/data.csv", "name,age\nalice,30\nbob,25\n");
        BashExecResult result = bash.exec("xan behead /data.csv").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).isEqualTo("alice,30\nbob,25\n");
        bash.shutdown();
    }

    @Test
    void selectColumns() {
        Bash bash = createBashWithFile("/users.csv", "name,age,email\nalice,30,alice@test.com\nbob,25,bob@test.com\n");
        BashExecResult result = bash.exec("xan select name,email /users.csv").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).isEqualTo("name,email\nalice,alice@test.com\nbob,bob@test.com\n");
        bash.shutdown();
    }

    @Test
    void dropColumns() {
        Bash bash = createBashWithFile("/users.csv", "name,age,email\nalice,30,alice@test.com\nbob,25,bob@test.com\n");
        BashExecResult result = bash.exec("xan drop age /users.csv").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).isEqualTo("name,email\nalice,alice@test.com\nbob,bob@test.com\n");
        bash.shutdown();
    }

    @Test
    void renameColumns() {
        Bash bash = createBashWithFile("/users.csv", "name,age\nalice,30\nbob,25\n");
        BashExecResult result = bash.exec("xan rename full_name,years /users.csv").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).isEqualTo("full_name,years\nalice,30\nbob,25\n");
        bash.shutdown();
    }

    @Test
    void enumColumn() {
        Bash bash = createBashWithFile("/numbers.csv", "n\n1\n2\n3\n");
        BashExecResult result = bash.exec("xan enum /numbers.csv").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).isEqualTo("index,n\n0,1\n1,2\n2,3\n");
        bash.shutdown();
    }

    @Test
    void sortNumeric() {
        Bash bash = createBashWithFile("/data.csv", "name,score\ncharlie,35\nalice,10\nbob,25\n");
        BashExecResult result = bash.exec("xan sort -s score -N /data.csv").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).isEqualTo("name,score\nalice,10\nbob,25\ncharlie,35\n");
        bash.shutdown();
    }

    @Test
    void dedup() {
        Bash bash = createBashWithFile("/data.csv", "name,age\nalice,30\nbob,25\nalice,30\n");
        BashExecResult result = bash.exec("xan dedup /data.csv").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).isEqualTo("name,age\nalice,30\nbob,25\n");
        bash.shutdown();
    }

    @Test
    void searchRegex() {
        Bash bash = createBashWithFile("/users.csv", "name,email\nalice,alice@test.com\nbob,bob@example.com\n");
        BashExecResult result = bash.exec("xan search test /users.csv").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).isEqualTo("name,email\nalice,alice@test.com\n");
        bash.shutdown();
    }

    @Test
    void filterWithExpression() {
        Bash bash = createBashWithFile("/users.csv", "name,age\nalice,30\nbob,25\ncharlie,35\n");
        BashExecResult result = bash.exec("xan filter '.age > 25' /users.csv").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).isEqualTo("name,age\nalice,30\ncharlie,35\n");
        bash.shutdown();
    }

    @Test
    void toJson() {
        Bash bash = createBashWithFile("/users.csv", "name,age\nalice,30\nbob,25\n");
        BashExecResult result = bash.exec("xan to json /users.csv").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("alice");
        assertThat(result.stdout()).contains("30");
        bash.shutdown();
    }

    @Test
    void transpose() {
        Bash bash = createBashWithFile("/data.csv", "a,b,c\n1,2,3\n4,5,6\n");
        BashExecResult result = bash.exec("xan transpose /data.csv").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).isNotEmpty();
        bash.shutdown();
    }

    @Test
    void missingFileError() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("xan count /nonexistent.csv").join();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr()).contains("No such file");
        bash.shutdown();
    }

    @Test
    void unknownCommandError() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("xan foobar /data.csv").join();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr()).contains("unknown command");
        bash.shutdown();
    }
}
