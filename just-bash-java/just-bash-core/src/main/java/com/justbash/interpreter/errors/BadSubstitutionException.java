package com.justbash.interpreter.errors;

public final class BadSubstitutionException extends ExecutionException {
    public BadSubstitutionException(String message, String stdout, String stderr) {
        super(message, stdout, stderr);
    }
}
