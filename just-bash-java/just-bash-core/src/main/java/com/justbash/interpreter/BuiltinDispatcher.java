package com.justbash.interpreter;

import com.justbash.ExecResult;
import com.justbash.fs.FsStat;
import com.justbash.fs.IFileSystem;
import com.justbash.interpreter.errors.ExitException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class BuiltinDispatcher {

    private final IFileSystem fs;

    public BuiltinDispatcher(IFileSystem fs) {
        this.fs = fs;
    }

    public Optional<ExecResult> dispatch(String name, List<String> args, InterpreterState state) {
        return switch (name) {
            case "echo" -> Optional.of(handleEcho(args));
            case "true" -> Optional.of(new ExecResult("", "", 0));
            case "false" -> Optional.of(new ExecResult("", "", 1));
            case "cd" -> Optional.of(handleCd(args, state));
            case "pwd" -> Optional.of(handlePwd(args, state));
            case "export" -> Optional.of(handleExport(args, state));
            case "unset" -> Optional.of(handleUnset(args, state));
            case "exit" -> Optional.of(handleExit(args, state));
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
}
