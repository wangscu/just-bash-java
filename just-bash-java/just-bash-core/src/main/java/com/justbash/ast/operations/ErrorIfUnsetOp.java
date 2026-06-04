package com.justbash.ast.operations;

import com.justbash.ast.word.WordNode;
import java.util.Optional;

public record ErrorIfUnsetOp(Optional<WordNode> word, boolean checkEmpty)
    implements ParameterOperation {}
