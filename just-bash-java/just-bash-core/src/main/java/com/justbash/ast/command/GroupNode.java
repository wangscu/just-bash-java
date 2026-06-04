package com.justbash.ast.command;

import com.justbash.ast.ASTNode;
import com.justbash.ast.RedirectionNode;
import com.justbash.ast.StatementNode;
import java.util.List;

public record GroupNode(
    int line,
    List<StatementNode> body,
    List<RedirectionNode> redirections
) implements CompoundCommandNode {

    @Override
    public String type() { return "Group"; }
}
