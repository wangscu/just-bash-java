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

public class SimpleCommandContext implements CommandContext {
    private final IFileSystem fs;
    private final String cwd;
    private final Map<String, String> env;
    private final Optional<Map<String, String>> exportedEnv;
    private final ByteString stdin;
    private final Optional<ExecutionLimits> limits;
    private final Optional<Consumer<TraceEvent>> trace;
    private final Optional<BiFunction<String, ExecOptions, CompletableFuture<BashExecResult>>> exec;
    private final Optional<Function<Long, CompletableFuture<Void>>> sleep;
    private final Optional<Map<Integer, String>> fileDescriptors;
    private final boolean xpgEcho;
    private final int substitutionDepth;
    private final Optional<Object> signal;

    public SimpleCommandContext(IFileSystem fs, String cwd, Map<String, String> env,
                                 Map<String, String> exportedEnv, String stdin,
                                 ExecutionLimits limits) {
        this.fs = fs;
        this.cwd = cwd;
        this.env = env;
        this.exportedEnv = Optional.ofNullable(exportedEnv);
        this.stdin = stdin != null
            ? ByteString.fromUtf8Bytes(stdin.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            : ByteString.fromUtf8Bytes(new byte[0]);
        this.limits = Optional.ofNullable(limits);
        this.trace = Optional.empty();
        this.exec = Optional.empty();
        this.sleep = Optional.empty();
        this.fileDescriptors = Optional.empty();
        this.xpgEcho = false;
        this.substitutionDepth = 0;
        this.signal = Optional.empty();
    }

    @Override public IFileSystem fs() { return fs; }
    @Override public String cwd() { return cwd; }
    @Override public Map<String, String> env() { return env; }
    @Override public Optional<Map<String, String>> exportedEnv() { return exportedEnv; }
    @Override public ByteString stdin() { return stdin; }
    @Override public Optional<ExecutionLimits> limits() { return limits; }
    @Override public Optional<Consumer<TraceEvent>> trace() { return trace; }
    @Override public Optional<BiFunction<String, ExecOptions, CompletableFuture<BashExecResult>>> exec() { return exec; }
    @Override public Optional<Function<Long, CompletableFuture<Void>>> sleep() { return sleep; }
    @Override public Optional<Map<Integer, String>> fileDescriptors() { return fileDescriptors; }
    @Override public boolean xpgEcho() { return xpgEcho; }
    @Override public int substitutionDepth() { return substitutionDepth; }
    @Override public Optional<Object> signal() { return signal; }
}
