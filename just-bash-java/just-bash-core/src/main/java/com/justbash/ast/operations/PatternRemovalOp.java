package com.justbash.ast.operations;

import com.justbash.ast.word.WordNode;

public record PatternRemovalOp(WordNode pattern, PatternSide side, boolean greedy)
    implements ParameterOperation {
    public enum PatternSide { PREFIX, SUFFIX }
}
