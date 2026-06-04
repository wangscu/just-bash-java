package com.justbash.ast.word;

import com.justbash.ast.operations.ParameterOperation;
import java.util.Optional;

public record ParameterExpansionPart(
    int line,
    String parameter,
    Optional<ParameterOperation> operation
) implements WordPart {
    @Override public String type() { return "ParameterExpansion"; }
}
