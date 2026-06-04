package com.justbash.ast.expression;

import com.justbash.ast.word.WordNode;

public record CondWordNode(int line, WordNode word)
    implements ConditionalExpressionNode {
    @Override public String type() { return "CondWord"; }
}
