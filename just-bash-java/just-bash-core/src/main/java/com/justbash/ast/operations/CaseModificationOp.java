package com.justbash.ast.operations;

import com.justbash.ast.word.WordNode;
import java.util.Optional;

public record CaseModificationOp(
    Direction direction, boolean all, Optional<WordNode> pattern)
    implements ParameterOperation {
    public enum Direction { UPPER, LOWER }
}
