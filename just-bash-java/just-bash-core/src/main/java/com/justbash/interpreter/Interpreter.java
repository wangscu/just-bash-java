package com.justbash.interpreter;

import com.justbash.BashExecResult;
import com.justbash.Command;
import com.justbash.ExecResult;
import com.justbash.SimpleCommandContext;
import com.justbash.ast.*;
import com.justbash.ast.command.*;
import com.justbash.ast.word.WordNode;
import com.justbash.fs.IFileSystem;
import com.justbash.fs.WriteFileOptions;
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
            } catch (ReturnException e) {
                e.setStdout(stdout + e.stdout());
                e.setStderr(stderr + e.stderr());
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
            case CaseNode caseNode -> executeCaseCommand(caseNode, stdin);
            case GroupNode group -> executeGroupCommand(group, stdin);
            case SubshellNode subshell -> executeSubshellCommand(subshell, stdin);
            case FunctionDefNode func -> executeFunctionDef(func);
            case ArithmeticCommandNode arith -> executeArithmeticCommand(arith);
            default -> new ExecResult("", "", 0); // Stub for MVP
        };
    }

    private ExecResult executeStatements(List<StatementNode> statements, String stdin) {
        String stdout = "";
        String stderr = "";
        int exitCode = 0;
        try {
            for (StatementNode stmt : statements) {
                var result = executeStatement(stmt);
                stdout += result.stdout();
                stderr += result.stderr();
                exitCode = result.exitCode();
            }
        } catch (ReturnException e) {
            e.setStdout(stdout + e.stdout());
            e.setStderr(stderr + e.stderr());
            throw e;
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
            ExpansionEngine.ScriptExecutor executor = this::executeScriptRaw;
            if (forNode.words().isPresent()) {
                values = new ArrayList<>();
                for (WordNode word : forNode.words().get()) {
                    List<String> expanded = expansion.expandWord(word, state, executor);
                    expanded = expansion.expandBraces(expanded);
                    expanded = expansion.expandGlobs(expanded, options.fs(), state);
                    values.addAll(expanded);
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

    private ExecResult executeCaseCommand(CaseNode caseNode, String stdin) {
        ExpansionEngine.ScriptExecutor executor = this::executeScriptRaw;
        String word = expansion.expandWord(caseNode.word(), state, executor).get(0);

        for (CaseNode.CaseItemNode item : caseNode.items()) {
            boolean matched = false;
            for (WordNode patternWord : item.patterns()) {
                String pattern = expansion.expandWord(patternWord, state, executor).get(0);
                if (globMatch(word, pattern)) {
                    matched = true;
                    break;
                }
            }
            if (matched) {
                return executeStatements(item.body(), stdin);
            }
        }

        return new ExecResult("", "", 0);
    }

    private boolean globMatch(String text, String pattern) {
        StringBuilder regex = new StringBuilder();
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append(".");
                case '[' -> {
                    int end = pattern.indexOf(']', i);
                    if (end == -1) {
                        regex.append("\\[");
                    } else {
                        regex.append(pattern.substring(i, end + 1));
                        i = end;
                    }
                }
                case '\\' -> {
                    if (i + 1 < pattern.length()) {
                        regex.append('\\').append(pattern.charAt(i + 1));
                        i++;
                    } else {
                        regex.append("\\\\");
                    }
                }
                default -> {
                    if ("[](){}^$|.+".indexOf(c) >= 0) {
                        regex.append('\\');
                    }
                    regex.append(c);
                }
            }
            i++;
        }
        return text.matches(regex.toString());
    }

    private ExecResult executeSimpleCommand(SimpleCommandNode cmd, String stdin) {
        ExpansionEngine.ScriptExecutor executor = this::executeScriptRaw;

        // Handle prefix assignments
        for (var assignment : cmd.assignments()) {
            if (assignment.array().isPresent()) {
                // Array literal: arr=(a b c)
                List<String> elements = new ArrayList<>();
                for (WordNode elem : assignment.array().get()) {
                    elements.addAll(expansion.expandWord(elem, state, executor));
                }
                state.indexedArrays.put(assignment.name(), elements);
            } else if (assignment.arrayIndex().isPresent()) {
                // Indexed assignment: arr[index]=value
                String index = expansion.expandWord(assignment.arrayIndex().get(), state, executor).get(0);
                String value = "";
                if (assignment.value().isPresent()) {
                    value = expansion.expandWord(assignment.value().get(), state, executor).get(0);
                }
                int idx = parseIntOrZero(index);
                setArrayElement(assignment.name(), idx, value);
            } else {
                String value = "";
                if (assignment.value().isPresent()) {
                    value = expansion.expandWord(assignment.value().get(), state, executor).get(0);
                }
                state.env.put(assignment.name(), value);
            }
        }

        // Expand redirection targets and apply input redirections
        List<RedirectionNode> redirects = new ArrayList<>();
        String redirectedStdin = stdin;
        for (var redir : cmd.redirections()) {
            String target = expansion.expandWord(
                ((RedirectionNode.WordTarget) redir.target()).word(), state, executor).get(0);
            String resolved = target.startsWith("/") ? target : state.cwd + "/" + target;

            if (redir.operator() == RedirectionNode.RedirectionOperator.LT) {
                // Input redirection
                try {
                    redirectedStdin = options.fs().readFile(resolved).join();
                } catch (Exception e) {
                    return new ExecResult("", "bash: " + target + ": No such file or directory\n", 1);
                }
            } else {
                redirects.add(redir);
            }
        }

        if (cmd.name() == null) {
            // Assignment-only command — still apply output redirections
            return applyOutputRedirections(new ExecResult("", "", 0), redirects);
        }

        // Make redirected stdin available to builtins like read
        String savedGroupStdin = state.groupStdin;
        state.groupStdin = redirectedStdin;

        // Expand command name
        String commandName = expansion.expandWord(cmd.name(), state, executor).get(0);

        // Expand arguments with brace and glob expansion
        List<String> args = new ArrayList<>();
        for (var argWord : cmd.args()) {
            List<String> expanded = expansion.expandWord(argWord, state, executor);
            expanded = expansion.expandBraces(expanded);
            expanded = expansion.expandGlobs(expanded, options.fs(), state);
            args.addAll(expanded);
        }

        ExecResult result;

        // Try user-defined functions first
        FunctionDefNode func = state.functions.get(commandName);
        if (func != null) {
            result = callFunction(func, args);
        } else {
            // Try builtin
            var builtinResult = builtins.dispatch(commandName, args, state);
            if (builtinResult.isPresent()) {
                result = builtinResult.get();
            } else {
                // Try registered external commands
                Command externalCmd = options.commands().get(commandName);
                if (externalCmd != null) {
                    Map<String, String> exportedEnv = buildExportedEnv();
                    SimpleCommandContext cmdCtx = new SimpleCommandContext(
                        options.fs(), state.cwd, state.env, exportedEnv,
                        redirectedStdin, options.limits()
                    );
                    try {
                        result = externalCmd.execute(args, cmdCtx).join();
                    } catch (Exception e) {
                        result = new ExecResult("", commandName + ": " + e.getMessage() + "\n", 1);
                    }
                } else {
                    // For MVP, unknown commands return exit 127
                    String stderr = "bash: " + commandName + ": command not found\n";
                    result = new ExecResult("", stderr, 127);
                }
            }
        }

        state.groupStdin = savedGroupStdin;
        return applyOutputRedirections(result, redirects);
    }

    private ExecResult applyOutputRedirections(ExecResult result, List<RedirectionNode> redirects) {
        String stdout = result.stdout();
        String stderr = result.stderr();
        int exitCode = result.exitCode();

        for (var redir : redirects) {
            String target = expansion.expandWord(
                ((RedirectionNode.WordTarget) redir.target()).word(), state, this::executeScriptRaw).get(0);
            String resolved = target.startsWith("/") ? target : state.cwd + "/" + target;

            switch (redir.operator()) {
                case GT -> {
                    int fd = redir.fd().orElse(1);
                    if (fd == 1) {
                        writeFile(resolved, stdout, false);
                        stdout = "";
                    } else if (fd == 2) {
                        writeFile(resolved, stderr, false);
                        stderr = "";
                    }
                }
                case GTGT -> {
                    int fd = redir.fd().orElse(1);
                    if (fd == 1) {
                        writeFile(resolved, stdout, true);
                        stdout = "";
                    } else if (fd == 2) {
                        writeFile(resolved, stderr, true);
                        stderr = "";
                    }
                }
                default -> { /* ignore unsupported for MVP */ }
            }
        }

        return new ExecResult(stdout, stderr, exitCode);
    }

    private void writeFile(String path, String content, boolean append) {
        try {
            if (append) {
                boolean exists = options.fs().exists(path).join();
                if (exists) {
                    options.fs().appendFile(path,
                        new IFileSystem.StringContent(content),
                        WriteFileOptions.utf8()).join();
                } else {
                    options.fs().writeFile(path,
                        new IFileSystem.StringContent(content),
                        WriteFileOptions.utf8()).join();
                }
            } else {
                options.fs().writeFile(path,
                    new IFileSystem.StringContent(content),
                    WriteFileOptions.utf8()).join();
            }
        } catch (Exception e) {
            // Ignore write errors for MVP
        }
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

    private ExecResult executeFunctionDef(FunctionDefNode funcNode) {
        state.functions.put(funcNode.name(), funcNode);
        return new ExecResult("", "", 0);
    }

    private ExecResult executeArithmeticCommand(ArithmeticCommandNode arith) {
        long result = ArithmeticEvaluator.evaluate(arith.expression().expression(), state);
        int exitCode = result != 0 ? 0 : 1;
        state.lastExitCode = exitCode;
        state.env.put("?", String.valueOf(exitCode));
        return new ExecResult("", "", exitCode);
    }

    private ExecResult callFunction(FunctionDefNode func, List<String> args) {
        state.callDepth++;
        state.pushLocalScope();

        Map<String, String> saved = new LinkedHashMap<>();
        for (int i = 0; i < args.size(); i++) {
            String key = String.valueOf(i + 1);
            saved.put(key, state.env.get(key));
            state.env.put(key, args.get(i));
        }
        saved.put("@", state.env.get("@"));
        saved.put("#", state.env.get("#"));
        state.env.put("@", String.join(" ", args));
        state.env.put("#", String.valueOf(args.size()));

        state.funcNameStack.add(0, func.name());

        try {
            try {
                return executeCommand(func.body(), "");
            } catch (ReturnException e) {
                return new ExecResult(e.stdout(), e.stderr(), e.exitCode());
            }
        } finally {
            for (Map.Entry<String, String> entry : saved.entrySet()) {
                if (entry.getValue() == null) {
                    state.env.remove(entry.getKey());
                } else {
                    state.env.put(entry.getKey(), entry.getValue());
                }
            }
            if (!state.localScopes.isEmpty()) {
                Map<String, String> localScope = state.localScopes.get(state.localScopes.size() - 1);
                for (Map.Entry<String, String> entry : localScope.entrySet()) {
                    if ("__UNSET__".equals(entry.getValue())) {
                        state.env.remove(entry.getKey());
                    } else {
                        state.env.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            if (!state.funcNameStack.isEmpty()) {
                state.funcNameStack.remove(0);
            }
            state.popLocalScope();
            state.callDepth--;
        }
    }

    private static int parseIntOrZero(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void setArrayElement(String name, int index, String value) {
        List<String> arr = state.indexedArrays.get(name);
        if (arr == null) {
            arr = new ArrayList<>();
            state.indexedArrays.put(name, arr);
        }
        // Ensure the array is large enough
        while (arr.size() <= index) {
            arr.add("");
        }
        arr.set(index, value);
    }
}
