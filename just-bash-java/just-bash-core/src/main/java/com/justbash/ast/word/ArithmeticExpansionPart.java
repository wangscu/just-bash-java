package com.justbash.ast.word;

import com.justbash.ast.expression.ArithmeticExpressionNode;

public record ArithmeticExpansionPart(
    int line, ArithmeticExpressionNode expression
) implements WordPart {
    @Override public String type() { return "ArithmeticExpansion"; }
}
