package com.justbash.fs;

public record ReadFileOptions(String encoding) {
    public static ReadFileOptions utf8() {
        return new ReadFileOptions("utf8");
    }
    public static ReadFileOptions binary() {
        return new ReadFileOptions("binary");
    }
}
