package com.justbash.ast.expression;

public record ArithSpecialVarNode(int line, String name) implements ArithExpr {
    @Override public String type() { return "ArithSpecialVar"; }
}
