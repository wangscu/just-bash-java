package com.justbash.ast.operations;

import java.util.Optional;

public record IndirectionOp(Optional<InnerParameterOperation> innerOp)
    implements ParameterOperation {}
