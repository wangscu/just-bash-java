package com.justbash.ast;

import com.justbash.ast.word.WordNode;
import java.util.List;
import java.util.Optional;

public record AssignmentNode(
    int line,
    String name,
    Optional<WordNode> value,
    boolean append,
    Optional<List<WordNode>> array
) implements ASTNode {

    @Override
    public String type() { return "Assignment"; }
}
