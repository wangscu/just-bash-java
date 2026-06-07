package com.justbash.postgres;

import org.junit.jupiter.api.*;

import javax.sql.DataSource;
import java.sql.*;

import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchemaTest {

    private DataSource dataSource;

    @BeforeAll
    void beforeAll() {
        dataSource = TestDbHelper.startPostgres();
    }

    @BeforeEach
    void beforeEach() throws SQLException {
        TestDbHelper.resetDb(dataSource);
    }

    @Test
    void shouldCreateFsNodesTable() throws SQLException {
        PgSchema.setupSchema(dataSource);
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getColumns(null, null, "fs_nodes", null)) {
            int count = 0;
            while (rs.next()) count++;
            assertThat(count).isGreaterThanOrEqualTo(10);
        }
    }

    @Test
    void shouldCreateLtreeExtension() throws SQLException {
        PgSchema.setupSchema(dataSource);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1 FROM pg_extension WHERE extname = 'ltree'")) {
            assertThat(rs.next()).isTrue();
        }
    }

    @Test
    void shouldEnableRls() throws SQLException {
        PgSchema.setupSchema(dataSource);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT relrowsecurity, relforcerowsecurity FROM pg_class WHERE relname = 'fs_nodes'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean(1)).isTrue();
            assertThat(rs.getBoolean(2)).isTrue();
        }
    }

    @Test
    void shouldBeIdempotent() throws SQLException {
        PgSchema.setupSchema(dataSource);
        PgSchema.setupSchema(dataSource);
        // Should not throw
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, null, "fs_nodes", null)) {
            assertThat(rs.next()).isTrue();
        }
    }

    @Test
    void shouldSetupVectorColumn() throws SQLException {
        PgSchema.setupSchema(dataSource);
        PgSchema.setupVectorColumn(dataSource, 384);
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getColumns(null, null, "fs_nodes", "embedding")) {
            assertThat(rs.next()).isTrue();
        }
    }

    @Test
    void shouldRejectInvalidVectorDimensions() {
        assertThatThrownBy(() -> PgSchema.setupVectorColumn(dataSource, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid vector dimensions");
    }
}
