package com.justbash.postgres;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

public final class PgSchema {
    private static final int MAX_VECTOR_DIMENSIONS = 16000;

    private PgSchema() {}

    public static void setupSchema(DataSource dataSource) throws SQLException {
        String migration = loadResource("sql/001-setup.sql");
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(migration);
        }
    }

    public static void setupVectorColumn(DataSource dataSource, int dimensions) throws SQLException {
        if (dimensions < 1 || dimensions > MAX_VECTOR_DIMENSIONS) {
            throw new IllegalArgumentException(
                "Invalid vector dimensions: " + dimensions + " (must be integer between 1 and " + MAX_VECTOR_DIMENSIONS + ")");
        }
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE EXTENSION IF NOT EXISTS vector");
            stmt.execute("""
                DO $$ BEGIN
                    ALTER TABLE fs_nodes ADD COLUMN embedding vector(%d);
                EXCEPTION WHEN duplicate_column THEN
                    NULL;
                END $$""".formatted(dimensions));
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_fs_embedding ON fs_nodes
                USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64)""");
        }
    }

    private static String loadResource(String path) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                PgSchema.class.getClassLoader().getResourceAsStream(path), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load resource: " + path, e);
        }
    }
}
