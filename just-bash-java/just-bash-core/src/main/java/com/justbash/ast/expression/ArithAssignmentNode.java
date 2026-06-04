package com.justbash.ast.expression;

public record ArithAssignmentNode(
    int line, String operator, String variable, ArithExpr value
) implements ArithExpr {
    @Override public String type() { return "ArithAssignment"; }
}
