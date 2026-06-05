package com.justbash.interpreter;

import com.justbash.BashExecResult;
import com.justbash.Command;
import com.justbash.ExecResult;
import com.justbash.SimpleCommandContext;
import com.justbash.ast.*;
import com.justbash.ast.command.*;
import com.justbash.ast.word.WordNode;
import com.justbash.fs.IFileSystem;
import com.justbash.interpreter.errors.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Interpreter {
    private final InterpreterOptions options;
    private InterpreterState state;
    private final ExpansionEngine expansion = new ExpansionEngine();
    private final BuiltinDispatcher builtins;
    private final CommandResolver resolver = new CommandResolver();
    private final PipelineExecutor pipelineExecutor;

    public Interpreter(InterpreterOptions options, InterpreterState state) {
        this.options = options;
        this.state = state;
        this.pipelineExecutor = new PipelineExecutor(this);
        this.builtins = new BuiltinDispatcher(this);
    }

    public InterpreterState getState() { return state; }
    public IFileSystem getFs() { return options.fs(); }

    /** Execute a ScriptNode without catching ExitException/ReturnException.
     *  Used by eval and source so that `eval "exit 42"` propagates up. */
    public ExecResult executeScriptRaw(ScriptNode script) {
        String stdout = "";
        String stderr = "";
        int exitCode = 0;

        for (StatementNode stmt : script.statements()) {
            var result = executeStatement(stmt);
            stdout += result.stdout();
            stderr += result.stderr();
            exitCode = result.exitCode();
            state.lastExitCode = exitCode;
            state.env.put("?", String.valueOf(exitCode));
        }

        return new ExecResult(stdout, stderr, exitCode);
    }

    /** Execute a ScriptNode (top-level entry point) */
    public BashExecResult executeScript(ScriptNode script) {
        try {
            var result = executeScriptRaw(script);
            return new BashExecResult(result.stdout(), result.stderr(), result.exitCode(), Map.copyOf(state.env));
        } catch (ExitException e) {
            return new BashExecResult(e.stdout(), e.stderr(), e.exitCode(), Map.copyOf(state.env));
        } catch (ReturnException e) {
            // Return at top level: set exit code and stop
            state.lastExitCode = e.exitCode();
            state.env.put("?", String.valueOf(e.exitCode()));
            return new BashExecResult("", "", e.exitCode(), Map.copyOf(state.env));
        }
    }

    /** Execute a StatementNode (handles &&, || between pipelines) */
    public ExecResult executeStatement(StatementNode stmt) {
        String stdout = "";
        String stderr = "";
        int exitCode = 0;

        List<PipelineNode> pipelines = stmt.pipelines();
        List<StatementNode.StatementOperator> operators = stmt.operators();

        for (int i = 0; i < pipelines.size(); i++) {
            StatementNode.StatementOperator operator = i > 0 ? operators.get(i - 1) : null;

            if (operator == StatementNode.StatementOperator.AND && exitCode != 0) continue;
            if (operator == StatementNode.StatementOperator.OR && exitCode == 0) continue;

            try {
                var result = pipelineExecutor.executePipeline(pipelines.get(i), state);
                stdout += result.stdout();
                stderr += result.stderr();
                exitCode = result.exitCode();
            } catch (BreakException e) {
                e.setStdout(e.stdout() + stdout);
                e.setStderr(e.stderr() + stderr);
                throw e;
            } catch (ContinueException e) {
                e.setStdout(e.stdout() + stdout);
                e.setStderr(e.stderr() + stderr);
                throw e;
            }
            state.lastExitCode = exitCode;
            state.env.put("?", String.valueOf(exitCode));
        }

        return new ExecResult(stdout, stderr, exitCode);
    }

    /** Execute a single CommandNode */
    public ExecResult executeCommand(CommandNode cmd, String stdin) {
        return switch (cmd) {
            case SimpleCommandNode simple -> executeSimpleCommand(simple, stdin);
            case IfNode ifNode -> executeIfCommand(ifNode, stdin);
            case ForNode forNode -> executeForCommand(forNode, stdin);
            case WhileNode whileNode -> executeWhileCommand(whileNode, stdin);
            case GroupNode group -> executeGroupCommand(group, stdin);
            case SubshellNode subshell -> executeSubshellCommand(subshell, stdin);
            default -> new ExecResult("", "", 0); // Stub for MVP
        };
    }

    private ExecResult executeStatements(List<StatementNode> statements, String stdin) {
        String stdout = "";
        String stderr = "";
        int exitCode = 0;
        for (StatementNode stmt : statements) {
            var result = executeStatement(stmt);
            stdout += result.stdout();
            stderr += result.stderr();
            exitCode = result.exitCode();
        }
        return new ExecResult(stdout, stderr, exitCode);
    }

    private ExecResult executeIfCommand(IfNode ifNode, String stdin) {
        for (IfNode.IfClause clause : ifNode.clauses()) {
            int conditionExit = executeStatements(clause.condition(), stdin).exitCode();
            if (conditionExit == 0) {
                return executeStatements(clause.body(), stdin);
            }
        }
        if (!ifNode.elseBody().isEmpty()) {
            return executeStatements(ifNode.elseBody(), stdin);
        }
        return new ExecResult("", "", 0);
    }

    private ExecResult executeForCommand(ForNode forNode, String stdin) {
        state.loopDepth++;
        try {
            List<String> values;
            if (forNode.words().isPresent()) {
                values = new ArrayList<>();
                for (WordNode word : forNode.words().get()) {
                    values.addAll(expansion.expandWord(word, state));
                }
            } else {
                // Default: iterate over $@ (positional parameters)
                String at = state.env.getOrDefault("@", "");
                values = at.isEmpty() ? List.of() : List.of(at.split(" "));
            }

            String stdout = "";
            String stderr = "";
            int exitCode = 0;

            for (String value : values) {
                state.env.put(forNode.variable(), value);
                try {
                    for (StatementNode stmt : forNode.body()) {
                        var result = executeStatement(stmt);
                        stdout += result.stdout();
                        stderr += result.stderr();
                        exitCode = result.exitCode();
                    }
                } catch (BreakException e) {
                    stdout += e.stdout();
                    stderr += e.stderr();
                    if (e.levels() > 1) {
                        var be = new BreakException(e.levels() - 1);
                        be.setStdout(stdout);
                        be.setStderr(stderr);
                        throw be;
                    }
                    break;
                } catch (ContinueException e) {
                    stdout += e.stdout();
                    stderr += e.stderr();
                    if (e.levels() > 1) {
                        var ce = new ContinueException(e.levels() - 1);
                        ce.setStdout(stdout);
                        ce.setStderr(stderr);
                        throw ce;
                    }
                    continue;
                }
            }

            return new ExecResult(stdout, stderr, exitCode);
        } finally {
            state.loopDepth--;
        }
    }

    private ExecResult executeWhileCommand(WhileNode whileNode, String stdin) {
        state.loopDepth++;
        try {
            String stdout = "";
            String stderr = "";
            int exitCode = 0;

            while (true) {
                int condExit = executeStatements(whileNode.condition(), stdin).exitCode();
                boolean shouldRun = whileNode.isUntil() ? (condExit != 0) : (condExit == 0);
                if (!shouldRun) break;

                try {
                    for (StatementNode stmt : whileNode.body()) {
                        var result = executeStatement(stmt);
                        stdout += result.stdout();
                        stderr += result.stderr();
                        exitCode = result.exitCode();
                    }
                } catch (BreakException e) {
                    stdout += e.stdout();
                    stderr += e.stderr();
                    if (e.levels() > 1) {
                        var be = new BreakException(e.levels() - 1);
                        be.setStdout(stdout);
                        be.setStderr(stderr);
                        throw be;
                    }
                    break;
                } catch (ContinueException e) {
                    stdout += e.stdout();
                    stderr += e.stderr();
                    if (e.levels() > 1) {
                        var ce = new ContinueException(e.levels() - 1);
                        ce.setStdout(stdout);
                        ce.setStderr(stderr);
                        throw ce;
                    }
                    continue;
                }
            }

            return new ExecResult(stdout, stderr, exitCode);
        } finally {
            state.loopDepth--;
        }
    }

    private ExecResult executeGroupCommand(GroupNode group, String stdin) {
        return executeStatements(group.body(), stdin);
    }

    private ExecResult executeSubshellCommand(SubshellNode subshell, String stdin) {
        // For MVP, execute in same state (full subshell isolation is future work)
        return executeStatements(subshell.body(), stdin);
    }

    private ExecResult executeSimpleCommand(SimpleCommandNode cmd, String stdin) {
        // Handle prefix assignments
        for (var assignment : cmd.assignments()) {
            String value = "";
            if (assignment.value().isPresent()) {
                value = expansion.expandWord(assignment.value().get(), state).get(0);
            }
            state.env.put(assignment.name(), value);
        }

        if (cmd.name() == null) {
            // Assignment-only command
            return new ExecResult("", "", 0);
        }

        // Expand command name
        String commandName = expansion.expandWord(cmd.name(), state).get(0);

        // Expand arguments
        List<String> args = new ArrayList<>();
        for (var argWord : cmd.args()) {
            args.addAll(expansion.expandWord(argWord, state));
        }

        // Try builtin first
        var builtinResult = builtins.dispatch(commandName, args, state);
        if (builtinResult.isPresent()) {
            return builtinResult.get();
        }

        // Try registered external commands
        Command externalCmd = options.commands().get(commandName);
        if (externalCmd != null) {
            Map<String, String> exportedEnv = buildExportedEnv();
            SimpleCommandContext cmdCtx = new SimpleCommandContext(
                options.fs(), state.cwd, state.env, exportedEnv,
                stdin, options.limits()
            );
            try {
                return externalCmd.execute(args, cmdCtx).join();
            } catch (Exception e) {
                return new ExecResult("", commandName + ": " + e.getMessage() + "\n", 1);
            }
        }

        // For MVP, unknown commands return exit 127
        String stderr = "bash: " + commandName + ": command not found\n";
        return new ExecResult("", stderr, 127);
    }

    private Map<String, String> buildExportedEnv() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String name : state.exportedVars) {
            String value = state.env.get(name);
            if (value != null) {
                result.put(name, value);
            }
        }
        return result;
    }
}
