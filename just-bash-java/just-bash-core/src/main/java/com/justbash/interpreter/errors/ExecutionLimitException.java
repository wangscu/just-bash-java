package com.justbash.interpreter.errors;

public final class ExecutionLimitException extends ExecutionException {
    public static final int EXIT_CODE = 125;
    private final String limitType;
    public ExecutionLimitException(String message, String limitType) {
        super(message);
        this.limitType = limitType;
    }
    public String limitType() { return limitType; }
}
