package com.justbash.ast;

import com.justbash.ast.word.WordNode;
import java.util.Optional;

public record RedirectionNode(
    int line,
    Optional<Integer> fd,
    Optional<String> fdVariable,
    RedirectionOperator operator,
    WordOrHereDoc target
) implements ASTNode {

    @Override
    public String type() { return "Redirection"; }

    public enum RedirectionOperator {
        LT, GT, GTGT, GTAMP, LTAMP, LTGT, GTPipe, AMPGT, AMPGTGT,
        HERESTRING, HEREDOC, HEREDOC_STRIP
    }

    public sealed interface WordOrHereDoc
        permits WordTarget, HereDocTarget {}
    public record WordTarget(WordNode word) implements WordOrHereDoc {}
    public record HereDocTarget(HereDocNode hereDoc) implements WordOrHereDoc {}
}
