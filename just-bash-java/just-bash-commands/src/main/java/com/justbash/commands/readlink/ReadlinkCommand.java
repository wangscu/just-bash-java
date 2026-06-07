package com.justbash.commands.readlink;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ReadlinkCommand implements Command {
    @Override
    public String name() { return "readlink"; }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            boolean canonicalize = false;
            List<String> files = new ArrayList<>();
            boolean optionsDone = false;

            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if (optionsDone) {
                    files.add(arg);
                    continue;
                }
                if (arg.equals("--")) {
                    optionsDone = true;
                } else if (arg.equals("-f") || arg.equals("--canonicalize")) {
                    canonicalize = true;
                } else if (arg.equals("--help")) {
                    return help();
                } else if (arg.startsWith("-") && !arg.equals("-")) {
                    return new ExecResult("", "readlink: invalid option -- '" + arg.substring(1) + "'\n", 1);
                } else {
                    files.add(arg);
                }
            }

            if (files.isEmpty()) {
                return new ExecResult("", "readlink: missing operand\n", 1);
            }

            StringBuilder stdout = new StringBuilder();
            boolean anyError = false;

            for (String file : files) {
                String filePath = file.startsWith("/") ? file : ctx.cwd() + "/" + file;

                try {
                    if (canonicalize) {
                        String resolved = resolveCanonical(ctx, filePath);
                        stdout.append(resolved).append('\n');
                    } else {
                        String target = ctx.fs().readlink(filePath).join();
                        stdout.append(target).append('\n');
                    }
                } catch (Exception e) {
                    if (!canonicalize) {
                        anyError = true;
                    } else {
                        stdout.append(filePath).append('\n');
                    }
                }
            }

            return new ExecResult(stdout.toString(), "", anyError ? 1 : 0);
        });
    }

    private String resolveCanonical(CommandContext ctx, String filePath) {
        String currentPath = filePath;
        Set<String> seen = new HashSet<>();

        while (true) {
            if (seen.contains(currentPath)) {
                break;
            }
            seen.add(currentPath);

            try {
                String target = ctx.fs().readlink(currentPath).join();
                if (target.startsWith("/")) {
                    currentPath = target;
                } else {
                    String dir = getParentDir(currentPath);
                    currentPath = ctx.fs().resolvePath(dir, target);
                }
            } catch (Exception e) {
                break;
            }
        }

        return currentPath;
    }

    private String getParentDir(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= 0) {
            return "/";
        }
        return path.substring(0, lastSlash);
    }

    private ExecResult help() {
        String help = "Usage: readlink [OPTIONS] FILE...\n\n" +
            "Print resolved symbolic links or canonical file names.\n\n" +
            "  -f, --canonicalize    canonicalize by following every symlink recursively\n" +
            "      --help            display this help and exit\n";
        return new ExecResult(help, "", 0);
    }
}
