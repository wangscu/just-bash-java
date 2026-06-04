package com.justbash;

import com.justbash.encoding.ByteString;
import com.justbash.fs.IFileSystem;
import com.justbash.security.ExecutionLimits;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public interface CommandContext {
    IFileSystem fs();
    String cwd();
    Map<String, String> env();
    Optional<Map<String, String>> exportedEnv();
    ByteString stdin();
    Optional<ExecutionLimits> limits();
    Optional<Consumer<TraceEvent>> trace();
    Optional<BiFunction<String, ExecOptions, CompletableFuture<BashExecResult>>> exec();
    Optional<Function<Long, CompletableFuture<Void>>> sleep();
    Optional<Map<Integer, String>> fileDescriptors();
    boolean xpgEcho();
    int substitutionDepth();
    Optional<Object> signal();
}
