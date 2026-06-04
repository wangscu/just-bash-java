package com.justbash.ast;

import java.util.List;
import java.util.Optional;

public record StatementNode(
    int line,
    List<PipelineNode> pipelines,
    List<StatementOperator> operators,
    boolean background,
    Optional<DeferredError> deferredError,
    Optional<String> sourceText
) implements ASTNode {

    @Override
    public String type() { return "Statement"; }

    public StatementNode(int line, List<PipelineNode> pipelines,
                         List<StatementOperator> operators, boolean background) {
        this(line, pipelines, operators, background, Optional.empty(), Optional.empty());
    }

    public enum StatementOperator { AND, OR, SEMICOLON }

    public record DeferredError(String message, String token) {}
}
