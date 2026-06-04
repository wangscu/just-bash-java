package com.justbash.ast.command;

import com.justbash.ast.ASTNode;
import com.justbash.ast.RedirectionNode;
import com.justbash.ast.StatementNode;
import com.justbash.ast.word.WordNode;
import java.util.List;

public record CaseNode(
    int line,
    WordNode word,
    List<CaseItemNode> items,
    List<RedirectionNode> redirections
) implements CompoundCommandNode {

    @Override
    public String type() { return "Case"; }

    public record CaseItemNode(int line, List<WordNode> patterns,
                               List<StatementNode> body,
                               Terminator terminator) implements ASTNode {
        @Override public String type() { return "CaseItem"; }
        public enum Terminator { DSEMI, SEMIAMP, DSEMIAMP }
    }
}
