package com.justbash.fs;

public record MkdirOptions(boolean recursive) {
    public static MkdirOptions defaults() {
        return new MkdirOptions(false);
    }
}
