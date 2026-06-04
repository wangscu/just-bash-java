package com.justbash.security;

public record ExecutionLimits(
    int maxCallDepth,
    int maxCommandCount,
    int maxLoopIterations,
    int maxOutputSize,
    int maxHeredocSize
) {
    public static ExecutionLimits defaults() {
        return new ExecutionLimits(
            100,
            10000,
            100000,
            10 * 1024 * 1024,
            64 * 1024
        );
    }
}
