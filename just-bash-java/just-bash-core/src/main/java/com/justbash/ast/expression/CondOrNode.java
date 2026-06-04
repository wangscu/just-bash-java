package com.justbash.ast.expression;

public record CondOrNode(
    int line, ConditionalExpressionNode left, ConditionalExpressionNode right
) implements ConditionalExpressionNode {
    @Override public String type() { return "CondOr"; }
}
