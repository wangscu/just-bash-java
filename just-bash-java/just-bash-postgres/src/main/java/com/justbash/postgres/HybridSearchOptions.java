package com.justbash.postgres;

import java.util.Optional;

public record HybridSearchOptions(
    Optional<String> path,
    Optional<Double> textWeight,
    Optional<Double> vectorWeight,
    Optional<Integer> limit
) {
    public HybridSearchOptions() {
        this(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public double effectiveTextWeight() {
        return textWeight.orElse(0.4);
    }

    public double effectiveVectorWeight() {
        return vectorWeight.orElse(0.6);
    }
}
