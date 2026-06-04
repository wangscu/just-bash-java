package com.justbash.fs;

public record CpOptions(boolean recursive) {
    public static CpOptions defaults() {
        return new CpOptions(false);
    }
}
