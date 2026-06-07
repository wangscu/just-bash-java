package com.justbash.postgres;

import java.util.Optional;

public record SearchResult(
    String path,
    String name,
    double rank,
    Optional<String> snippet
) {
    public SearchResult(String path, String name, double rank) {
        this(path, name, rank, Optional.empty());
    }
}
