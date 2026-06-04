package com.justbash.ast.operations;

public record ArrayKeysOp(String array, boolean star)
    implements ParameterOperation {}
