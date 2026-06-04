package com.justbash.ast.operations;

public record TransformOp(Operator operator) implements ParameterOperation {
    public enum Operator { Q, P, a, A, E, K, k, u, U, L }
}
