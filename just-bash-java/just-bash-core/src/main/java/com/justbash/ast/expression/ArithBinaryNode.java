package com.justbash.ast.expression;

public record ArithBinaryNode(
    int line, String operator, ArithExpr left, ArithExpr right
) implements ArithExpr {
    @Override public String type() { return "ArithBinary"; }
}
