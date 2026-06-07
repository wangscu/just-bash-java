package com.justbash.postgres;

import com.justbash.Bash;
import com.justbash.BashExecResult;
import com.justbash.BashOptions;
import com.justbash.commands.CommandRegistry;
import org.junit.jupiter.api.*;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntegrationTest {

    private DataSource dataSource;
    private Bash bash;

    @BeforeAll
    void beforeAll() {
        dataSource = TestDbHelper.startPostgres();
    }

    @BeforeEach
    void beforeEach() throws SQLException {
        TestDbHelper.resetDb(dataSource);
        PgFileSystem fs = new PgFileSystem(new PgFileSystemOptions(
            dataSource, 1, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
        fs.setup();

        BashOptions options = new BashOptions(
            Optional.empty(), Optional.of("/"), Optional.of(fs),
            Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty()
        );
        bash = new Bash(options);
        CommandRegistry.registerAll(bash);
    }

    @AfterEach
    void afterEach() {
        if (bash != null) bash.shutdown();
    }

    @Test
    void shouldEchoToFile() {
        BashExecResult result = bash.exec("echo hello > /test.txt").join();
        assertThat(result.exitCode()).isEqualTo(0);
        BashExecResult catResult = bash.exec("cat /test.txt").join();
        assertThat(catResult.stdout().trim()).isEqualTo("hello");
    }

    @Test
    void shouldCatFile() {
        bash.exec("echo 'line1\nline2' > /doc.txt").join();
        BashExecResult result = bash.exec("cat /doc.txt").join();
        assertThat(result.stdout()).isEqualTo("line1\nline2\n");
    }

    @Test
    void shouldMkdirAndLs() {
        bash.exec("mkdir -p /a/b/c").join();
        BashExecResult result = bash.exec("ls /a/b").join();
        assertThat(result.stdout().trim()).isEqualTo("c");
    }

    @Test
    void shouldRmFile() {
        bash.exec("echo test > /del.txt").join();
        BashExecResult rmResult = bash.exec("rm /del.txt").join();
        assertThat(rmResult.exitCode()).isEqualTo(0);
        BashExecResult lsResult = bash.exec("ls /").join();
        assertThat(lsResult.stdout().trim()).isEmpty();
    }

    @Test
    void shouldMvFile() {
        bash.exec("echo content > /old.txt").join();
        bash.exec("mv /old.txt /new.txt").join();
        BashExecResult result = bash.exec("cat /new.txt").join();
        assertThat(result.stdout().trim()).isEqualTo("content");
    }

    @Test
    void shouldCpFile() {
        bash.exec("echo data > /src.txt").join();
        bash.exec("cp /src.txt /dst.txt").join();
        BashExecResult result = bash.exec("cat /dst.txt").join();
        assertThat(result.stdout().trim()).isEqualTo("data");
    }
}
