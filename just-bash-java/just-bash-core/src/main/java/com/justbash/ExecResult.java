package com.justbash;

import java.util.Optional;

public record ExecResult(
    String stdout,
    String stderr,
    int exitCode,
    Optional<StdoutKind> stdoutKind
) {
    public enum StdoutKind { TEXT, BYTES }

    public ExecResult(String stdout, String stderr, int exitCode) {
        this(stdout, stderr, exitCode, Optional.empty());
    }
}
