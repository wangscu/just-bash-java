package com.justbash.ast.word;

import com.justbash.ast.ASTNode;

public sealed interface WordPart extends ASTNode
    permits LiteralPart, SingleQuotedPart, DoubleQuotedPart,
            EscapedPart, ParameterExpansionPart,
            CommandSubstitutionPart, ArithmeticExpansionPart,
            ProcessSubstitutionPart, BraceExpansionPart,
            TildeExpansionPart, GlobPart {}
