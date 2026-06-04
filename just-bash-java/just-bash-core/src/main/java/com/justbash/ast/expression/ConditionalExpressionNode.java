package com.justbash.ast.expression;

import com.justbash.ast.ASTNode;

public sealed interface ConditionalExpressionNode extends ASTNode
    permits CondBinaryNode, CondUnaryNode, CondNotNode,
            CondAndNode, CondOrNode, CondGroupNode, CondWordNode {}
