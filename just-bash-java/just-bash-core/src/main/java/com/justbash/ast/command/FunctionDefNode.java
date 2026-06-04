package com.justbash.ast.command;

import com.justbash.ast.ASTNode;
import com.justbash.ast.RedirectionNode;
import java.util.List;
import java.util.Optional;

public record FunctionDefNode(
    int line,
    String name,
    CompoundCommandNode body,
    List<RedirectionNode> redirections,
    Optional<String> sourceFile
) implements CommandNode {

    @Override
    public String type() { return "FunctionDef"; }
}
