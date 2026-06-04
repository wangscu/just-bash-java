package com.justbash.ast.command;

public sealed interface CompoundCommandNode extends CommandNode
    permits IfNode, ForNode, WhileNode, CaseNode,
            SubshellNode, GroupNode,
            ArithmeticCommandNode, ConditionalCommandNode {}
