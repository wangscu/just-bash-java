package com.justbash.fs;

public record DirentEntry(
    String name, boolean isFile, boolean isDirectory, boolean isSymbolicLink
) {}
