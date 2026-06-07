package com.justbash.postgres;

import java.util.Optional;

public record SearchOptions(
    Optional<String> path,
    Optional<Integer> limit
) {
    public SearchOptions() {
        this(Optional.empty(), Optional.empty());
    }

    public SearchOptions withPath(String path) {
        return new SearchOptions(Optional.of(path), this.limit);
    }

    public SearchOptions withLimit(int limit) {
        return new SearchOptions(this.path, Optional.of(limit));
    }
}
