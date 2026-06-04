package com.justbash.ast;

import com.justbash.ast.command.CommandNode;
import java.util.List;
import java.util.Optional;

public record PipelineNode(
    int line,
    List<CommandNode> commands,
    boolean negated,
    boolean timed,
    boolean timePosix,
    Optional<List<Boolean>> pipeStderr
) implements ASTNode {

    @Override
    public String type() { return "Pipeline"; }

    public PipelineNode(int line, List<CommandNode> commands, boolean negated) {
        this(line, commands, negated, false, false, Optional.empty());
    }
}
