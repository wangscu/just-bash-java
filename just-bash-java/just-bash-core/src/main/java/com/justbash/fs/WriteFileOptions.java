package com.justbash.fs;

public record WriteFileOptions(String encoding) {
    public static WriteFileOptions utf8() {
        return new WriteFileOptions("utf8");
    }
}
