package com.justbash.ast.operations;

import com.justbash.ast.word.WordNode;

public record UseAlternativeOp(WordNode word, boolean checkEmpty)
    implements ParameterOperation {}
