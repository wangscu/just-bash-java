package com.justbash.postgres;

import com.justbash.fs.*;
import org.junit.jupiter.api.*;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RlsTest {

    private DataSource dataSource;

    @BeforeAll
    void beforeAll() {
        dataSource = TestDbHelper.startPostgres();
    }

    @BeforeEach
    void beforeEach() throws SQLException {
        TestDbHelper.resetDb(dataSource);
    }

    // Application-layer isolation

    @Test
    void shouldIsolateSessionsAtApplicationLayer() throws SQLException {
        PgFileSystem fs1 = createFs(1);
        PgFileSystem fs2 = createFs(2);

        fs1.writeFile("/secret.txt", new IFileSystem.StringContent("session1 data"), WriteFileOptions.utf8()).join();
        fs2.writeFile("/other.txt", new IFileSystem.StringContent("session2 data"), WriteFileOptions.utf8()).join();

        assertThat(fs1.exists("/secret.txt").join()).isTrue();
        assertThat(fs1.exists("/other.txt").join()).isFalse();
        assertThat(fs2.exists("/secret.txt").join()).isFalse();
        assertThat(fs2.exists("/other.txt").join()).isTrue();
    }

    @Test
    void shouldIsolateReaddirAcrossSessions() throws SQLException {
        PgFileSystem fs1 = createFs(1);
        PgFileSystem fs2 = createFs(2);

        fs1.writeFile("/a.txt", new IFileSystem.StringContent("a"), WriteFileOptions.utf8()).join();
        fs2.writeFile("/b.txt", new IFileSystem.StringContent("b"), WriteFileOptions.utf8()).join();

        List<String> list1 = fs1.readdir("/").join();
        List<String> list2 = fs2.readdir("/").join();

        assertThat(list1).containsExactly("a.txt");
        assertThat(list2).containsExactly("b.txt");
    }

    @Test
    void shouldIsolateStatAcrossSessions() throws SQLException {
        PgFileSystem fs1 = createFs(1);
        PgFileSystem fs2 = createFs(2);

        fs1.writeFile("/file.txt", new IFileSystem.StringContent("data"), WriteFileOptions.utf8()).join();

        assertThat(fs1.stat("/file.txt").join().isFile()).isTrue();
        assertThatThrownBy(() -> fs2.stat("/file.txt").join())
            .hasCauseInstanceOf(FsError.class)
            .hasMessageContaining("ENOENT");
    }

    @Test
    void shouldIsolateDeleteAcrossSessions() throws SQLException {
        PgFileSystem fs1 = createFs(1);
        PgFileSystem fs2 = createFs(2);

        fs1.writeFile("/file.txt", new IFileSystem.StringContent("data"), WriteFileOptions.utf8()).join();
        fs2.writeFile("/file.txt", new IFileSystem.StringContent("data2"), WriteFileOptions.utf8()).join();

        fs1.rm("/file.txt", new RmOptions(false, false)).join();

        assertThat(fs1.exists("/file.txt").join()).isFalse();
        assertThat(fs2.exists("/file.txt").join()).isTrue();
    }

    @Test
    void shouldIsolateSearchAcrossSessions() throws SQLException {
        PgFileSystem fs1 = createFs(1);
        PgFileSystem fs2 = createFs(2);

        fs1.writeFile("/doc.txt", new IFileSystem.StringContent("keyword here"), WriteFileOptions.utf8()).join();
        fs2.writeFile("/doc.txt", new IFileSystem.StringContent("keyword here"), WriteFileOptions.utf8()).join();

        List<SearchResult> results1 = fs1.search("keyword", new SearchOptions()).join();
        List<SearchResult> results2 = fs2.search("keyword", new SearchOptions()).join();

        assertThat(results1).hasSize(1);
        assertThat(results2).hasSize(1);
        // Each result should point to its own session's file
        assertThat(results1.get(0).path()).isEqualTo("/doc.txt");
        assertThat(results2.get(0).path()).isEqualTo("/doc.txt");
    }

    // Database-level RLS enforcement

    @Test
    void shouldEnforceRlsAtDatabaseLevel() throws SQLException {
        TestDbHelper.createFsAppRole(dataSource);
        PgFileSystem fs1 = createFs(1);
        fs1.writeFile("/secret.txt", new IFileSystem.StringContent("secret"), WriteFileOptions.utf8()).join();

        DataSource appDs = TestDbHelper.createAppDataSource(dataSource);
        PgFileSystem fsApp = createFsApp(appDs, 1);

        // fs_app with correct session_id can read
        assertThat(fsApp.exists("/secret.txt").join()).isTrue();
    }

    @Test
    void shouldBlockCrossSessionAccessAtDatabaseLevel() throws SQLException {
        TestDbHelper.createFsAppRole(dataSource);
        PgFileSystem fs1 = createFs(1);
        fs1.writeFile("/secret.txt", new IFileSystem.StringContent("secret"), WriteFileOptions.utf8()).join();

        DataSource appDs = TestDbHelper.createAppDataSource(dataSource);
        PgFileSystem fsApp = createFsApp(appDs, 2); // wrong session

        assertThat(fsApp.exists("/secret.txt").join()).isFalse();
    }

    @Test
    void shouldBlockInsertForWrongSessionAtDatabaseLevel() throws SQLException {
        TestDbHelper.createFsAppRole(dataSource);
        // Admin sets up schema first
        PgFileSystem fsAdmin = createFs(1);
        fsAdmin.writeFile("/seed.txt", new IFileSystem.StringContent("seed"), WriteFileOptions.utf8()).join();

        DataSource appDs = TestDbHelper.createAppDataSource(dataSource);
        PgFileSystem fsApp = createFsApp(appDs, 1);

        // fs_app can insert into its own session
        fsApp.writeFile("/file.txt", new IFileSystem.StringContent("data"), WriteFileOptions.utf8()).join();
        assertThat(fsApp.exists("/file.txt").join()).isTrue();
    }

    private PgFileSystem createFs(long sessionId) throws SQLException {
        PgFileSystem fs = new PgFileSystem(new PgFileSystemOptions(
            dataSource, sessionId, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
        fs.setup();
        return fs;
    }

    private PgFileSystem createFsApp(DataSource ds, long sessionId) throws SQLException {
        // Schema is already set up by admin user; fs_app just needs to use it
        return new PgFileSystem(new PgFileSystemOptions(
            ds, sessionId, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
    }
}
