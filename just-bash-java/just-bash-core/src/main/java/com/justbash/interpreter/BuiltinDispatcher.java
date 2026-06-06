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
import com.justbash.parser.Parser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
}
