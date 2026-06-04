package com.justbash.ast.expression;

import com.justbash.ast.word.WordNode;

public record CondUnaryNode(
    int line, String operator, WordNode operand
) implements ConditionalExpressionNode {
    @Override public String type() { return "CondUnary"; }
}
