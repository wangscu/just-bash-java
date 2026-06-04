package com.justbash.ast.expression;

public record CondNotNode(int line, ConditionalExpressionNode operand)
    implements ConditionalExpressionNode {
    @Override public String type() { return "CondNot"; }
}
