package com.justbash.ast.expression;

public record CondGroupNode(int line, ConditionalExpressionNode expression)
    implements ConditionalExpressionNode {
    @Override public String type() { return "CondGroup"; }
}
