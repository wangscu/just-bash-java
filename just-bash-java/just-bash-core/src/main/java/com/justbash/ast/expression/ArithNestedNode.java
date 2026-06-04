package com.justbash.ast.expression;

public record ArithNestedNode(int line, ArithExpr expression) implements ArithExpr {
    @Override public String type() { return "ArithNested"; }
}
