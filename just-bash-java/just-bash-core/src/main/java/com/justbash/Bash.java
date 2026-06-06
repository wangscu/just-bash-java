package com.justbash;

import com.justbash.ast.ScriptNode;
import com.justbash.fs.IFileSystem;
import com.justbash.fs.InMemoryFs;
import com.justbash.interpreter.*;
import com.justbash.interpreter.errors.ParseException;
import com.justbash.network.NetworkConfig;
import com.justbash.network.SecureHttpClient;
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
    private final Optional<SecureHttpClient> secureHttpClient;

    // Persistent interpreter state across exec() calls
    private InterpreterState state;

    public Bash() { this(BashOptions.defaults()); }

    public Bash(BashOptions options) {
        this.fs = options.fs().orElseGet(InMemoryFs::new);
        this.limits = options.executionLimits().orElse(ExecutionLimits.defaults());
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.logger = options.logger();
        this.secureHttpClient = options.networkConfig().map(SecureHttpClient::create);

        // Initialize persistent state
        this.state = InterpreterState.defaults();
        this.state.getEnv().put("HOME", "/home/user");
        this.state.getEnv().put("PATH", "/usr/bin:/bin");
        this.state.getEnv().put("IFS", " \t\n");
        this.state.getEnv().put("PWD", "/home/user");
        this.state.getEnv().put("OLDPWD", "/home/user");
        this.state.getExportedVars().add("HOME");
        this.state.getExportedVars().add("PATH");
        this.state.getExportedVars().add("IFS");
        this.state.getExportedVars().add("PWD");
        this.state.getExportedVars().add("OLDPWD");
        this.state.setCwd("/home/user");
        this.state.setPreviousDir("/home/user");

        if (options.env().isPresent()) {
            for (Map.Entry<String, String> entry : options.env().get().entrySet()) {
                this.state.getEnv().put(entry.getKey(), entry.getValue());
                this.state.getExportedVars().add(entry.getKey());
            }
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
        try {
            var ast = Parser.parse(commandLine);
            if (ast instanceof ScriptNode script) {
                InterpreterOptions interpOptions = new InterpreterOptions(
                    this.fs,
                    this.commands,
                    this.limits,
                    (scriptStr, execOpts) -> exec(scriptStr, execOpts),
                    this.secureHttpClient
                );

                Interpreter interpreter = new Interpreter(interpOptions, this.state);
                BashExecResult result = interpreter.executeScript(script);
                return result;
            }
            return new BashExecResult("", "", 0, Map.copyOf(state.getEnv()));
        } catch (ParseException e) {
            return new BashExecResult("",
                "bash: syntax error: " + e.getMessage() + "\n", 2, Map.copyOf(state.getEnv()));
        }
    }

    public CompletableFuture<String> readFile(String path) {
        return fs.readFile(fs.resolvePath(state.getCwd(), path));
    }

    public String getCwd() { return state.getCwd(); }
    public Map<String, String> getEnv() { return Map.copyOf(state.getEnv()); }

    public void registerCommand(Command command) {
        commands.put(command.name(), command);
    }

    public void shutdown() {
        virtualThreadExecutor.shutdown();
    }
}
