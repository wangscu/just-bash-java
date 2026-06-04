package com.justbash.ast.operations;

public sealed interface ParameterOperation
    permits DefaultValueOp, AssignDefaultOp, ErrorIfUnsetOp,
            UseAlternativeOp, LengthOp, SubstringOp,
            PatternRemovalOp, PatternReplacementOp,
            CaseModificationOp, TransformOp,
            IndirectionOp, ArrayKeysOp, VarNamePrefixOp {}
