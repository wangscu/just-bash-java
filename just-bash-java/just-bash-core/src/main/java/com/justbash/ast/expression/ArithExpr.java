package com.justbash.ast.expression;

import com.justbash.ast.ASTNode;

public sealed interface ArithExpr extends ASTNode
    permits ArithNumberNode, ArithVariableNode, ArithSpecialVarNode,
            ArithBinaryNode, ArithUnaryNode, ArithTernaryNode,
            ArithAssignmentNode, ArithGroupNode, ArithNestedNode,
            ArithCommandSubstNode, ArithArrayElementNode {}
