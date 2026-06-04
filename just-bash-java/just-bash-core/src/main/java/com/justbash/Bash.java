package com.justbash;

import com.justbash.fs.IFileSystem;
import com.justbash.fs.InMemoryFs;
import com.justbash.parser.Parser;
import com.justbash.security.ExecutionLimits;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class Bash {
    private final IFileSystem fs;
    private final Map<String, Command> commands = new LinkedHashMap<>();
    private final ExecutionLimits limits;
    private final ExecutorService virtualThreadExecutor;
    private final Optional<BashLogger> logger;

    private String cwd = "/home/user";
    private Map<String, String> env = new LinkedHashMap<>();

    public Bash() { this(BashOptions.defaults()); }

    public Bash(BashOptions options) {
        this.fs = options.fs().orElseGet(InMemoryFs::new);
        this.limits = options.executionLimits().orElse(ExecutionLimits.defaults());
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.logger = options.logger();

        // Initialize default env
        env.put("HOME", "/home/user");
        env.put("PATH", "/usr/bin:/bin");
        env.put("IFS", " \t\n");
        env.put("PWD", cwd);
        env.put("OLDPWD", cwd);

        if (options.env().isPresent()) {
            env.putAll(options.env().get());
        }
    }

    public CompletableFuture<BashExecResult> exec(String commandLine) {
        return exec(commandLine, ExecOptions.defaults());
    }

    public CompletableFuture<BashExecResult> exec(String commandLine,
                                                   ExecOptions options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeSync(commandLine, options);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, virtualThreadExecutor);
    }

    private BashExecResult executeSync(String commandLine, ExecOptions options) {
        // TODO: Full interpreter integration
        // For now, parse and return empty result
        try {
            var ast = Parser.parse(commandLine);
            return new BashExecResult("", "", 0, Map.copyOf(env));
        } catch (com.justbash.interpreter.errors.ParseException e) {
            return new BashExecResult("",
                "bash: syntax error: " + e.getMessage() + "\n", 2, Map.copyOf(env));
        }
    }

    public CompletableFuture<String> readFile(String path) {
        return fs.readFile(fs.resolvePath(cwd, path));
    }

    public String getCwd() { return cwd; }
    public Map<String, String> getEnv() { return Map.copyOf(env); }

    public void registerCommand(Command command) {
        commands.put(command.name(), command);
    }

    public void shutdown() {
        virtualThreadExecutor.shutdown();
    }
}
