package com.justbash.ast.operations;

public record VarNamePrefixOp(String prefix, boolean star)
    implements ParameterOperation {}
