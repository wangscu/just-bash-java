package com.justbash.ast.expression;

public record ArithGroupNode(int line, ArithExpr expression) implements ArithExpr {
    @Override public String type() { return "ArithGroup"; }
}
