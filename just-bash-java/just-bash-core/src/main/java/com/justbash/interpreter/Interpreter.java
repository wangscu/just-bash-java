package com.justbash.interpreter;

import com.justbash.BashExecResult;
import com.justbash.ExecResult;
import com.justbash.ast.*;
import com.justbash.ast.command.*;
import com.justbash.ast.word.WordNode;
import com.justbash.interpreter.errors.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Interpreter {
    private final InterpreterOptions options;
    private InterpreterState state;
    private final ExpansionEngine expansion = new ExpansionEngine();
    private final BuiltinDispatcher builtins = new BuiltinDispatcher();
    private final CommandResolver resolver = new CommandResolver();
    private final PipelineExecutor pipelineExecutor;

    public Interpreter(InterpreterOptions options, InterpreterState state) {
        this.options = options;
        this.state = state;
        this.pipelineExecutor = new PipelineExecutor(this);
    }

    public InterpreterState getState() { return state; }

    /** Execute a ScriptNode (top-level entry point) */
    public BashExecResult executeScript(ScriptNode script) {
        String stdout = "";
        String stderr = "";
        int exitCode = 0;

        for (StatementNode stmt : script.statements()) {
            try {
                var result = executeStatement(stmt);
                stdout += result.stdout();
                stderr += result.stderr();
                exitCode = result.exitCode();
                state.lastExitCode = exitCode;
                state.env.put("?", String.valueOf(exitCode));
            } catch (ExitException e) {
                return new BashExecResult(stdout + e.stdout(), stderr + e.stderr(), e.exitCode(), Map.copyOf(state.env));
            } catch (ReturnException e) {
                // Return at top level: set exit code and stop
                exitCode = e.exitCode();
                state.lastExitCode = exitCode;
                state.env.put("?", String.valueOf(exitCode));
                break;
            }
        }

        return new BashExecResult(stdout, stderr, exitCode, Map.copyOf(state.env));
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

            var result = pipelineExecutor.executePipeline(pipelines.get(i), state);
            stdout += result.stdout();
            stderr += result.stderr();
            exitCode = result.exitCode();
            state.lastExitCode = exitCode;
            state.env.put("?", String.valueOf(exitCode));
        }

        return new ExecResult(stdout, stderr, exitCode);
    }

    /** Execute a single CommandNode */
    public ExecResult executeCommand(CommandNode cmd, String stdin) {
        return switch (cmd) {
            case SimpleCommandNode simple -> executeSimpleCommand(simple, stdin);
            default -> new ExecResult("", "", 0); // Stub for MVP
        };
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

        // For MVP, unknown commands return exit 127
        String stderr = "bash: " + commandName + ": command not found\n";
        return new ExecResult("", stderr, 127);
    }
}
