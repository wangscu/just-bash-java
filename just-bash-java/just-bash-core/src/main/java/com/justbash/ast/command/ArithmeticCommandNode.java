package com.justbash.ast.command;

import com.justbash.ast.ASTNode;
import com.justbash.ast.RedirectionNode;
import com.justbash.ast.expression.ArithmeticExpressionNode;
import java.util.List;

public record ArithmeticCommandNode(
    int line,
    ArithmeticExpressionNode expression,
    List<RedirectionNode> redirections
) implements CompoundCommandNode {

    @Override
    public String type() { return "ArithmeticCommand"; }
}
