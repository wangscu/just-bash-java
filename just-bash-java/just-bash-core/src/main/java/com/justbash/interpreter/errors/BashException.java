package com.justbash.interpreter.errors;

public sealed class BashException extends RuntimeException
    permits ParseException, LexerException, ExecutionException {

    public BashException(String message) { super(message); }
    public BashException(String message, Throwable cause) { super(message, cause); }
}
