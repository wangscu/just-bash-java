package com.justbash.ast.expression;

public record ArithUnaryNode(
    int line, String operator, ArithExpr operand, boolean prefix
) implements ArithExpr {
    @Override public String type() { return "ArithUnary"; }
}
