package com.justbash.interpreter;

import com.justbash.ExecResult;
import com.justbash.ast.ScriptNode;
import com.justbash.fs.FsStat;
import com.justbash.fs.IFileSystem;
import com.justbash.interpreter.errors.BreakException;
import com.justbash.interpreter.errors.ContinueException;
import com.justbash.interpreter.errors.ExitException;
import com.justbash.interpreter.errors.ParseException;
import com.justbash.interpreter.errors.ReturnException;
import com.justbash.parser.ArithmeticParser;
import com.justbash.parser.Parser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public class BuiltinDispatcher {

    private final Interpreter interpreter;
    private final IFileSystem fs;

    public BuiltinDispatcher(Interpreter interpreter) {
        this.interpreter = interpreter;
        this.fs = interpreter.getFs();
    }

    public Optional<ExecResult> dispatch(String name, List<String> args, InterpreterState state) {
        return switch (name) {
            case "echo" -> Optional.of(handleEcho(args));
            case "true", ":" -> Optional.of(new ExecResult("", "", 0));
            case "false" -> Optional.of(new ExecResult("", "", 1));
            case "cd" -> Optional.of(handleCd(args, state));
            case "pwd" -> Optional.of(handlePwd(args, state));
            case "export" -> Optional.of(handleExport(args, state));
            case "unset" -> Optional.of(handleUnset(args, state));
            case "exit" -> Optional.of(handleExit(args, state));
            case "eval" -> Optional.of(handleEval(args, state));
            case "source", "." -> Optional.of(handleSource(args, state));
            case "local" -> Optional.of(handleLocal(args, state));
            case "declare", "typeset" -> Optional.of(handleDeclare(args, state));
            case "read" -> Optional.of(handleRead(args, state));
            case "break" -> Optional.of(handleBreak(args, state));
            case "continue" -> Optional.of(handleContinue(args, state));
            case "return" -> Optional.of(handleReturn(args, state));
            case "shift" -> Optional.of(handleShift(args, state));
            case "test" -> Optional.of(handleTest(args, state, false));
            case "[" -> Optional.of(handleTest(args, state, true));
            case "set" -> Optional.of(handleSet(args, state));
            case "shopt" -> Optional.of(handleShopt(args, state));
            case "alias" -> Optional.of(handleAlias(args, state));
            case "unalias" -> Optional.of(handleUnalias(args, state));
            case "readonly" -> Optional.of(handleReadonly(args, state));
            case "pushd" -> Optional.of(handlePushd(args, state));
            case "popd" -> Optional.of(handlePopd(args, state));
            case "dirs" -> Optional.of(handleDirs(args, state));
            case "let" -> Optional.of(handleLet(args, state));
            case "getopts" -> Optional.of(handleGetopts(args, state));
            case "trap" -> Optional.of(handleTrap(args, state));
            case "kill" -> Optional.of(handleKill(args, state));
            case "umask" -> Optional.of(handleUmask(args, state));
            case "times" -> Optional.of(handleTimes(args, state));
            case "history" -> Optional.of(handleHistory(args, state));
            case "type" -> Optional.of(handleType(args, state));
            case "command" -> Optional.of(handleCommand(args, state));
            case "hash" -> Optional.of(handleHash(args, state));
            case "builtin" -> Optional.of(handleBuiltin(args, state));
            case "mapfile", "readarray" -> Optional.of(handleMapfile(args, state));
            case "printf" -> Optional.of(handlePrintf(args, state));
            case "jobs", "fg", "bg", "ulimit", "logout", "caller",
                 "suspend", "disown", "enable", "bind", "wait",
                 "compgen", "complete", "compopt", "help" ->
                Optional.of(new ExecResult("", "bash: " + name + ": not supported in this environment\n", 1));
            default -> Optional.empty();
        };
    }

    private ExecResult handleEcho(List<String> args) {
        String output = String.join(" ", args) + "\n";
        return new ExecResult(output, "", 0);
    }

    private ExecResult handleCd(List<String> args, InterpreterState state) {
        int i = 0;
        while (i < args.size()) {
            String arg = args.get(i);
            if (arg.equals("--")) {
                i++;
                break;
            }
            if (arg.equals("-L") || arg.equals("-P")) {
                i++;
            } else if (arg.startsWith("-") && !arg.equals("-")) {
                i++;
            } else {
                break;
            }
        }

        List<String> remaining = args.subList(i, args.size());
        String target;
        boolean printPath = false;

        if (remaining.isEmpty()) {
            target = state.env.getOrDefault("HOME", "/");
        } else if (remaining.get(0).equals("-")) {
            target = state.previousDir;
            printPath = true;
            if (target == null || target.isEmpty()) {
                return new ExecResult("", "bash: cd: OLDPWD not set\n", 1);
            }
        } else if (remaining.get(0).startsWith("~")) {
            String home = state.env.getOrDefault("HOME", "/");
            target = home + remaining.get(0).substring(1);
        } else {
            target = remaining.get(0);
        }

        String resolved = resolveCdPath(target, state);

        // Check if directory exists using filesystem
        try {
            FsStat stat = fs.statSync(resolved);
            if (!stat.isDirectory()) {
                return new ExecResult("", "bash: cd: " + target + ": Not a directory\n", 1);
            }
        } catch (Exception e) {
            return new ExecResult("", "bash: cd: " + target + ": No such file or directory\n", 1);
        }

        state.previousDir = state.cwd;
        state.cwd = resolved;
        state.env.put("PWD", resolved);
        state.env.put("OLDPWD", state.previousDir);

        String stdout = printPath ? resolved + "\n" : "";
        return new ExecResult(stdout, "", 0);
    }

    private String resolveCdPath(String target, InterpreterState state) {
        if (target.startsWith("/")) {
            return target;
        }
        return state.cwd + "/" + target;
    }

    private ExecResult handlePwd(List<String> args, InterpreterState state) {
        return new ExecResult(state.cwd + "\n", "", 0);
    }

    private ExecResult handleExport(List<String> args, InterpreterState state) {
        boolean unexport = false;
        List<String> processed = new ArrayList<>();

        for (String arg : args) {
            if (arg.equals("-n")) {
                unexport = true;
            } else if (arg.equals("-p") || arg.equals("--")) {
                // no-op for MVP
            } else if (arg.startsWith("-") && !arg.equals("-")) {
                // ignore unknown options for MVP
            } else {
                processed.add(arg);
            }
        }

        if (processed.isEmpty() && !unexport) {
            StringBuilder stdout = new StringBuilder();
            List<String> sorted = new ArrayList<>(state.exportedVars);
            Collections.sort(sorted);
            for (String name : sorted) {
                String value = state.env.get(name);
                if (value != null) {
                    String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
                    stdout.append("declare -x ").append(name).append("=\"").append(escaped).append("\"\n");
                }
            }
            return new ExecResult(stdout.toString(), "", 0);
        }

        if (unexport) {
            for (String arg : processed) {
                String name = arg.contains("=") ? arg.substring(0, arg.indexOf("=")) : arg;
                if (arg.contains("=")) {
                    String value = arg.substring(arg.indexOf("=") + 1);
                    state.env.put(name, value);
                }
                state.exportedVars.remove(name);
            }
            return new ExecResult("", "", 0);
        }

        String stderr = "";
        int exitCode = 0;
        for (String arg : processed) {
            String name;
            String value;

            if (arg.contains("+=")) {
                int idx = arg.indexOf("+=");
                name = arg.substring(0, idx);
                value = arg.substring(idx + 2);
                String existing = state.env.getOrDefault(name, "");
                value = existing + value;
            } else if (arg.contains("=")) {
                int idx = arg.indexOf("=");
                name = arg.substring(0, idx);
                value = arg.substring(idx + 1);
            } else {
                name = arg;
                value = state.env.get(name);
            }

            if (value != null) {
                state.env.put(name, value);
            }
            state.exportedVars.add(name);
        }
        return new ExecResult("", stderr, exitCode);
    }

    private ExecResult handleUnset(List<String> args, InterpreterState state) {
        boolean unsetFunctions = false;
        List<String> names = new ArrayList<>();

        for (String arg : args) {
            if (arg.equals("-f")) {
                unsetFunctions = true;
            } else if (arg.equals("-v")) {
                unsetFunctions = false;
            } else if (arg.equals("--")) {
                // no-op
            } else if (arg.startsWith("-") && !arg.equals("-")) {
                // ignore unknown options
            } else {
                names.add(arg);
            }
        }

        StringBuilder stderr = new StringBuilder();
        for (String name : names) {
            if (state.readonlyVars.contains(name)) {
                stderr.append("bash: unset: ").append(name).append(": cannot unset: readonly variable\n");
                continue;
            }
            if (unsetFunctions) {
                state.functions.remove(name);
            } else {
                state.env.remove(name);
                state.exportedVars.remove(name);
                state.readonlyVars.remove(name);
                state.integerVars.remove(name);
                state.associativeArrays.remove(name);
                state.namerefs.remove(name);
                state.lowercaseVars.remove(name);
                state.uppercaseVars.remove(name);
                state.declaredVars.remove(name);
            }
        }
        int exitCode = stderr.isEmpty() ? 0 : 1;
        return new ExecResult("", stderr.toString(), exitCode);
    }

    private ExecResult handleExit(List<String> args, InterpreterState state) {
        int exitCode;
        String stderr = "";

        if (args.isEmpty()) {
            exitCode = state.lastExitCode;
        } else {
            String arg = args.get(0);
            try {
                int parsed = Integer.parseInt(arg);
                exitCode = ((parsed % 256) + 256) % 256;
            } catch (NumberFormatException e) {
                stderr = "bash: exit: " + arg + ": numeric argument required\n";
                exitCode = 2;
            }
        }

        throw new ExitException(exitCode, "", stderr);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phase 5B: eval, source, local, declare, read
    // ─────────────────────────────────────────────────────────────────────────

    private ExecResult handleEval(List<String> args, InterpreterState state) {
        // Handle options: -- ends option processing
        List<String> evalArgs = new ArrayList<>(args);
        if (!evalArgs.isEmpty()) {
            String first = evalArgs.get(0);
            if (first.equals("--")) {
                evalArgs = evalArgs.subList(1, evalArgs.size());
            } else if (first.startsWith("-") && !first.equals("-") && first.length() > 1) {
                return new ExecResult("",
                    "bash: eval: " + first + ": invalid option\neval: usage: eval [arg ...]\n", 2);
            }
        }

        if (evalArgs.isEmpty()) {
            return new ExecResult("", "", 0);
        }

        String command = String.join(" ", evalArgs);
        if (command.trim().isEmpty()) {
            return new ExecResult("", "", 0);
        }

        // Parse and execute — let ExitException / ReturnException propagate
        try {
            ScriptNode ast = Parser.parse(command);
            return interpreter.executeScriptRaw(ast);
        } catch (ParseException e) {
            return new ExecResult("", "bash: syntax error: " + e.getMessage() + "\n", 2);
        }
    }

    private ExecResult handleSource(List<String> args, InterpreterState state) {
        List<String> sourceArgs = new ArrayList<>(args);
        if (!sourceArgs.isEmpty() && sourceArgs.get(0).equals("--")) {
            sourceArgs = sourceArgs.subList(1, sourceArgs.size());
        }

        if (sourceArgs.isEmpty()) {
            return new ExecResult("", "bash: source: filename argument required\n", 2);
        }

        String filename = sourceArgs.get(0);
        String path = filename.startsWith("/") ? filename : state.cwd + "/" + filename;

        try {
            String content = fs.readFile(path).join();
            ScriptNode ast = Parser.parse(content);
            return interpreter.executeScriptRaw(ast);
        } catch (Exception e) {
            return new ExecResult("", "bash: " + filename + ": No such file or directory\n", 1);
        }
    }

    private ExecResult handleLocal(List<String> args, InterpreterState state) {
        if (state.localScopes.isEmpty()) {
            return new ExecResult("", "bash: local: can only be used in a function\n", 1);
        }

        Map<String, String> currentScope = state.localScopes.get(state.localScopes.size() - 1);
        String stderr = "";
        int exitCode = 0;

        for (String arg : args) {
            if (arg.startsWith("-") && !arg.equals("-")) {
                // Ignore flags for MVP
                continue;
            }

            String name;
            String value;
            if (arg.contains("=")) {
                int idx = arg.indexOf("=");
                name = arg.substring(0, idx);
                value = arg.substring(idx + 1);
            } else {
                name = arg;
                value = null;
            }

            // Save current value to local scope (only on first local declaration in this scope)
            if (!currentScope.containsKey(name)) {
                String currentValue = state.env.get(name);
                currentScope.put(name, currentValue != null ? currentValue : "__UNSET__");
            }

            // Set new value
            if (value != null) {
                state.env.put(name, value);
            }
        }

        return new ExecResult("", stderr, exitCode);
    }

    private ExecResult handleDeclare(List<String> args, InterpreterState state) {
        boolean exportFlag = false;
        boolean readonlyFlag = false;
        boolean integerFlag = false;
        boolean associativeFlag = false;
        boolean indexedFlag = false;
        boolean printFlag = false;
        List<String> processed = new ArrayList<>();

        for (String arg : args) {
            if (arg.equals("-x")) {
                exportFlag = true;
            } else if (arg.equals("-r")) {
                readonlyFlag = true;
            } else if (arg.equals("-i")) {
                integerFlag = true;
            } else if (arg.equals("-A")) {
                associativeFlag = true;
            } else if (arg.equals("-a")) {
                indexedFlag = true;
            } else if (arg.equals("-p")) {
                printFlag = true;
            } else if (arg.startsWith("-") && !arg.equals("-")) {
                // Handle combined flags like -xr, -ri, etc.
                for (char c : arg.substring(1).toCharArray()) {
                    if (c == 'x') exportFlag = true;
                    else if (c == 'r') readonlyFlag = true;
                    else if (c == 'i') integerFlag = true;
                    else if (c == 'A') associativeFlag = true;
                    else if (c == 'a') indexedFlag = true;
                    else if (c == 'p') printFlag = true;
                }
            } else {
                processed.add(arg);
            }
        }

        if (printFlag && !processed.isEmpty()) {
            StringBuilder stdout = new StringBuilder();
            for (String name : processed) {
                String attrs = "";
                if (state.readonlyVars.contains(name)) attrs += " -r";
                if (state.integerVars.contains(name)) attrs += " -i";
                if (state.exportedVars.contains(name)) attrs += " -x";
                if (state.associativeArrays.contains(name)) attrs += " -A";
                if (state.indexedArrays.containsKey(name) && !state.associativeArrays.contains(name)) attrs += " -a";
                if (attrs.isEmpty()) attrs = " --";
                stdout.append("declare").append(attrs).append(" ").append(name);
                if (state.associativeArrays.contains(name)) {
                    stdout.append("=");
                    Map<String, String> data = state.associativeArrayData.getOrDefault(name, Map.of());
                    if (data.isEmpty()) {
                        stdout.append("()");
                    } else {
                        stdout.append("(");
                        boolean first = true;
                        for (Map.Entry<String, String> entry : data.entrySet()) {
                            if (!first) stdout.append(" ");
                            stdout.append("[").append(entry.getKey()).append("]=\"").append(entry.getValue()).append("\"");
                            first = false;
                        }
                        stdout.append(")");
                    }
                } else {
                    String value = state.env.getOrDefault(name, "");
                    stdout.append("=\"").append(value).append("\"");
                }
                stdout.append("\n");
            }
            return new ExecResult(stdout.toString(), "", 0);
        }

        if (processed.isEmpty()) {
            // Print all variables
            StringBuilder stdout = new StringBuilder();
            List<String> sorted = new ArrayList<>(state.env.keySet());
            Collections.sort(sorted);
            for (String name : sorted) {
                if (name.equals("?")) continue; // skip special $?
                String value = state.env.get(name);
                String attrs = "";
                if (state.readonlyVars.contains(name)) attrs += " -r";
                if (state.integerVars.contains(name)) attrs += " -i";
                if (state.exportedVars.contains(name)) attrs += " -x";
                if (state.associativeArrays.contains(name)) attrs += " -A";
                if (state.indexedArrays.containsKey(name) && !state.associativeArrays.contains(name)) attrs += " -a";
                if (attrs.isEmpty()) attrs = " --";
                stdout.append("declare").append(attrs).append(" ").append(name).append("=\"").append(value).append("\"\n");
            }
            return new ExecResult(stdout.toString(), "", 0);
        }

        for (String arg : processed) {
            String name;
            String value;
            if (arg.contains("=")) {
                int idx = arg.indexOf("=");
                name = arg.substring(0, idx);
                value = arg.substring(idx + 1);
            } else {
                name = arg;
                value = state.env.get(name);
            }

            if (associativeFlag) {
                state.associativeArrays.add(name);
                state.associativeArrayData.putIfAbsent(name, new LinkedHashMap<>());
            }
            if (indexedFlag) {
                state.indexedArrays.putIfAbsent(name, new ArrayList<>());
            }
            if (value != null) {
                state.env.put(name, value);
            }
            if (exportFlag) state.exportedVars.add(name);
            if (readonlyFlag) state.readonlyVars.add(name);
            if (integerFlag) state.integerVars.add(name);
        }

        return new ExecResult("", "", 0);
    }

    private ExecResult handleRead(List<String> args, InterpreterState state) {
        boolean raw = false;
        List<String> varNames = new ArrayList<>();

        for (String arg : args) {
            if (arg.equals("-r")) {
                raw = true;
            } else if (arg.startsWith("-") && !arg.equals("-")) {
                // Ignore other options for MVP
            } else {
                varNames.add(arg);
            }
        }

        if (varNames.isEmpty()) {
            varNames.add("REPLY");
        }

        // Get stdin from groupStdin or empty
        String stdin = state.groupStdin != null ? state.groupStdin : "";

        // Find newline delimiter
        int newlineIdx = stdin.indexOf('\n');
        String line;
        if (newlineIdx >= 0) {
            line = stdin.substring(0, newlineIdx);
            state.groupStdin = stdin.substring(newlineIdx + 1);
        } else {
            line = stdin;
            state.groupStdin = "";
        }

        // Handle backslash continuation unless -r
        if (!raw && line.endsWith("\\")) {
            line = line.substring(0, line.length() - 1);
        }

        // Split by IFS
        String ifs = state.env.getOrDefault("IFS", " \t\n");
        String[] fields;
        if (ifs.isEmpty()) {
            // Empty IFS: no splitting, entire line goes to first variable
            fields = new String[] { line };
        } else if (ifs.contains(" ") && ifs.contains("\t")) {
            fields = line.split("[ \\t]+", varNames.size());
        } else {
            String regex = "[" + Pattern.quote(ifs) + "]+";
            fields = line.split(regex, varNames.size());
        }

        // Assign to variables
        for (int i = 0; i < varNames.size(); i++) {
            String value;
            if (i < fields.length) {
                if (i == varNames.size() - 1) {
                    // Last variable gets all remaining fields
                    StringBuilder remaining = new StringBuilder();
                    for (int j = i; j < fields.length; j++) {
                        if (j > i) remaining.append(" ");
                        remaining.append(fields[j]);
                    }
                    value = remaining.toString();
                } else {
                    value = fields[i];
                }
            } else {
                value = "";
            }
            state.env.put(varNames.get(i), value);
        }

        return new ExecResult("", "", 0);
    }

    private ExecResult handleBreak(List<String> args, InterpreterState state) {
        if (state.loopDepth == 0) {
            return new ExecResult("", "bash: break: only meaningful in a loop\n", 1);
        }
        int levels = 1;
        if (!args.isEmpty()) {
            try {
                levels = Integer.parseInt(args.get(0));
            } catch (NumberFormatException e) {
                return new ExecResult("", "bash: break: " + args.get(0) + ": numeric argument required\n", 1);
            }
        }
        if (levels < 1) levels = 1;
        throw new BreakException(levels);
    }

    private ExecResult handleContinue(List<String> args, InterpreterState state) {
        if (state.loopDepth == 0) {
            return new ExecResult("", "bash: continue: only meaningful in a loop\n", 1);
        }
        int levels = 1;
        if (!args.isEmpty()) {
            try {
                levels = Integer.parseInt(args.get(0));
            } catch (NumberFormatException e) {
                return new ExecResult("", "bash: continue: " + args.get(0) + ": numeric argument required\n", 1);
            }
        }
        if (levels < 1) levels = 1;
        throw new ContinueException(levels);
    }

    private ExecResult handleReturn(List<String> args, InterpreterState state) {
        if (state.callDepth == 0) {
            return new ExecResult("", "bash: return: can only `return' from a function or sourced script\n", 1);
        }
        int exitCode = state.lastExitCode;
        if (!args.isEmpty()) {
            try {
                exitCode = Integer.parseInt(args.get(0));
            } catch (NumberFormatException e) {
                return new ExecResult("", "bash: return: " + args.get(0) + ": numeric argument required\n", 2);
            }
        }
        throw new ReturnException(exitCode);
    }

    private ExecResult handleTest(List<String> args, InterpreterState state, boolean bracketMode) {
        int exitCode = TestCommandEvaluator.evaluate(args, fs, state, bracketMode);
        if (exitCode == 2) {
            String msg = bracketMode
                ? "bash: [: missing `]'\n"
                : "bash: test: missing argument\n";
            return new ExecResult("", msg, 2);
        }
        return new ExecResult("", "", exitCode);
    }

    private ExecResult handleSet(List<String> args, InterpreterState state) {
        if (args.isEmpty()) {
            StringBuilder stdout = new StringBuilder();
            List<String> sorted = new ArrayList<>(state.env.keySet());
            Collections.sort(sorted);
            for (String name : sorted) {
                if (name.equals("?")) continue;
                String value = state.env.get(name);
                if (value != null) {
                    stdout.append(name).append("=").append(value).append("\n");
                }
            }
            return new ExecResult(stdout.toString(), "", 0);
        }

        int i = 0;
        boolean setPositional = false;
        List<String> positionalArgs = new ArrayList<>();

        while (i < args.size()) {
            String arg = args.get(i);

            if (arg.equals("--")) {
                setPositional = true;
                positionalArgs = new ArrayList<>(args.subList(i + 1, args.size()));
                break;
            }

            if (arg.equals("-")) {
                setPositional = true;
                positionalArgs = new ArrayList<>(args.subList(i + 1, args.size()));
                break;
            }

            if (arg.startsWith("-") && arg.length() > 1) {
                if (arg.equals("-o")) {
                    i++;
                    if (i >= args.size()) {
                        return new ExecResult("", "bash: set: -o: option requires an argument\n", 2);
                    }
                    String optName = args.get(i);
                    setOptionByName(optName, true, state);
                } else {
                    for (char c : arg.substring(1).toCharArray()) {
                        setOptionByChar(c, true, state);
                    }
                }
            } else if (arg.startsWith("+") && arg.length() > 1) {
                if (arg.equals("+o")) {
                    i++;
                    if (i >= args.size()) {
                        return new ExecResult("", "bash: set: +o: option requires an argument\n", 2);
                    }
                    String optName = args.get(i);
                    setOptionByName(optName, false, state);
                } else {
                    for (char c : arg.substring(1).toCharArray()) {
                        setOptionByChar(c, false, state);
                    }
                }
            } else {
                setPositional = true;
                positionalArgs = new ArrayList<>(args.subList(i, args.size()));
                break;
            }
            i++;
        }

        if (setPositional) {
            int oldCount;
            try {
                oldCount = Integer.parseInt(state.env.getOrDefault("#", "0"));
            } catch (NumberFormatException e) {
                oldCount = 0;
            }
            for (int j = 1; j <= oldCount; j++) {
                state.env.remove(String.valueOf(j));
            }
            for (int j = 0; j < positionalArgs.size(); j++) {
                state.env.put(String.valueOf(j + 1), positionalArgs.get(j));
            }
            state.env.put("#", String.valueOf(positionalArgs.size()));
            state.env.put("@", String.join(" ", positionalArgs));
        }

        updateShelOpts(state);
        return new ExecResult("", "", 0);
    }

    private void setOptionByChar(char c, boolean enable, InterpreterState state) {
        switch (c) {
            case 'e' -> state.options.errexit = enable;
            case 'u' -> state.options.nounset = enable;
            case 'x' -> state.options.xtrace = enable;
            case 'v' -> state.options.verbose = enable;
            case 'f' -> state.options.noglob = enable;
            case 'C' -> state.options.noclobber = enable;
            case 'a' -> state.options.allexport = enable;
            case 'n' -> state.options.noexec = enable;
        }
    }

    private void setOptionByName(String name, boolean enable, InterpreterState state) {
        switch (name) {
            case "errexit" -> state.options.errexit = enable;
            case "nounset" -> state.options.nounset = enable;
            case "xtrace" -> state.options.xtrace = enable;
            case "verbose" -> state.options.verbose = enable;
            case "noglob" -> state.options.noglob = enable;
            case "noclobber" -> state.options.noclobber = enable;
            case "allexport" -> state.options.allexport = enable;
            case "noexec" -> state.options.noexec = enable;
            case "pipefail" -> state.options.pipefail = enable;
            case "posix" -> state.options.posix = enable;
        }
    }

    private void updateShelOpts(InterpreterState state) {
        StringBuilder sb = new StringBuilder();
        ShellOptions opts = state.options;
        if (opts.allexport) sb.append(":allexport");
        if (opts.errexit) sb.append(":errexit");
        if (opts.noclobber) sb.append(":noclobber");
        if (opts.noexec) sb.append(":noexec");
        if (opts.noglob) sb.append(":noglob");
        if (opts.nounset) sb.append(":nounset");
        if (opts.pipefail) sb.append(":pipefail");
        if (opts.posix) sb.append(":posix");
        if (opts.verbose) sb.append(":verbose");
        if (opts.xtrace) sb.append(":xtrace");
        String value = sb.isEmpty() ? "" : sb.substring(1);
        state.env.put("SHELLOPTS", value);
    }

    private ExecResult handleShift(List<String> args, InterpreterState state) {
        int shiftCount = 1;
        if (!args.isEmpty()) {
            try {
                shiftCount = Integer.parseInt(args.get(0));
            } catch (NumberFormatException e) {
                return new ExecResult("", "bash: shift: " + args.get(0) + ": numeric argument required\n", 2);
            }
        }

        if (shiftCount < 0) {
            return new ExecResult("", "bash: shift: " + args.get(0) + ": shift count out of range\n", 2);
        }

        int paramCount;
        try {
            paramCount = Integer.parseInt(state.env.getOrDefault("#", "0"));
        } catch (NumberFormatException e) {
            paramCount = 0;
        }

        if (shiftCount > paramCount) {
            return new ExecResult("", "bash: shift: shift count out of range\n", 1);
        }

        // Shift positional params: remove 1..shiftCount, move shiftCount+1..N to 1..N-shiftCount
        List<String> newParams = new ArrayList<>();
        for (int i = shiftCount + 1; i <= paramCount; i++) {
            String key = String.valueOf(i);
            String value = state.env.get(key);
            newParams.add(value != null ? value : "");
            state.env.remove(key);
        }

        // Clean up old keys that are now beyond the new count
        for (int i = 1; i <= paramCount; i++) {
            state.env.remove(String.valueOf(i));
        }

        // Set new positional params
        for (int i = 0; i < newParams.size(); i++) {
            state.env.put(String.valueOf(i + 1), newParams.get(i));
        }

        int newCount = newParams.size();
        state.env.put("#", String.valueOf(newCount));
        state.env.put("@", String.join(" ", newParams));

        return new ExecResult("", "", 0);
    }

    private ExecResult handleShopt(List<String> args, InterpreterState state) {
        boolean setFlag = false;
        boolean unsetFlag = false;
        boolean queryFlag = false;
        List<String> opts = new ArrayList<>();

        for (String arg : args) {
            if (arg.equals("-s")) {
                setFlag = true;
            } else if (arg.equals("-u")) {
                unsetFlag = true;
            } else if (arg.equals("-q")) {
                queryFlag = true;
            } else if (!arg.startsWith("-")) {
                opts.add(arg);
            }
        }

        if (opts.isEmpty() && !queryFlag) {
            // Print all options
            StringBuilder stdout = new StringBuilder();
            ShoptOptions shopt = state.shoptOptions;
            stdout.append("dotglob").append(shopt.dotglob ? "\ton\n" : "\t off\n");
            stdout.append("extglob").append(shopt.extglob ? "\ton\n" : "\t off\n");
            stdout.append("failglob").append(shopt.failglob ? "\ton\n" : "\t off\n");
            stdout.append("globstar").append(shopt.globstar ? "\ton\n" : "\t off\n");
            stdout.append("nullglob").append(shopt.nullglob ? "\ton\n" : "\t off\n");
            return new ExecResult(stdout.toString(), "", 0);
        }

        for (String opt : opts) {
            boolean value = setFlag || (!unsetFlag && !queryFlag);
            switch (opt) {
                case "dotglob" -> state.shoptOptions.dotglob = value;
                case "extglob" -> state.shoptOptions.extglob = value;
                case "failglob" -> state.shoptOptions.failglob = value;
                case "globstar" -> state.shoptOptions.globstar = value;
                case "nullglob" -> state.shoptOptions.nullglob = value;
                case "nocaseglob" -> state.shoptOptions.nocaseglob = value;
                case "expand_aliases" -> state.shoptOptions.expand_aliases = value;
            }
        }

        return new ExecResult("", "", 0);
    }

    // ------------------------------------------------------------------
    // Alias / Unalias
    // ------------------------------------------------------------------

    private ExecResult handleAlias(List<String> args, InterpreterState state) {
        if (args.isEmpty()) {
            // Print all aliases
            StringBuilder stdout = new StringBuilder();
            List<String> sorted = new ArrayList<>(state.aliases.keySet());
            Collections.sort(sorted);
            for (String name : sorted) {
                String value = state.aliases.get(name);
                stdout.append("alias ").append(name).append("='").append(value).append("'\n");
            }
            return new ExecResult(stdout.toString(), "", 0);
        }

        StringBuilder stderr = new StringBuilder();
        int exitCode = 0;

        for (String arg : args) {
            if (arg.equals("-p")) {
                // Print all aliases in reusable format
                StringBuilder stdout = new StringBuilder();
                List<String> sorted = new ArrayList<>(state.aliases.keySet());
                Collections.sort(sorted);
                for (String name : sorted) {
                    String value = state.aliases.get(name);
                    stdout.append("alias ").append(name).append("='").append(value).append("'\n");
                }
                return new ExecResult(stdout.toString(), "", 0);
            }

            int eqIdx = arg.indexOf('=');
            if (eqIdx >= 0) {
                String name = arg.substring(0, eqIdx);
                String value = arg.substring(eqIdx + 1);
                // Remove surrounding quotes if present
                if ((value.startsWith("'") && value.endsWith("'"))
                    || (value.startsWith("\"") && value.endsWith("\""))) {
                    value = value.substring(1, value.length() - 1);
                }
                state.aliases.put(name, value);
            } else {
                String value = state.aliases.get(arg);
                if (value != null) {
                    return new ExecResult("alias " + arg + "='" + value + "'\n", "", 0);
                } else {
                    stderr.append("bash: alias: ").append(arg).append(": not found\n");
                    exitCode = 1;
                }
            }
        }

        return new ExecResult("", stderr.toString(), exitCode);
    }

    private ExecResult handleUnalias(List<String> args, InterpreterState state) {
        boolean all = false;
        List<String> names = new ArrayList<>();

        for (String arg : args) {
            if (arg.equals("-a")) {
                all = true;
            } else if (arg.startsWith("-") && !arg.equals("-")) {
                return new ExecResult("", "bash: unalias: " + arg + ": invalid option\n", 2);
            } else {
                names.add(arg);
            }
        }

        if (all) {
            state.aliases.clear();
            return new ExecResult("", "", 0);
        }

        StringBuilder stderr = new StringBuilder();
        int exitCode = 0;
        for (String name : names) {
            if (state.aliases.remove(name) == null) {
                stderr.append("bash: unalias: ").append(name).append(": not found\n");
                exitCode = 1;
            }
        }
        return new ExecResult("", stderr.toString(), exitCode);
    }

    private ExecResult handleReadonly(List<String> args, InterpreterState state) {
        boolean printFlag = false;
        List<String> processed = new ArrayList<>();

        for (String arg : args) {
            if (arg.equals("-p")) {
                printFlag = true;
            } else if (arg.startsWith("-") && !arg.equals("-")) {
                // readonly accepts -a, -A, -f, -i, -t, -x but for MVP we just track -p
                // Ignore other flags
            } else {
                processed.add(arg);
            }
        }

        if (processed.isEmpty() || printFlag) {
            // Print all readonly variables
            StringBuilder stdout = new StringBuilder();
            List<String> sorted = new ArrayList<>(state.readonlyVars);
            Collections.sort(sorted);
            for (String name : sorted) {
                String value = state.env.getOrDefault(name, "");
                stdout.append("declare -r ").append(name).append("=\"").append(value).append("\"\n");
            }
            return new ExecResult(stdout.toString(), "", 0);
        }

        StringBuilder stderr = new StringBuilder();
        int exitCode = 0;

        for (String arg : processed) {
            String name;
            String value;
            if (arg.contains("=")) {
                int idx = arg.indexOf("=");
                name = arg.substring(0, idx);
                value = arg.substring(idx + 1);
            } else {
                name = arg;
                value = state.env.get(name);
            }

            if (value != null) {
                state.env.put(name, value);
            }
            state.readonlyVars.add(name);
        }

        return new ExecResult("", stderr.toString(), exitCode);
    }

    private ExecResult handlePushd(List<String> args, InterpreterState state) {
        if (args.isEmpty()) {
            // Swap current directory with top of stack
            if (state.directoryStack.isEmpty()) {
                return new ExecResult("", "bash: pushd: no other directory\n", 1);
            }
            String top = state.directoryStack.get(0);
            state.directoryStack.set(0, state.cwd);
            state.cwd = top;
            return new ExecResult(buildDirsOutput(state), "", 0);
        }

        String dir = args.get(0);
        if (dir.startsWith("+")) {
            // Rotate: bring Nth directory to top
            try {
                int n = Integer.parseInt(dir.substring(1));
                if (n < 0 || n >= state.directoryStack.size()) {
                    return new ExecResult("", "bash: pushd: " + dir + ": invalid argument\n", 1);
                }
                String target = state.directoryStack.remove(n);
                state.directoryStack.add(0, target);
                state.cwd = target;
                return new ExecResult(buildDirsOutput(state), "", 0);
            } catch (NumberFormatException e) {
                return new ExecResult("", "bash: pushd: " + dir + ": invalid argument\n", 1);
            }
        }

        // Resolve and change directory
        String resolved = resolveCdPath(dir, state);
        if (resolved == null) {
            return new ExecResult("", "bash: pushd: " + dir + ": No such file or directory\n", 1);
        }
        state.directoryStack.add(0, state.cwd);
        state.cwd = resolved;
        return new ExecResult(buildDirsOutput(state), "", 0);
    }

    private ExecResult handlePopd(List<String> args, InterpreterState state) {
        if (state.directoryStack.isEmpty()) {
            return new ExecResult("", "bash: popd: directory stack empty\n", 1);
        }

        if (!args.isEmpty() && args.get(0).startsWith("+")) {
            try {
                int n = Integer.parseInt(args.get(0).substring(1));
                if (n < 0 || n >= state.directoryStack.size()) {
                    return new ExecResult("", "bash: popd: " + args.get(0) + ": invalid argument\n", 1);
                }
                state.directoryStack.remove(n);
                return new ExecResult(buildDirsOutput(state), "", 0);
            } catch (NumberFormatException e) {
                return new ExecResult("", "bash: popd: " + args.get(0) + ": invalid argument\n", 1);
            }
        }

        String target = state.directoryStack.remove(0);
        state.cwd = target;
        return new ExecResult(buildDirsOutput(state), "", 0);
    }

    private ExecResult handleDirs(List<String> args, InterpreterState state) {
        boolean clearFlag = false;
        boolean verboseFlag = false;
        boolean longFlag = false; // -l: print tilde-expanded
        int n = -1;

        for (String arg : args) {
            if (arg.equals("-c")) {
                clearFlag = true;
            } else if (arg.equals("-v")) {
                verboseFlag = true;
            } else if (arg.equals("-l")) {
                longFlag = true;
            } else if (arg.startsWith("+") || arg.startsWith("-")) {
                try {
                    n = Integer.parseInt(arg.substring(1));
                    if (arg.startsWith("-")) {
                        n = state.directoryStack.size() - n;
                    }
                } catch (NumberFormatException e) {
                    return new ExecResult("", "bash: dirs: " + arg + ": invalid argument\n", 1);
                }
            }
        }

        if (clearFlag) {
            state.directoryStack.clear();
            return new ExecResult("", "", 0);
        }

        if (n >= 0 && n < state.directoryStack.size()) {
            String dir = state.directoryStack.get(n);
            if (verboseFlag) {
                return new ExecResult(n + "  " + dir + "\n", "", 0);
            }
            return new ExecResult(dir + "\n", "", 0);
        }

        if (verboseFlag) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < state.directoryStack.size(); i++) {
                sb.append(i).append("  ").append(state.directoryStack.get(i)).append("\n");
            }
            return new ExecResult(sb.toString(), "", 0);
        }

        return new ExecResult(buildDirsOutput(state), "", 0);
    }

    private String buildDirsOutput(InterpreterState state) {
        StringBuilder sb = new StringBuilder();
        sb.append(state.cwd);
        for (String dir : state.directoryStack) {
            sb.append(" ").append(dir);
        }
        sb.append("\n");
        return sb.toString();
    }

    private ExecResult handleLet(List<String> args, InterpreterState state) {
        if (args.isEmpty()) {
            return new ExecResult("", "bash: let: syntax error: operand expected\n", 1);
        }

        // Join all arguments with spaces to form a single expression
        String exprStr = String.join(" ", args);

        try {
            var arithExpr = ArithmeticParser.parse(exprStr, 0);
            long result = com.justbash.interpreter.ArithmeticEvaluator.evaluate(arithExpr, state);
            return new ExecResult("", "", result == 0 ? 1 : 0);
        } catch (Exception e) {
            return new ExecResult("", "bash: let: syntax error\n", 1);
        }
    }

    private ExecResult handleGetopts(List<String> args, InterpreterState state) {
        if (args.size() < 2) {
            return new ExecResult("", "bash: getopts: usage: getopts optstring name [arg]\n", 2);
        }

        String optstring = args.get(0);
        String varName = args.get(1);
        List<String> argList;

        if (args.size() > 2) {
            // Use explicit args
            argList = new ArrayList<>(args.subList(2, args.size()));
            state.getoptsArgList = argList;
            state.getoptsOptind = 1;
            state.getoptsNextCharIndex = 0;
        } else {
            // Use positional parameters
            argList = new ArrayList<>();
            int i = 1;
            while (true) {
                String p = state.env.get(String.valueOf(i));
                if (p == null) break;
                argList.add(p);
                i++;
            }
            // Only reset state if argList changed or we're starting fresh
            if (state.getoptsArgList == null || !state.getoptsArgList.equals(argList)) {
                state.getoptsArgList = argList;
                state.getoptsOptind = 1;
                state.getoptsNextCharIndex = 0;
            }
        }

        int optind = state.getoptsOptind;
        boolean silentMode = optstring.startsWith(":");
        if (silentMode) {
            optstring = optstring.substring(1);
        }

        while (true) {
            if (optind > argList.size()) {
                // No more arguments
                state.env.put(varName, "?");
                state.env.remove("OPTARG");
                state.getoptsOptind = optind;
                return new ExecResult("", "", 1);
            }

            String currentArg = argList.get(optind - 1);

            if (state.getoptsNextCharIndex == 0) {
                // Starting a new argument
                if (currentArg.equals("--")) {
                    state.env.put(varName, "?");
                    state.env.remove("OPTARG");
                    state.getoptsOptind = optind + 1;
                    return new ExecResult("", "", 1);
                }
                if (!currentArg.startsWith("-") || currentArg.length() <= 1) {
                    // Not an option argument
                    state.env.put(varName, "?");
                    state.env.remove("OPTARG");
                    state.getoptsOptind = optind;
                    return new ExecResult("", "", 1);
                }
                state.getoptsNextCharIndex = 1;
            }

            char optchar = currentArg.charAt(state.getoptsNextCharIndex);
            boolean requiresArg = optstring.indexOf(optchar) >= 0 &&
                optstring.indexOf(optchar) + 1 < optstring.length() &&
                optstring.charAt(optstring.indexOf(optchar) + 1) == ':';

            if (optstring.indexOf(optchar) < 0) {
                // Invalid option
                if (silentMode) {
                    state.env.put(varName, "?");
                    state.env.put("OPTARG", String.valueOf(optchar));
                } else {
                    state.env.put(varName, "?");
                    state.env.remove("OPTARG");
                }
                state.getoptsNextCharIndex++;
                if (state.getoptsNextCharIndex >= currentArg.length()) {
                    state.getoptsNextCharIndex = 0;
                    optind++;
                }
                state.getoptsOptind = optind;
                return new ExecResult("", "", 0);
            }

            if (requiresArg) {
                String optarg;
                if (state.getoptsNextCharIndex + 1 < currentArg.length()) {
                    // Rest of current arg is the argument
                    optarg = currentArg.substring(state.getoptsNextCharIndex + 1);
                } else if (optind < argList.size()) {
                    // Next arg is the argument
                    optarg = argList.get(optind);
                    optind++;
                } else {
                    // Missing required argument
                    if (silentMode) {
                        state.env.put(varName, ":");
                        state.env.put("OPTARG", String.valueOf(optchar));
                    } else {
                        state.env.put(varName, "?");
                        state.env.remove("OPTARG");
                    }
                    state.getoptsNextCharIndex = 0;
                    optind++;
                    state.getoptsOptind = optind;
                    return new ExecResult("", "", 0);
                }
                state.env.put(varName, String.valueOf(optchar));
                state.env.put("OPTARG", optarg);
                state.getoptsNextCharIndex = 0;
                optind++;
                state.getoptsOptind = optind;
                return new ExecResult("", "", 0);
            }

            // Simple option (no argument required)
            state.env.put(varName, String.valueOf(optchar));
            state.env.remove("OPTARG");
            state.getoptsNextCharIndex++;
            if (state.getoptsNextCharIndex >= currentArg.length()) {
                state.getoptsNextCharIndex = 0;
                optind++;
            }
            state.getoptsOptind = optind;
            return new ExecResult("", "", 0);
        }
    }

    private ExecResult handleTrap(List<String> args, InterpreterState state) {
        boolean printFlag = false;
        List<String> signals = new ArrayList<>();
        String command = null;

        for (String arg : args) {
            if (arg.equals("-p")) {
                printFlag = true;
            } else if (arg.startsWith("-") && !arg.equals("-")) {
                // Ignore other flags for MVP
            } else {
                if (command == null && !isSignalName(arg)) {
                    command = arg;
                } else {
                    signals.add(normalizeSignal(arg));
                }
            }
        }

        if (command == null && signals.isEmpty() && !args.isEmpty()) {
            // All args were signals, no command given -> treat first arg as command if quoted
            // For MVP: if no signals parsed, re-evaluate
            // Actually, bash syntax: trap [action] [signals...]
            // If only one arg and it's a signal name, that means empty action for that signal
            if (args.size() == 1 && isSignalName(args.get(0))) {
                command = "";
                signals.add(normalizeSignal(args.get(0)));
            }
        }

        if (printFlag || (command == null && signals.isEmpty())) {
            // Print all traps
            StringBuilder stdout = new StringBuilder();
            for (Map.Entry<String, String> entry : state.trapHandlers.entrySet()) {
                stdout.append("trap -- '").append(entry.getValue()).append("' ").append(entry.getKey()).append("\n");
            }
            return new ExecResult(stdout.toString(), "", 0);
        }

        if (signals.isEmpty()) {
            // Default to EXIT if no signal specified
            signals.add("EXIT");
        }

        for (String signal : signals) {
            if (command == null || command.equals("-")) {
                state.trapHandlers.remove(signal);
            } else {
                state.trapHandlers.put(signal, command);
            }
        }

        return new ExecResult("", "", 0);
    }

    private boolean isSignalName(String s) {
        return s.equalsIgnoreCase("EXIT") || s.equalsIgnoreCase("ERR") || s.equalsIgnoreCase("DEBUG")
            || s.equalsIgnoreCase("RETURN") || s.equals("0") || s.equals("1") || s.equals("2")
            || s.equals("3") || s.equals("6") || s.equals("9") || s.equals("14") || s.equals("15");
    }

    private String normalizeSignal(String s) {
        return switch (s.toUpperCase()) {
            case "0" -> "EXIT";
            case "1", "HUP" -> "SIGHUP";
            case "2", "INT" -> "SIGINT";
            case "3", "QUIT" -> "SIGQUIT";
            case "6", "ABRT" -> "SIGABRT";
            case "9", "KILL" -> "SIGKILL";
            case "14", "ALRM" -> "SIGALRM";
            case "15", "TERM" -> "SIGTERM";
            default -> s.toUpperCase();
        };
    }

    private ExecResult handleKill(List<String> args, InterpreterState state) {
        boolean listSignals = false;
        String signal = "TERM";
        List<String> pids = new ArrayList<>();

        for (String arg : args) {
            if (arg.equals("-l") || arg.equals("-L")) {
                listSignals = true;
            } else if (arg.startsWith("-")) {
                signal = arg.substring(1);
            } else {
                pids.add(arg);
            }
        }

        if (listSignals) {
            return new ExecResult(
                "HUP INT QUIT ILL TRAP ABRT BUS FPE KILL USR1 SEGV USR2 PIPE ALRM TERM STKFLT CHLD CONT STOP TSTP TTIN TTOU URG XCPU XFSZ VTALRM PROF WINCH POLL PWR SYS\n",
                "", 0);
        }

        if (pids.isEmpty()) {
            return new ExecResult("", "bash: kill: usage: kill [-s sigspec | -n signum | -sigspec] pid | jobspec ... or kill -l [sigspec]\n", 2);
        }

        // In the sandboxed environment, we just pretend to send signals
        return new ExecResult("", "", 0);
    }

    private ExecResult handleUmask(List<String> args, InterpreterState state) {
        if (args.isEmpty()) {
            // Print current umask in symbolic form
            return new ExecResult("u=rwx,g=rx,o=rx\n", "", 0);
        }

        String arg = args.get(0);
        if (arg.equals("-S")) {
            if (args.size() > 1) {
                // Set new umask symbolically - just accept for MVP
                return new ExecResult("", "", 0);
            }
            return new ExecResult("u=rwx,g=rx,o=rx\n", "", 0);
        }

        if (arg.startsWith("-")) {
            return new ExecResult("", "bash: umask: " + arg + ": invalid option\n", 2);
        }

        // Set numeric umask - just accept for MVP
        return new ExecResult("", "", 0);
    }

    private ExecResult handleTimes(List<String> args, InterpreterState state) {
        long now = System.currentTimeMillis() - state.startTime;
        double user = now / 1000.0;
        double sys = now / 10000.0;
        double childUser = user * 0.1;
        double childSys = sys * 0.1;
        String output = String.format("%.3fs %.3fs\n%.3fs %.3fs\n", user, sys, childUser, childSys);
        return new ExecResult(output, "", 0);
    }

    private ExecResult handleHistory(List<String> args, InterpreterState state) {
        // MVP: no persistent history
        return new ExecResult("", "", 0);
    }

    private ExecResult handleType(List<String> args, InterpreterState state) {
        boolean allFlag = false;
        boolean pathFlag = false;
        boolean typeFlag = false;
        List<String> names = new ArrayList<>();

        for (String arg : args) {
            if (arg.equals("-a")) {
                allFlag = true;
            } else if (arg.equals("-p")) {
                pathFlag = true;
            } else if (arg.equals("-t")) {
                typeFlag = true;
            } else if (arg.startsWith("-") && !arg.equals("-")) {
                return new ExecResult("", "bash: type: " + arg + ": invalid option\n", 2);
            } else {
                names.add(arg);
            }
        }

        if (names.isEmpty()) {
            return new ExecResult("", "", 0);
        }

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        int exitCode = 0;

        for (String name : names) {
            String classification = classifyCommand(name, state);

            if (typeFlag) {
                if (classification.equals("builtin")) {
                    stdout.append("builtin\n");
                } else if (classification.equals("alias")) {
                    stdout.append("alias\n");
                } else if (classification.equals("function")) {
                    stdout.append("function\n");
                } else if (classification.equals("keyword")) {
                    stdout.append("keyword\n");
                } else {
                    stdout.append("file\n");
                }
            } else if (pathFlag) {
                if (classification.equals("file")) {
                    stdout.append("/usr/bin/").append(name).append("\n");
                }
            } else {
                if (classification.equals("builtin")) {
                    stdout.append(name).append(" is a shell builtin\n");
                } else if (classification.equals("alias")) {
                    String aliasValue = state.aliases.get(name);
                    stdout.append(name).append(" is aliased to '").append(aliasValue).append("'\n");
                } else if (classification.equals("function")) {
                    stdout.append(name).append(" is a function\n");
                } else if (classification.equals("keyword")) {
                    stdout.append(name).append(" is a shell keyword\n");
                } else {
                    stdout.append(name).append(" is /usr/bin/").append(name).append("\n");
                }
            }
        }

        return new ExecResult(stdout.toString(), stderr.toString(), exitCode);
    }

    private String classifyCommand(String name, InterpreterState state) {
        if (state.aliases.containsKey(name)) return "alias";
        if (isKeyword(name)) return "keyword";
        if (state.functions.containsKey(name)) return "function";
        if (isBuiltin(name)) return "builtin";
        return "file";
    }

    private boolean isKeyword(String name) {
        return Set.of("if", "then", "else", "elif", "fi", "case", "esac", "for",
            "while", "until", "do", "done", "in", "function", "time", "select",
            "coproc").contains(name);
    }

    private boolean isBuiltin(String name) {
        return new CommandResolver().isBuiltin(name);
    }

    private ExecResult handleCommand(List<String> args, InterpreterState state) {
        boolean bypassFunctions = true;
        boolean searchPath = false;
        boolean verbose = false;
        List<String> rest = new ArrayList<>();

        int i = 0;
        while (i < args.size()) {
            String arg = args.get(i);
            if (arg.equals("-p")) {
                searchPath = true;
                i++;
            } else if (arg.equals("-v")) {
                verbose = true;
                i++;
            } else if (arg.equals("-V")) {
                verbose = true;
                i++;
            } else if (arg.equals("--")) {
                i++;
                break;
            } else if (arg.startsWith("-") && !arg.equals("-")) {
                return new ExecResult("", "bash: command: " + arg + ": invalid option\n", 2);
            } else {
                break;
            }
        }

        rest.addAll(args.subList(i, args.size()));

        if (rest.isEmpty()) {
            return new ExecResult("", "", 0);
        }

        String cmdName = rest.get(0);
        List<String> cmdArgs = rest.subList(1, rest.size());

        if (verbose) {
            String classification = classifyCommand(cmdName, state);
            StringBuilder sb = new StringBuilder();
            if (classification.equals("builtin")) {
                sb.append(cmdName).append(" is a shell builtin\n");
            } else if (classification.equals("alias")) {
                sb.append(cmdName).append(" is aliased to '").append(state.aliases.get(cmdName)).append("'\n");
            } else if (classification.equals("function")) {
                sb.append(cmdName).append(" is a function\n");
            } else if (classification.equals("keyword")) {
                sb.append(cmdName).append(" is a shell keyword\n");
            } else {
                sb.append(cmdName).append(" is hashed (/usr/bin/").append(cmdName).append(")\n");
            }
            return new ExecResult(sb.toString(), "", 0);
        }

        // Execute the command, bypassing functions if requested
        if (bypassFunctions) {
            var builtinResult = dispatch(cmdName, cmdArgs, state);
            if (builtinResult.isPresent()) {
                return builtinResult.get();
            }
        }

        // Fall through to normal dispatch
        return dispatch(cmdName, cmdArgs, state)
            .orElse(new ExecResult("", "bash: " + cmdName + ": command not found\n", 127));
    }

    private ExecResult handleHash(List<String> args, InterpreterState state) {
        boolean listFlag = false;
        boolean removeFlag = false;
        boolean resetFlag = false;
        String pathname = null;
        List<String> names = new ArrayList<>();

        int i = 0;
        while (i < args.size()) {
            String arg = args.get(i);
            if (arg.equals("-r")) {
                resetFlag = true;
            } else if (arg.equals("-l")) {
                listFlag = true;
            } else if (arg.equals("-p")) {
                i++;
                if (i >= args.size()) {
                    return new ExecResult("", "bash: hash: -p: option requires an argument\n", 2);
                }
                pathname = args.get(i);
            } else if (arg.startsWith("-") && !arg.equals("-")) {
                return new ExecResult("", "bash: hash: " + arg + ": invalid option\n", 2);
            } else {
                names.add(arg);
            }
            i++;
        }

        if (resetFlag) {
            state.hashTable.clear();
            return new ExecResult("", "", 0);
        }

        if (pathname != null) {
            for (String name : names) {
                state.hashTable.put(name, pathname);
            }
            return new ExecResult("", "", 0);
        }

        if (listFlag) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : state.hashTable.entrySet()) {
                sb.append("builtin hash -p ").append(entry.getValue())
                  .append(" ").append(entry.getKey()).append("\n");
            }
            return new ExecResult(sb.toString(), "", 0);
        }

        if (names.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : state.hashTable.entrySet()) {
                sb.append(entry.getKey()).append("\t").append(entry.getValue()).append("\n");
            }
            return new ExecResult(sb.toString(), "", 0);
        }

        StringBuilder stderr = new StringBuilder();
        int exitCode = 0;
        for (String name : names) {
            // For MVP, just pretend to hash all commands
            state.hashTable.putIfAbsent(name, "/usr/bin/" + name);
        }
        return new ExecResult("", stderr.toString(), exitCode);
    }

    private ExecResult handleBuiltin(List<String> args, InterpreterState state) {
        if (args.isEmpty()) {
            return new ExecResult("", "bash: builtin: usage: builtin [shell-builtin [arg ...]]\n", 1);
        }
        String cmdName = args.get(0);
        List<String> cmdArgs = args.subList(1, args.size());
        return dispatch(cmdName, cmdArgs, state)
            .orElse(new ExecResult("", "bash: " + cmdName + ": not a shell builtin\n", 1));
    }

    private ExecResult handleMapfile(List<String> args, InterpreterState state) {
        String arrayName = "MAPFILE";
        String delimiter = "\n";
        int count = -1;
        int offset = 0;
        boolean removeTrailing = false;
        int skip = 0;
        List<String> input = new ArrayList<>();

        int i = 0;
        while (i < args.size()) {
            String arg = args.get(i);
            if (arg.equals("-t")) {
                removeTrailing = true;
                i++;
            } else if (arg.equals("-d")) {
                i++;
                if (i >= args.size()) {
                    return new ExecResult("", "bash: mapfile: -d: option requires an argument\n", 2);
                }
                delimiter = args.get(i);
                i++;
            } else if (arg.equals("-n")) {
                i++;
                if (i >= args.size()) {
                    return new ExecResult("", "bash: mapfile: -n: option requires an argument\n", 2);
                }
                try {
                    count = Integer.parseInt(args.get(i));
                } catch (NumberFormatException e) {
                    return new ExecResult("", "bash: mapfile: " + args.get(i) + ": invalid count\n", 1);
                }
                i++;
            } else if (arg.equals("-O")) {
                i++;
                if (i >= args.size()) {
                    return new ExecResult("", "bash: mapfile: -O: option requires an argument\n", 2);
                }
                try {
                    offset = Integer.parseInt(args.get(i));
                } catch (NumberFormatException e) {
                    return new ExecResult("", "bash: mapfile: " + args.get(i) + ": invalid offset\n", 1);
                }
                i++;
            } else if (arg.equals("-s")) {
                i++;
                if (i >= args.size()) {
                    return new ExecResult("", "bash: mapfile: -s: option requires an argument\n", 2);
                }
                try {
                    skip = Integer.parseInt(args.get(i));
                } catch (NumberFormatException e) {
                    return new ExecResult("", "bash: mapfile: " + args.get(i) + ": invalid skip count\n", 1);
                }
                i++;
            } else if (arg.equals("-u")) {
                i++;
                if (i >= args.size()) {
                    return new ExecResult("", "bash: mapfile: -u: option requires an argument\n", 2);
                }
                i++; // ignore fd for MVP
            } else if (arg.startsWith("-") && !arg.equals("-")) {
                return new ExecResult("", "bash: mapfile: " + arg + ": invalid option\n", 2);
            } else {
                arrayName = arg;
                i++;
            }
        }

        // Read from stdin (groupStdin)
        String stdin = state.groupStdin;
        if (stdin != null && !stdin.isEmpty()) {
            if (delimiter.isEmpty()) {
                // Read everything as a single element
                input.add(stdin);
            } else {
                String[] lines = stdin.split(Pattern.quote(delimiter), -1);
                for (String line : lines) {
                    input.add(line);
                }
            }
        }

        if (removeTrailing && !input.isEmpty()) {
            String last = input.getLast();
            if (last.endsWith("\n")) {
                input.set(input.size() - 1, last.substring(0, last.length() - 1));
            }
        }

        // Skip lines
        int startIdx = Math.min(skip, input.size());
        List<String> effective = input.subList(startIdx, input.size());

        // Limit count
        if (count >= 0) {
            int end = Math.min(count, effective.size());
            effective = effective.subList(0, end);
        }

        // Store in array
        List<String> arr = new ArrayList<>();
        for (int j = 0; j < offset; j++) {
            arr.add("");
        }
        arr.addAll(effective);
        state.indexedArrays.put(arrayName, arr);

        return new ExecResult("", "", 0);
    }

    private ExecResult handlePrintf(List<String> args, InterpreterState state) {
        if (args.isEmpty()) {
            return new ExecResult("", "bash: printf: usage: printf format [arguments]\n", 2);
        }

        int idx = 0;
        String varName = null;
        if (args.get(0).equals("-v")) {
            if (args.size() < 2) {
                return new ExecResult("", "bash: printf: -v: option requires an argument\n", 2);
            }
            varName = args.get(1);
            idx = 2;
        }

        if (idx >= args.size()) {
            return new ExecResult("", "bash: printf: usage: printf format [arguments]\n", 2);
        }

        String format = args.get(idx);
        List<String> formatArgs = args.subList(idx + 1, args.size());

        String output = formatPrintf(format, formatArgs);

        if (varName != null) {
            state.env.put(varName, output);
        } else {
            return new ExecResult(output, "", 0);
        }
        return new ExecResult("", "", 0);
    }

    private String formatPrintf(String format, List<String> args) {
        StringBuilder result = new StringBuilder();
        int argIdx = 0;
        for (int i = 0; i < format.length(); i++) {
            char c = format.charAt(i);
            if (c == '\\' && i + 1 < format.length()) {
                char next = format.charAt(i + 1);
                result.append(switch (next) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case 'r' -> '\r';
                    case '\\' -> '\\';
                    case 'a' -> '';
                    case 'b' -> '\b';
                    case 'v' -> '';
                    case 'f' -> '';
                    case '"' -> '"';
                    case '\'' -> '\'';
                    case '0' -> ' ';
                    default -> next;
                });
                i++;
            } else if (c == '%' && i + 1 < format.length()) {
                char spec = format.charAt(i + 1);
                if (spec == '%') {
                    result.append('%');
                    i++;
                } else if (argIdx < args.size()) {
                    String arg = args.get(argIdx++);
                    result.append(switch (spec) {
                        case 's' -> arg;
                        case 'd', 'i' -> formatInt(arg, 10);
                        case 'u' -> formatUnsignedInt(arg);
                        case 'f' -> formatFloat(arg);
                        case 'e' -> formatScientific(arg, 'e');
                        case 'E' -> formatScientific(arg, 'E');
                        case 'g', 'G' -> formatFloat(arg);
                        case 'c' -> arg.isEmpty() ? "" : arg.substring(0, 1);
                        case 'x' -> formatHex(arg, false);
                        case 'X' -> formatHex(arg, true);
                        case 'o' -> formatOctal(arg);
                        default -> "%" + spec;
                    });
                    i++;
                } else {
                    result.append('%').append(spec);
                    i++;
                }
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private String formatInt(String arg, int radix) {
        try {
            return String.valueOf(Long.parseLong(arg.trim()));
        } catch (NumberFormatException e) {
            return "0";
        }
    }

    private String formatUnsignedInt(String arg) {
        try {
            return Long.toUnsignedString(Long.parseLong(arg.trim()));
        } catch (NumberFormatException e) {
            return "0";
        }
    }

    private String formatFloat(String arg) {
        try {
            return String.valueOf(Double.parseDouble(arg.trim()));
        } catch (NumberFormatException e) {
            return "0";
        }
    }

    private String formatScientific(String arg, char e) {
        try {
            return String.format(java.util.Locale.US, "%" + e, Double.parseDouble(arg.trim()));
        } catch (NumberFormatException e2) {
            return "0";
        }
    }

    private String formatHex(String arg, boolean upper) {
        try {
            long val = Long.parseLong(arg.trim());
            return upper ? Long.toHexString(val).toUpperCase() : Long.toHexString(val);
        } catch (NumberFormatException e) {
            return "0";
        }
    }

    private String formatOctal(String arg) {
        try {
            return Long.toOctalString(Long.parseLong(arg.trim()));
        } catch (NumberFormatException e) {
            return "0";
        }
    }
}
