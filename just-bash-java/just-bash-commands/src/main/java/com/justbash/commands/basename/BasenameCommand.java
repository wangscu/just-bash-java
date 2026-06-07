package com.justbash.commands.basename;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BasenameCommand implements Command {
    @Override
    public String name() { return "basename"; }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            boolean multiple = false;
            String suffix = "";
            List<String> names = new ArrayList<>();
            boolean optionsDone = false;

            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if (optionsDone) {
                    names.add(arg);
                    continue;
                }
                if (arg.equals("--")) {
                    optionsDone = true;
                } else if (arg.equals("-a") || arg.equals("--multiple")) {
                    multiple = true;
                } else if (arg.equals("-s")) {
                    multiple = true;
                    if (i + 1 >= args.size()) {
                        return new ExecResult("", "basename: option requires an argument -- 's'\n", 1);
                    }
                    suffix = args.get(++i);
                } else if (arg.startsWith("--suffix=")) {
                    multiple = true;
                    suffix = arg.substring("--suffix=".length());
                } else if (arg.startsWith("-") && !arg.equals("-")) {
                    if (arg.equals("--help")) {
                        return help();
                    }
                    return new ExecResult("", "basename: invalid option -- '" + arg.substring(1) + "'\n", 1);
                } else {
                    names.add(arg);
                }
            }

            if (names.isEmpty()) {
                return new ExecResult("", "basename: missing operand\n", 1);
            }

            if (!multiple && names.size() >= 2) {
                suffix = names.remove(names.size() - 1);
            }

            StringBuilder stdout = new StringBuilder();
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) {
                    stdout.append('\n');
                }
                stdout.append(stripSuffix(computeBasename(names.get(i)), suffix));
            }
            stdout.append('\n');

            return new ExecResult(stdout.toString(), "", 0);
        });
    }

    private String computeBasename(String name) {
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

        // Find last slash before the trimmed end
        int lastSlash = name.lastIndexOf('/', end - 1);
        return name.substring(lastSlash + 1, end);
    }

    private String stripSuffix(String base, String suffix) {
        if (!suffix.isEmpty() && base.endsWith(suffix) && !base.equals(suffix)) {
            return base.substring(0, base.length() - suffix.length());
        }
        return base;
    }

    private ExecResult help() {
        String help = "Usage: basename NAME [SUFFIX]\n" +
            "       basename OPTION... NAME...\n\n" +
            "  -a, --multiple       support multiple arguments\n" +
            "  -s, --suffix=SUFFIX  remove a trailing SUFFIX\n" +
            "      --help           display this help and exit\n";
        return new ExecResult(help, "", 0);
    }
}
