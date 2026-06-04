package com.justbash.ast.expression;

public record ArithNumberNode(int line, int value) implements ArithExpr {
    @Override public String type() { return "ArithNumber"; }
}
