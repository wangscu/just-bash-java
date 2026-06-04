package com.justbash.ast.operations;

import com.justbash.ast.expression.ArithExpr;
import java.util.Optional;

public record SubstringOp(ArithExpr offset, Optional<ArithExpr> length)
    implements ParameterOperation {}
