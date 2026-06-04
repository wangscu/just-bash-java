package com.justbash.interpreter.errors;

public final class ExecutionAbortedException extends ExecutionException {
    public ExecutionAbortedException(String stdout, String stderr) {
        super("Execution aborted", stdout, stderr);
    }
}
