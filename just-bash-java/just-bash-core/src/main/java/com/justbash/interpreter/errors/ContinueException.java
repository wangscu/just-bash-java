package com.justbash.interpreter.errors;

public final class ContinueException extends ExecutionException {
    private final int levels;
    public ContinueException(int levels) {
        super("continue");
        this.levels = levels;
    }
    public int levels() { return levels; }
}
