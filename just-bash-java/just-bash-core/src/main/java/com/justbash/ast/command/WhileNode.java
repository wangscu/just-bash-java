package com.justbash.ast.command;

import com.justbash.ast.ASTNode;
import com.justbash.ast.RedirectionNode;
import com.justbash.ast.StatementNode;
import java.util.List;

public record WhileNode(
    int line,
    List<StatementNode> condition,
    List<StatementNode> body,
    List<RedirectionNode> redirections
) implements CompoundCommandNode {

    @Override
    public String type() { return "While"; }
}
