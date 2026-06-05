package com.justbash.ast;

import com.justbash.ast.word.WordNode;
import java.util.List;
import java.util.Optional;

public record AssignmentNode(
    int line,
    String name,
    Optional<WordNode> value,
    boolean append,
    Optional<List<WordNode>> array,
    Optional<WordNode> arrayIndex
) implements ASTNode {

    @Override
    public String type() { return "Assignment"; }

    public static AssignmentNode ofSimple(int line, String name, WordNode value) {
        return new AssignmentNode(line, name, Optional.of(value), false, Optional.empty(), Optional.empty());
    }

    public static AssignmentNode ofArray(int line, String name, List<WordNode> elements) {
        return new AssignmentNode(line, name, Optional.empty(), false, Optional.of(elements), Optional.empty());
    }

    public static AssignmentNode ofIndexed(int line, String name, WordNode index, WordNode value) {
        return new AssignmentNode(line, name, Optional.of(value), false, Optional.empty(), Optional.of(index));
    }
}
