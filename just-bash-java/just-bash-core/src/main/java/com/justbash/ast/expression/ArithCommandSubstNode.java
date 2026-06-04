package com.justbash.ast.expression;

public record ArithCommandSubstNode(int line, String command) implements ArithExpr {
    @Override public String type() { return "ArithCommandSubst"; }
}
