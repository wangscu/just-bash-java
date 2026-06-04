package com.justbash.interpreter.errors;

public final class ReturnException extends ExecutionException {
    private final int exitCode;
    public ReturnException(int exitCode) {
        super("return " + exitCode);
        this.exitCode = exitCode;
    }
    public int exitCode() { return exitCode; }
}
