package com.justbash.ast.command;

import com.justbash.ast.ASTNode;

public sealed interface CommandNode extends ASTNode
    permits SimpleCommandNode, CompoundCommandNode, FunctionDefNode {}
