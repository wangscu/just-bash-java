package com.justbash.ast.expression;

public record ArithVariableNode(int line, String name) implements ArithExpr {
    @Override public String type() { return "ArithVariable"; }
}
