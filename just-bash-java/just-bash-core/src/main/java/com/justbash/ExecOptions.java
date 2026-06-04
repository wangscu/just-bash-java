package com.justbash;

import java.util.Map;
import java.util.Optional;

public record ExecOptions(
    Optional<Map<String, String>> env,
    boolean replaceEnv,
    Optional<String> cwd,
    boolean rawScript,
    Optional<String> stdin,
    Optional<StdinKind> stdinKind,
    Optional<Object> signal,
    Optional<java.util.List<String>> args
) {
    public enum StdinKind { TEXT, BYTES }

    public static ExecOptions defaults() {
        return new ExecOptions(
            Optional.empty(), false, Optional.empty(),
            false, Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty()
        );
    }
}
