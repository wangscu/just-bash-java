package com.justbash;

import com.justbash.fs.IFileSystem;
import com.justbash.network.NetworkConfig;
import com.justbash.security.ExecutionLimits;
import java.util.Map;
import java.util.Optional;

public record BashOptions(
    Optional<Map<String, String>> env,
    Optional<String> cwd,
    Optional<IFileSystem> fs,
    Optional<ExecutionLimits> executionLimits,
    Optional<NetworkConfig> networkConfig,
    Optional<Boolean> python,
    Optional<Object> javascript,
    Optional<BashLogger> logger
) {
    public static BashOptions defaults() {
        return new BashOptions(
            Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty()
        );
    }
}
