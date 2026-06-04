package com.justbash.interpreter.errors;

public final class ParseException extends BashException {
    private final int line;
    private final int column;
    public ParseException(String message, int line, int column) {
        super(message);
        this.line = line;
        this.column = column;
    }
    public int line() { return line; }
    public int column() { return column; }
}
