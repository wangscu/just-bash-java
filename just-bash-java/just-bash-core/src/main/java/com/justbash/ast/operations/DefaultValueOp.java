package com.justbash.ast.operations;

import com.justbash.ast.word.WordNode;

public record DefaultValueOp(WordNode word, boolean checkEmpty)
    implements ParameterOperation {}
