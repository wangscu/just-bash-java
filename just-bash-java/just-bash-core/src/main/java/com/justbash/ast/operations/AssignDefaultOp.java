package com.justbash.ast.operations;

import com.justbash.ast.word.WordNode;

public record AssignDefaultOp(WordNode word, boolean checkEmpty)
    implements ParameterOperation {}
