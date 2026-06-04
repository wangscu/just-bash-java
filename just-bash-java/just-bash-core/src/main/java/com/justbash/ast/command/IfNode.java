package com.justbash.ast.command;

import com.justbash.ast.ASTNode;
import com.justbash.ast.RedirectionNode;
import com.justbash.ast.StatementNode;
import java.util.List;

public record IfNode(
    int line,
    List<IfClause> clauses,
    List<StatementNode> elseBody,
    List<RedirectionNode> redirections
) implements CompoundCommandNode {

    @Override
    public String type() { return "If"; }

    public record IfClause(List<StatementNode> condition,
                           List<StatementNode> body) {}
}
