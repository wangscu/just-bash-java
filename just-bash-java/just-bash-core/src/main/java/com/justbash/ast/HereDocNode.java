package com.justbash.ast;

import com.justbash.ast.word.WordNode;

public record HereDocNode(
    int line,
    String delimiter,
    WordNode content,
    boolean stripTabs,
    boolean quoted
) implements ASTNode {

    @Override
    public String type() { return "HereDoc"; }
}
