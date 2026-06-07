package com.justbash.postgres;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class TestDbHelper {

    private static PostgreSQLContainer<?> container;

    public static synchronized DataSource startPostgres() {
        String dbUrl = System.getenv("TEST_DATABASE_URL");
        if (dbUrl != null && !dbUrl.isEmpty()) {
            org.postgresql.ds.PGSimpleDataSource ds = new org.postgresql.ds.PGSimpleDataSource();
            ds.setUrl(dbUrl);
            return ds;
        }

        if (container == null) {
            container = new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg17")
                    .asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("just_bash_postgres_test")
                .withUsername("postgres")
                .withPassword("postgres");
            container.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (container != null) container.stop();
            }));
        }

        org.postgresql.ds.PGSimpleDataSource ds = new org.postgresql.ds.PGSimpleDataSource();
        ds.setUrl(container.getJdbcUrl());
        ds.setUser(container.getUsername());
        ds.setPassword(container.getPassword());
        return ds;
    }

    public static void resetDb(DataSource ds) throws SQLException {
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS fs_nodes CASCADE");
        }
    }

    public static void createFsAppRole(DataSource ds) throws SQLException {
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DO $$ BEGIN CREATE ROLE fs_app LOGIN; EXCEPTION WHEN duplicate_object THEN NULL; END $$");
            stmt.execute("GRANT USAGE, CREATE ON SCHEMA public TO fs_app");
        }
    }

    public static DataSource createAppDataSource(DataSource adminDs) throws SQLException {
        String url;
        try (Connection conn = adminDs.getConnection()) {
            url = conn.getMetaData().getURL();
        }
        org.postgresql.ds.PGSimpleDataSource ds = new org.postgresql.ds.PGSimpleDataSource();
        ds.setUrl(url);
        ds.setUser("fs_app");
        ds.setPassword("");
        return ds;
    }
}
