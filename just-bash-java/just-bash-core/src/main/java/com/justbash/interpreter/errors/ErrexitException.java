package com.justbash.interpreter.errors;

public final class ErrexitException extends ExecutionException {
    public ErrexitException(String stdout, String stderr) {
        super("errexit", stdout, stderr);
    }
}
