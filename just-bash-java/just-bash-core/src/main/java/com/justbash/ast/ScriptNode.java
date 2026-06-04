package com.justbash.ast;

import java.util.List;

public record ScriptNode(int line, List<StatementNode> statements)
    implements ASTNode {

    @Override
    public String type() { return "Script"; }
}
