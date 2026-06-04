package com.justbash.ast.expression;

import com.justbash.ast.word.WordNode;

public record CondBinaryNode(
    int line, String operator, WordNode left, WordNode right
) implements ConditionalExpressionNode {
    @Override public String type() { return "CondBinary"; }
}
