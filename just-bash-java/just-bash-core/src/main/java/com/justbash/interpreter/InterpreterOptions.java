package com.justbash.interpreter;

import com.justbash.BashExecResult;
import com.justbash.Command;
import com.justbash.ExecOptions;
import com.justbash.TraceEvent;
import com.justbash.fs.IFileSystem;
import com.justbash.security.ExecutionLimits;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class InterpreterOptions {
    private final IFileSystem fs;
    private final Map<String, Command> commands;
    private final ExecutionLimits limits;
    private final BiFunction<String, ExecOptions, CompletableFuture<BashExecResult>> exec;

    private Optional<Consumer<TraceEvent>> trace = Optional.empty();
    private Optional<Function<Long, CompletableFuture<Void>>> sleep = Optional.empty();
    private boolean requireDefenseContext = false;
    private Optional<String> jsBootstrapCode = Optional.empty();
    private Optional<BiFunction<String, String, CompletableFuture<String>>> invokeTool = Optional.empty();

    public InterpreterOptions(
            IFileSystem fs,
            Map<String, Command> commands,
            ExecutionLimits limits,
            BiFunction<String, ExecOptions, CompletableFuture<BashExecResult>> exec) {
        this.fs = fs;
        this.commands = commands;
        this.limits = limits;
        this.exec = exec;
    }

    public InterpreterOptions withTrace(Consumer<TraceEvent> trace) {
        this.trace = Optional.ofNullable(trace);
        return this;
    }

    public InterpreterOptions withSleep(Function<Long, CompletableFuture<Void>> sleep) {
        this.sleep = Optional.ofNullable(sleep);
        return this;
    }

    public InterpreterOptions withRequireDefenseContext(boolean require) {
        this.requireDefenseContext = require;
        return this;
    }

    public InterpreterOptions withJsBootstrapCode(String code) {
        this.jsBootstrapCode = Optional.ofNullable(code);
        return this;
    }

    public InterpreterOptions withInvokeTool(BiFunction<String, String, CompletableFuture<String>> invokeTool) {
        this.invokeTool = Optional.ofNullable(invokeTool);
        return this;
    }

    public IFileSystem fs() {
        return fs;
    }

    public Map<String, Command> commands() {
        return commands;
    }

    public ExecutionLimits limits() {
        return limits;
    }

    public BiFunction<String, ExecOptions, CompletableFuture<BashExecResult>> exec() {
        return exec;
    }

    public Optional<Consumer<TraceEvent>> trace() {
        return trace;
    }

    public Optional<Function<Long, CompletableFuture<Void>>> sleep() {
        return sleep;
    }

    public boolean requireDefenseContext() {
        return requireDefenseContext;
    }

    public Optional<String> jsBootstrapCode() {
        return jsBootstrapCode;
    }

    public Optional<BiFunction<String, String, CompletableFuture<String>>> invokeTool() {
        return invokeTool;
    }
}
