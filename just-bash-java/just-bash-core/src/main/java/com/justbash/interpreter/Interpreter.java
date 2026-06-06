package com.justbash.interpreter;

import com.justbash.BashExecResult;
import com.justbash.Command;
import com.justbash.ExecResult;
import com.justbash.SimpleCommandContext;
import com.justbash.ast.*;
import com.justbash.ast.command.*;
import com.justbash.ast.word.*;
import com.justbash.parser.Parser;
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
        this.expansion.setFileSystem(options.fs());
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

    private String reconstructStatement(StatementNode stmt) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stmt.pipelines().size(); i++) {
            if (i > 0) {
                var op = i <= stmt.operators().size() ? stmt.operators().get(i - 1) : null;
                String sep = switch (op) {
                    case AND -> " && ";
                    case OR -> " || ";
                    case SEMICOLON -> "; ";
                    case null -> "; ";
                };
                sb.append(sep);
            }
            sb.append(reconstructPipeline(stmt.pipelines().get(i)));
        }
        return sb.toString();
    }

    private String reconstructPipeline(PipelineNode pipeline) {
        StringBuilder sb = new StringBuilder();
        if (pipeline.negated()) sb.append("! ");
        for (int i = 0; i < pipeline.commands().size(); i++) {
            if (i > 0) sb.append(" | ");
            sb.append(reconstructCommand(pipeline.commands().get(i)));
        }
        return sb.toString();
    }

    private String reconstructCommand(CommandNode cmd) {
        return switch (cmd) {
            case SimpleCommandNode simple -> reconstructSimpleCommand(simple);
            case GroupNode group -> "{ ... }";
            case SubshellNode subshell -> "(...)";
            case IfNode ifNode -> "if ...; then ...; fi";
            case ForNode forNode -> "for ...; do ...; done";
            case WhileNode whileNode -> whileNode.isUntil() ? "until ...; do ...; done" : "while ...; do ...; done";
            case CaseNode caseNode -> "case ... in ... esac";
            case FunctionDefNode func -> "function " + func.name() + "() { ... }";
            case ArithmeticCommandNode arith -> "(( ... ))";
            case ConditionalCommandNode cond -> "[[ ... ]]";
            default -> "...";
        };
    }

    private String reconstructSimpleCommand(SimpleCommandNode cmd) {
        StringBuilder sb = new StringBuilder();
        // Reconstruct from original word nodes (not expanded)
        if (cmd.name() != null) {
            sb.append(reconstructWord(cmd.name()));
        }
        for (WordNode arg : cmd.args()) {
            sb.append(" ").append(reconstructWord(arg));
        }
        return sb.toString();
    }

    private String reconstructWord(WordNode word) {
        StringBuilder sb = new StringBuilder();
        for (var part : word.parts()) {
            sb.append(switch (part) {
                case LiteralPart lp -> lp.value();
                case ParameterExpansionPart pep -> "$" + (pep.operation().isPresent() ? "{" + pep.parameter() + "}" : pep.parameter());
                case CommandSubstitutionPart csp -> "$(...)";
                case ArithmeticExpansionPart aep -> "$((" + aep.expression() + "))";
                case DoubleQuotedPart dqp -> "\"" + reconstructWord(new WordNode(0, dqp.parts())) + "\"";
                case SingleQuotedPart sqp -> "'" + sqp.value() + "'";
                case EscapedPart ep -> ep.value();
                default -> "";
            });
        }
        return sb.toString();
    }

    /** Execute a ScriptNode (top-level entry point) */
    public BashExecResult executeScript(ScriptNode script) {
        BashExecResult result;
        ExecResult trapResult = new ExecResult("", "", 0);
        try {
            try {
                var rawResult = executeScriptRaw(script);
                result = new BashExecResult(rawResult.stdout(), rawResult.stderr(), rawResult.exitCode(), Map.copyOf(state.env));
            } catch (ExitException e) {
                result = new BashExecResult(e.stdout(), e.stderr(), e.exitCode(), Map.copyOf(state.env));
            } catch (ReturnException e) {
                // Return at top level: set exit code and stop
                state.lastExitCode = e.exitCode();
                state.env.put("?", String.valueOf(e.exitCode()));
                result = new BashExecResult("", "", e.exitCode(), Map.copyOf(state.env));
            } catch (ExecutionLimitException e) {
                String msg = e.getMessage() + "\n";
                result = new BashExecResult("", msg, ExecutionLimitException.EXIT_CODE, Map.copyOf(state.env));
            }
        } finally {
            trapResult = runExitTrap();
        }
        if (trapResult.exitCode() != 0 || !trapResult.stdout().isEmpty() || !trapResult.stderr().isEmpty()) {
            result = new BashExecResult(
                result.stdout() + trapResult.stdout(),
                result.stderr() + trapResult.stderr(),
                trapResult.exitCode() != 0 ? trapResult.exitCode() : result.exitCode(),
                Map.copyOf(state.env)
            );
        }
        return result;
    }

    private ExecResult runExitTrap() {
        String trapCmd = state.trapHandlers.get("EXIT");
        if (trapCmd == null || trapCmd.isEmpty()) {
            return new ExecResult("", "", 0);
        }
        try {
            var ast = Parser.parse(trapCmd);
            if (ast instanceof ScriptNode script) {
                return executeScriptRaw(script);
            }
        } catch (Exception e) {
            // Ignore trap execution errors
        }
        return new ExecResult("", "", 0);
    }

    /** Execute a StatementNode (handles &&, || between pipelines) */
    public ExecResult executeStatement(StatementNode stmt) {
        state.currentLine = stmt.line();
        String stdout = "";
        String stderr = "";
        int exitCode = 0;

        List<PipelineNode> pipelines = stmt.pipelines();
        List<StatementNode.StatementOperator> operators = stmt.operators();

        for (int i = 0; i < pipelines.size(); i++) {
            StatementNode.StatementOperator operator = i > 0 ? operators.get(i - 1) : null;

            if (operator == StatementNode.StatementOperator.AND && exitCode != 0) continue;
            if (operator == StatementNode.StatementOperator.OR && exitCode == 0) continue;

            // verbose: print reconstructed command before execution
            if (state.options.verbose) {
                String reconstructed = reconstructPipeline(pipelines.get(i));
                if (!reconstructed.isEmpty()) {
                    stderr += reconstructed + "\n";
                }
            }

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

            // errexit: exit immediately on non-zero, except in conditions,
            // negated pipelines, or &&/|| chains
            boolean hasAndOr = operators.stream().anyMatch(op ->
                op == StatementNode.StatementOperator.AND || op == StatementNode.StatementOperator.OR);
            if (state.options.errexit && exitCode != 0
                    && !state.inCondition
                    && !pipelines.get(i).negated()
                    && !hasAndOr) {
                throw new ExitException(exitCode, stdout, stderr);
            }
        }

        return new ExecResult(stdout, stderr, exitCode);
    }

    /** Execute a single CommandNode */
    public ExecResult executeCommand(CommandNode cmd, String stdin) {
        state.commandCount++;
        if (state.commandCount > options.limits().maxCommandCount()) {
            throw new ExecutionLimitException(
                "too many commands executed (>" + options.limits().maxCommandCount() +
                    "), increase executionLimits.maxCommandCount",
                "maxCommandCount");
        }
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
            case ConditionalCommandNode cond -> executeConditionalCommand(cond);
            default -> new ExecResult("", "", 0); // Stub for MVP
        };
    }

    /** Execute a CommandNode with a local (copied) state for subshell isolation */
    ExecResult executeCommand(CommandNode cmd, String stdin, InterpreterState localState) {
        InterpreterState saved = this.state;
        this.state = localState;
        try {
            return executeCommand(cmd, stdin);
        } finally {
            this.state = saved;
        }
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
            boolean saved = state.inCondition;
            state.inCondition = true;
            int conditionExit;
            try {
                conditionExit = executeStatements(clause.condition(), stdin).exitCode();
            } finally {
                state.inCondition = saved;
            }
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

            // Check for nounset expansion error
            var expansionError = checkExpansionError();
            if (expansionError != null) {
                return expansionError;
            }

            String stdout = "";
            String stderr = "";
            int exitCode = 0;
            int iterations = 0;

            for (String value : values) {
                iterations++;
                if (iterations > options.limits().maxLoopIterations()) {
                    throw new ExecutionLimitException(
                        "for loop: too many iterations (" + options.limits().maxLoopIterations() +
                            "), increase executionLimits.maxLoopIterations",
                        "maxLoopIterations");
                }
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

            int iterations = 0;
            while (true) {
                iterations++;
                if (iterations > options.limits().maxLoopIterations()) {
                    throw new ExecutionLimitException(
                        (whileNode.isUntil() ? "until" : "while") +
                            " loop: too many iterations (" + options.limits().maxLoopIterations() +
                            "), increase executionLimits.maxLoopIterations",
                        "maxLoopIterations");
                }
                boolean saved = state.inCondition;
                state.inCondition = true;
                int condExit;
                try {
                    condExit = executeStatements(whileNode.condition(), stdin).exitCode();
                } finally {
                    state.inCondition = saved;
                }
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
        InterpreterState savedState = this.state;
        this.state = savedState.copy();
        try {
            try {
                return executeStatements(subshell.body(), stdin);
            } catch (ExitException e) {
                // exit in subshell: return the exit code, don't propagate
                return new ExecResult(e.stdout(), e.stderr(), e.exitCode());
            } catch (ReturnException e) {
                // return in subshell without function context: just return the code
                return new ExecResult(e.stdout(), e.stderr(), e.exitCode());
            }
        } finally {
            // Update parent's $? with subshell exit code
            int subshellExitCode = this.state.lastExitCode;
            this.state = savedState;
            this.state.lastExitCode = subshellExitCode;
            this.state.env.put("?", String.valueOf(subshellExitCode));
        }
    }

    private ExecResult executeCaseCommand(CaseNode caseNode, String stdin) {
        ExpansionEngine.ScriptExecutor executor = this::executeScriptRaw;
        String word = expansion.expandWord(caseNode.word(), state, executor).get(0);

        // Check for nounset expansion error
        var expansionError = checkExpansionError();
        if (expansionError != null) {
            return expansionError;
        }

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
                String index = expansion.expandWord(assignment.arrayIndex().get(), state, executor).get(0);
                String value = "";
                if (assignment.value().isPresent()) {
                    value = expansion.expandWord(assignment.value().get(), state, executor).get(0);
                }
                if (state.associativeArrays.contains(assignment.name())) {
                    // Associative array assignment: arr[key]=value
                    setAssociativeArrayElement(assignment.name(), index, value);
                } else {
                    // Indexed assignment: arr[index]=value
                    int idx = parseIntOrZero(index);
                    setArrayElement(assignment.name(), idx, value);
                }
            } else {
                String value = "";
                if (assignment.value().isPresent()) {
                    value = expansion.expandWord(assignment.value().get(), state, executor).get(0);
                }
                state.env.put(assignment.name(), value);
            }
        }

        // Apply persistent input redirections from prior exec
        String effectiveStdin = applyPersistentInputRedirect(stdin);

        // Expand redirection targets and apply input redirections
        List<RedirectionNode> redirects = new ArrayList<>();
        List<String> redirectPaths = new ArrayList<>();
        String redirectedStdin = effectiveStdin;
        for (var redir : cmd.redirections()) {
            if (redir.operator() == RedirectionNode.RedirectionOperator.HERESTRING) {
                String content = expansion.expandWord(
                    ((RedirectionNode.WordTarget) redir.target()).word(), state, executor).get(0);
                redirectedStdin = content + "\n";
                continue;
            }
            if (redir.operator() == RedirectionNode.RedirectionOperator.HEREDOC
                || redir.operator() == RedirectionNode.RedirectionOperator.HEREDOC_STRIP) {
                HereDocNode hereDoc = ((RedirectionNode.HereDocTarget) redir.target()).hereDoc();
                String body = hereDoc.content().parts().get(0) instanceof LiteralPart lp
                    ? lp.value() : "";
                if (!hereDoc.quoted()) {
                    WordNode bodyWord = Parser.parseWord(body, hereDoc.line());
                    body = expansion.expandWord(bodyWord, state, executor).get(0);
                }
                redirectedStdin = body + "\n";
                continue;
            }

            String target = expansion.expandWord(
                ((RedirectionNode.WordTarget) redir.target()).word(), state, executor).get(0);
            String resolved;
            if (redir.operator() == RedirectionNode.RedirectionOperator.GTAMP
                || redir.operator() == RedirectionNode.RedirectionOperator.LTAMP) {
                // FD duplication targets are literal numbers, not paths
                resolved = target;
            } else {
                resolved = target.startsWith("/") ? target : state.cwd + "/" + target;
            }

            if (redir.operator() == RedirectionNode.RedirectionOperator.LT) {
                // Input redirection
                try {
                    redirectedStdin = options.fs().readFile(resolved).join();
                } catch (Exception e) {
                    return new ExecResult("", "bash: " + target + ": No such file or directory\n", 1);
                }
            } else {
                redirects.add(redir);
                redirectPaths.add(resolved);
            }
        }

        // Check for nounset expansion error after assignments and redirections
        var expansionError = checkExpansionError();
        if (expansionError != null) {
            return expansionError;
        }

        if (cmd.name() == null) {
            // Assignment-only command — still apply output redirections
            return applyOutputRedirections(new ExecResult("", "", 0), redirects, redirectPaths);
        }

        // Make redirected stdin available to builtins like read
        String savedGroupStdin = state.groupStdin;
        state.groupStdin = redirectedStdin;

        // Expand command name
        String commandName = expansion.expandWord(cmd.name(), state, executor).get(0);

        // Check for nounset expansion error
        expansionError = checkExpansionError();
        if (expansionError != null) {
            state.groupStdin = savedGroupStdin;
            return expansionError;
        }

        // Alias expansion
        List<String> args;
        List<String> aliasExpanded = expandAlias(commandName, state);
        if (aliasExpanded != null) {
            commandName = aliasExpanded.get(0);
            // Prepend alias arguments before command arguments
            List<String> aliasArgs = aliasExpanded.size() > 1
                ? aliasExpanded.subList(1, aliasExpanded.size())
                : List.of();

            // Expand original command arguments
            List<String> originalArgs = new ArrayList<>();
            for (var argWord : cmd.args()) {
                List<String> expanded = expansion.expandWord(argWord, state, executor);
                expanded = expansion.expandBraces(expanded);
                expanded = expansion.expandGlobs(expanded, options.fs(), state);
                originalArgs.addAll(expanded);
            }

            // Combine: alias args first, then original args
            args = new ArrayList<>(aliasArgs);
            args.addAll(originalArgs);
        } else {
            // Expand arguments with brace and glob expansion
            args = new ArrayList<>();
            for (var argWord : cmd.args()) {
                List<String> expanded = expansion.expandWord(argWord, state, executor);
                expanded = expansion.expandBraces(expanded);
                expanded = expansion.expandGlobs(expanded, options.fs(), state);
                args.addAll(expanded);
            }
        }

        // Check for nounset expansion error after args expansion
        expansionError = checkExpansionError();
        if (expansionError != null) {
            state.groupStdin = savedGroupStdin;
            return expansionError;
        }

        // Update $_ (last argument of previous command)
        if (!args.isEmpty()) {
            state.lastArg = args.get(args.size() - 1);
        } else {
            state.lastArg = commandName;
        }

        // Handle exec command: apply redirections to shell state, optionally run command
        boolean isExec = commandName.equals("exec");
        if (isExec) {
            applyExecRedirections(redirects, redirectPaths);
            redirects.clear();
            redirectPaths.clear();
            if (args.isEmpty()) {
                return applyPersistentOutputRedirect(new ExecResult("", "", 0));
            }
            commandName = args.get(0);
            args = new ArrayList<>(args.subList(1, args.size()));
        }

        // xtrace: print expanded command before execution
        String xtraceOutput = "";
        if (state.options.xtrace) {
            StringBuilder trace = new StringBuilder("+ ");
            trace.append(commandName);
            for (String arg : args) {
                trace.append(" ").append(arg);
            }
            trace.append("\n");
            xtraceOutput = trace.toString();
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
        result = applyOutputRedirections(result, redirects, redirectPaths);
        result = applyPersistentOutputRedirect(result);

        // Prepend xtrace output to stderr
        if (!xtraceOutput.isEmpty()) {
            result = new ExecResult(result.stdout(), xtraceOutput + result.stderr(), result.exitCode());
        }

        // Execute pending output process substitutions
        for (var pending : new ArrayList<>(state.pendingOutputProcessSubs)) {
            try {
                String content = options.fs().readFile(pending.path()).join();
                InterpreterState saved = this.state;
                this.state = saved.copy();
                this.state.groupStdin = content;
                try {
                    executeScriptRaw(pending.body());
                } catch (ExitException | ReturnException e) {
                    // Ignore for MVP
                } finally {
                    this.state = saved;
                }
            } catch (Exception e) {
                // Ignore read errors for MVP
            }
        }
        state.pendingOutputProcessSubs.clear();

        return result;
    }

    private ExecResult applyOutputRedirections(ExecResult result,
                                               List<RedirectionNode> redirects,
                                               List<String> redirectPaths) {
        String stdout = result.stdout();
        String stderr = result.stderr();
        int exitCode = result.exitCode();

        for (int i = 0; i < redirects.size(); i++) {
            var redir = redirects.get(i);
            String resolved = redirectPaths.get(i);

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
                case GTAMP -> {
                    int fd = redir.fd().orElse(1);
                    if (resolved.equals("-")) {
                        state.fileDescriptors.remove(fd);
                        if (fd == 1) stdout = "";
                        if (fd == 2) stderr = "";
                    } else {
                        try {
                            int srcFd = Integer.parseInt(resolved);
                            if (fd == 2 && srcFd == 1) {
                                // 2>&1: merge stderr into stdout
                                stdout += stderr;
                                stderr = "";
                            } else if (fd == 1 && srcFd == 2) {
                                // >&2: merge stdout into stderr
                                stderr += stdout;
                                stdout = "";
                            } else if (fd >= 3 || srcFd >= 3) {
                                // Virtual fd handling: write to the file fd srcFd points to,
                                // but do NOT modify state.fileDescriptors (this is a temporary redirect)
                                String srcValue = state.fileDescriptors.get(srcFd);
                                if (srcValue != null && (srcValue.startsWith("w:") || srcValue.startsWith("a:") || srcValue.startsWith("rw:"))) {
                                    String path = srcValue.substring(srcValue.indexOf(':') + 1);
                                    boolean append = srcValue.startsWith("a:");
                                    if (fd == 1) {
                                        writeFile(path, stdout, append);
                                        stdout = "";
                                    } else if (fd == 2) {
                                        writeFile(path, stderr, append);
                                        stderr = "";
                                    }
                                }
                            }
                        } catch (NumberFormatException e) {
                            // ignore invalid fd
                        }
                    }
                }
                case LTAMP -> {
                    int fd = redir.fd().orElse(0);
                    if (resolved.equals("-")) {
                        state.fileDescriptors.remove(fd);
                    } else {
                        try {
                            int srcFd = Integer.parseInt(resolved);
                            String srcValue = state.fileDescriptors.get(srcFd);
                            if (srcValue != null) {
                                state.fileDescriptors.put(fd, srcValue);
                            }
                        } catch (NumberFormatException e) {
                            // ignore invalid fd
                        }
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

    private String applyPersistentInputRedirect(String stdin) {
        String fd0 = state.fileDescriptors.get(0);
        if (fd0 != null && (fd0.startsWith("r:") || fd0.startsWith("rw:"))) {
            String path = fd0.substring(fd0.indexOf(':') + 1);
            try {
                return options.fs().readFile(path).join();
            } catch (Exception e) {
                // Fall through to return original stdin
            }
        }
        return stdin;
    }

    private ExecResult applyPersistentOutputRedirect(ExecResult result) {
        String stdout = result.stdout();
        String stderr = result.stderr();
        int exitCode = result.exitCode();

        String fd1 = state.fileDescriptors.get(1);
        if (fd1 != null) {
            if (fd1.startsWith("w:")) {
                // exec >file truncated the file; subsequent writes append
                writeFile(fd1.substring(2), stdout, true);
                stdout = "";
            } else if (fd1.startsWith("a:")) {
                writeFile(fd1.substring(2), stdout, true);
                stdout = "";
            } else if (fd1.startsWith("rw:")) {
                writeFile(fd1.substring(3), stdout, true);
                stdout = "";
            }
        }

        String fd2 = state.fileDescriptors.get(2);
        if (fd2 != null) {
            if (fd2.startsWith("w:")) {
                writeFile(fd2.substring(2), stderr, true);
                stderr = "";
            } else if (fd2.startsWith("a:")) {
                writeFile(fd2.substring(2), stderr, true);
                stderr = "";
            } else if (fd2.startsWith("rw:")) {
                writeFile(fd2.substring(3), stderr, false);
                stderr = "";
            }
        }

        return new ExecResult(stdout, stderr, exitCode);
    }

    private void applyExecRedirections(List<RedirectionNode> redirects, List<String> redirectPaths) {
        for (int i = 0; i < redirects.size(); i++) {
            var redir = redirects.get(i);
            String target = redirectPaths.get(i);

            switch (redir.operator()) {
                case GT -> {
                    int fd = redir.fd().orElse(1);
                    state.fileDescriptors.put(fd, "w:" + target);
                    // Truncate file immediately so subsequent commands append
                    writeFile(target, "", false);
                }
                case GTGT -> {
                    int fd = redir.fd().orElse(1);
                    state.fileDescriptors.put(fd, "a:" + target);
                }
                case LT -> {
                    int fd = redir.fd().orElse(0);
                    state.fileDescriptors.put(fd, "r:" + target);
                }
                case GTAMP, LTAMP -> {
                    int fd = redir.fd().orElse(redir.operator() == RedirectionNode.RedirectionOperator.GTAMP ? 1 : 0);
                    if (target.equals("-")) {
                        state.fileDescriptors.remove(fd);
                    } else {
                        try {
                            int srcFd = Integer.parseInt(target);
                            String src = state.fileDescriptors.get(srcFd);
                            if (src != null) {
                                state.fileDescriptors.put(fd, src);
                            }
                        } catch (NumberFormatException e) {
                            // ignore invalid fd
                        }
                    }
                }
                case LTGT -> {
                    int fd = redir.fd().orElse(0);
                    state.fileDescriptors.put(fd, "rw:" + target);
                }
                default -> { /* ignore */ }
            }
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

    private ExecResult executeConditionalCommand(ConditionalCommandNode cond) {
        ConditionalExpressionEvaluator eval = new ConditionalExpressionEvaluator(state, options.fs());
        boolean truthy = eval.evaluate(cond.expression());
        int exitCode = truthy ? 0 : 1;
        state.lastExitCode = exitCode;
        state.env.put("?", String.valueOf(exitCode));
        return new ExecResult("", "", exitCode);
    }

    private ExecResult callFunction(FunctionDefNode func, List<String> args) {
        state.callDepth++;
        if (state.callDepth > options.limits().maxCallDepth()) {
            throw new ExecutionLimitException(
                func.name() + ": maximum recursion depth (" + options.limits().maxCallDepth() +
                    ") exceeded, increase executionLimits.maxCallDepth",
                "maxCallDepth");
        }
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

    private ExecResult checkExpansionError() {
        if (state.expansionExitCode != null) {
            int code = state.expansionExitCode;
            String stderr = state.expansionStderr;
            state.expansionExitCode = null;
            state.expansionStderr = "";
            return new ExecResult("", stderr, code);
        }
        return null;
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

    private void setAssociativeArrayElement(String name, String key, String value) {
        Map<String, String> arr = state.associativeArrayData.get(name);
        if (arr == null) {
            arr = new LinkedHashMap<>();
            state.associativeArrayData.put(name, arr);
        }
        arr.put(key, value);
    }

    private List<String> expandAlias(String commandName, InterpreterState state) {
        if (!state.shoptOptions.expand_aliases) {
            return null;
        }
        String aliasValue = state.aliases.get(commandName);
        if (aliasValue == null) {
            return null;
        }
        if (state.expandingAliases.contains(commandName)) {
            return null;
        }
        state.expandingAliases.add(commandName);
        try {
            // Split alias value by whitespace, filtering empty strings
            List<String> parts = new ArrayList<>();
            for (String part : aliasValue.split("\\s+")) {
                if (!part.isEmpty()) {
                    parts.add(part);
                }
            }
            return parts.isEmpty() ? null : parts;
        } finally {
            state.expandingAliases.remove(commandName);
        }
    }
}
