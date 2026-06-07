package com.justbash.postgres;

import javax.sql.DataSource;
import java.util.Optional;

public record PgFileSystemOptions(
    DataSource dataSource,
    long sessionId,
    Optional<EmbeddingProvider> embed,
    Optional<Integer> embeddingDimensions,
    Optional<Long> maxFileSize,
    Optional<Long> statementTimeoutMs
) {
    public PgFileSystemOptions {
        if (!isValidSessionId(sessionId)) {
            throw new IllegalArgumentException(
                "Invalid sessionId: must be a positive integer, got " + sessionId);
        }
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is required");
        }
        embed = embed != null ? embed : Optional.empty();
        embeddingDimensions = embeddingDimensions != null ? embeddingDimensions : Optional.empty();
        maxFileSize = maxFileSize != null ? maxFileSize : Optional.empty();
        statementTimeoutMs = statementTimeoutMs != null ? statementTimeoutMs : Optional.empty();
    }

    private static boolean isValidSessionId(long sessionId) {
        return sessionId >= 1 && sessionId <= Long.MAX_VALUE;
    }
}
