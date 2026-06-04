package com.justbash.ast.expression;

public record ArithTernaryNode(
    int line, ArithExpr condition, ArithExpr consequent, ArithExpr alternate
) implements ArithExpr {
    @Override public String type() { return "ArithTernary"; }
}
