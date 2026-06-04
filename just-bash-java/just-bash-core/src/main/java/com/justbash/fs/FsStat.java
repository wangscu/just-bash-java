package com.justbash.fs;

import java.time.Instant;

public record FsStat(
    boolean isFile, boolean isDirectory, boolean isSymbolicLink,
    int mode, long size, Instant mtime
) {}
