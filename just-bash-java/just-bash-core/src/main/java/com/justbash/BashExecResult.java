package com.justbash;

import java.util.Map;
import java.util.Optional;

public record BashExecResult(
    String stdout,
    String stderr,
    int exitCode,
    Optional<ExecResult.StdoutKind> stdoutKind,
    Map<String, String> env,
    Optional<Map<String, Object>> metadata
) {
    public BashExecResult(String stdout, String stderr, int exitCode,
                          Map<String, String> env) {
        this(stdout, stderr, exitCode, Optional.empty(),
             Map.copyOf(env), Optional.empty());
    }

    public static BashExecResult from(ExecResult result, Map<String, String> env) {
        return new BashExecResult(
            result.stdout(), result.stderr(), result.exitCode(),
            result.stdoutKind(), Map.copyOf(env), Optional.empty()
        );
    }
}
