package com.justbash.ast.command;

import com.justbash.ast.ASTNode;
import com.justbash.ast.RedirectionNode;
import com.justbash.ast.expression.ConditionalExpressionNode;
import java.util.List;

public record ConditionalCommandNode(
    int line,
    ConditionalExpressionNode expression,
    List<RedirectionNode> redirections
) implements CompoundCommandNode {

    @Override
    public String type() { return "ConditionalCommand"; }
}
