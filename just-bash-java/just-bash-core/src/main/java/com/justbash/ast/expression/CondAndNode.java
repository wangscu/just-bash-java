package com.justbash.ast.expression;

public record CondAndNode(
    int line, ConditionalExpressionNode left, ConditionalExpressionNode right
) implements ConditionalExpressionNode {
    @Override public String type() { return "CondAnd"; }
}
