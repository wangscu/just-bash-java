package com.justbash.ast.command;

import com.justbash.ast.ASTNode;
import com.justbash.ast.AssignmentNode;
import com.justbash.ast.RedirectionNode;
import com.justbash.ast.word.WordNode;
import java.util.List;

public record SimpleCommandNode(
    int line,
    WordNode name,
    List<WordNode> args,
    List<AssignmentNode> assignments,
    List<RedirectionNode> redirections
) implements CommandNode {

    @Override
    public String type() { return "SimpleCommand"; }
}
