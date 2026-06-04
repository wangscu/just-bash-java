package com.justbash.interpreter.errors;

public final class ExitException extends ExecutionException {
    private final int exitCode;
    public ExitException(int exitCode, String stdout, String stderr) {
        super("exit " + exitCode, stdout, stderr);
        this.exitCode = exitCode;
    }
    public int exitCode() { return exitCode; }
}
