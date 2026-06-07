package com.justbash.commands.queryengine;

import java.util.ArrayList;
import java.util.List;

/**
 * Exception thrown when a break statement is encountered in a labeled block.
 */
public class BreakException extends RuntimeException {
    private final String label;
    private final List<Object> partialResults;

    public BreakException(String label) {
        this(label, List.of());
    }

    public BreakException(String label, List<Object> partialResults) {
        super("break " + label);
        this.label = label;
        this.partialResults = partialResults;
    }

    public String getLabel() {
        return label;
    }

    public List<Object> getPartialResults() {
        return partialResults;
    }

    public BreakException withPrependedResults(List<Object> results) {
        List<Object> combined = new ArrayList<>(results.size() + partialResults.size());
        combined.addAll(results);
        combined.addAll(partialResults);
        return new BreakException(label, combined);
    }
}
