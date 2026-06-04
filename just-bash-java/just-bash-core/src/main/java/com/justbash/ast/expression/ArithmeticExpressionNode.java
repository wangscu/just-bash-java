package com.justbash.ast.expression;

import com.justbash.ast.ASTNode;
import java.util.Optional;

public record ArithmeticExpressionNode(
    int line,
    ArithExpr expression,
    Optional<String> originalText
) implements ASTNode {

    @Override
    public String type() { return "ArithmeticExpression"; }
}
