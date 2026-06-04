package com.justbash.ast.command;

import com.justbash.ast.ASTNode;
import com.justbash.ast.RedirectionNode;
import com.justbash.ast.StatementNode;
import com.justbash.ast.word.WordNode;
import java.util.List;
import java.util.Optional;

public record ForNode(
    int line,
    String variable,
    Optional<List<WordNode>> words,
    List<StatementNode> body,
    List<RedirectionNode> redirections
) implements CompoundCommandNode {

    @Override
    public String type() { return "For"; }
}
