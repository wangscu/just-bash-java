package com.justbash.commands.dirname;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DirnameCommand implements Command {
    @Override
    public String name() { return "dirname"; }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> names = new ArrayList<>();
            boolean optionsDone = false;

            for (String arg : args) {
                if (optionsDone) {
                    names.add(arg);
                    continue;
                }
                if (arg.equals("--")) {
                    optionsDone = true;
                } else if (arg.equals("--help")) {
                    return help();
                } else if (arg.startsWith("-") && !arg.equals("-")) {
                    return new ExecResult("", "dirname: invalid option -- '" + arg.substring(1) + "'\n", 1);
                } else {
                    names.add(arg);
                }
            }

            if (names.isEmpty()) {
                return new ExecResult("", "dirname: missing operand\n", 1);
            }

            StringBuilder stdout = new StringBuilder();
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) {
                    stdout.append('\n');
                }
                stdout.append(computeDirname(names.get(i)));
            }
            stdout.append('\n');

            return new ExecResult(stdout.toString(), "", 0);
        });
    }

    private String computeDirname(String name) {
        if (name.isEmpty()) {
            return ".";
        }

        // Remove trailing slashes
        int end = name.length();
        while (end > 0 && name.charAt(end - 1) == '/') {
            end--;
        }

        if (end == 0) {
            // Input was all slashes (e.g., "/", "///")
            return "/";
        }

        // Find last slash within the trimmed portion
        int lastSlash = name.lastIndexOf('/', end - 1);

        if (lastSlash == -1) {
            // No slash found: e.g., "foo", "foo/"
            return ".";
        }

        if (lastSlash == 0) {
            // Slash at position 0: e.g., "/foo", "/foo/"
            return "/";
        }

        // Return everything before the last slash
        return name.substring(0, lastSlash);
    }

    private ExecResult help() {
        String help = "Usage: dirname [OPTION] NAME...\n\n" +
            "    --help    display this help and exit\n";
        return new ExecResult(help, "", 0);
    }
}
