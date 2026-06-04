package com.justbash.ast.expression;

public record ArithArrayElementNode(int line, String array, ArithExpr index)
    implements ArithExpr {
    @Override public String type() { return "ArithArrayElement"; }
}
