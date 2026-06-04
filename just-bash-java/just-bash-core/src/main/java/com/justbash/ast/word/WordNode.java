package com.justbash.ast.word;

import com.justbash.ast.ASTNode;
import java.util.List;

public record WordNode(int line, List<WordPart> parts) implements ASTNode {
    @Override
    public String type() { return "Word"; }
}
