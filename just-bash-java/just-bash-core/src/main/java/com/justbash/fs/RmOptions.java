package com.justbash.fs;

public record RmOptions(boolean recursive, boolean force) {
    public static RmOptions defaults() {
        return new RmOptions(false, false);
    }
}
