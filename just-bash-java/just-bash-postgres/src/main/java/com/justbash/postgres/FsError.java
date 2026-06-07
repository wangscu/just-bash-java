package com.justbash.postgres;

public class FsError extends RuntimeException {
    private final String code;

    public FsError(String code, String op, String path) {
        super(code + ": " + op + ", '" + path + "'");
        this.code = code;
    }

    public String code() {
        return code;
    }
}
