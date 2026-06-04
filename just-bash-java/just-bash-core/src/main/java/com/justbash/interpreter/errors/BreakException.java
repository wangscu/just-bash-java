package com.justbash.interpreter.errors;

public final class BreakException extends ExecutionException {
    private final int levels;
    public BreakException(int levels) {
        super("break");
        this.levels = levels;
    }
    public int levels() { return levels; }
}
