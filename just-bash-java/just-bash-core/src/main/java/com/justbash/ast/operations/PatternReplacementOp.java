package com.justbash.ast.operations;

import com.justbash.ast.word.WordNode;
import java.util.Optional;

public record PatternReplacementOp(
    WordNode pattern, Optional<WordNode> replacement,
    boolean all, Optional<Anchor> anchor) implements ParameterOperation {
    public enum Anchor { START, END }
}
