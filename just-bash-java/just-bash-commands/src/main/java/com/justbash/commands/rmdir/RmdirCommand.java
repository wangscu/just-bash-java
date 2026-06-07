package com.justbash.commands.rmdir;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import com.justbash.fs.FsStat;
import com.justbash.fs.RmOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RmdirCommand implements Command {
    @Override
    public String name() { return "rmdir"; }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            boolean parents = false;
            boolean verbose = false;
            List<String> dirs = new ArrayList<>();
            boolean optionsDone = false;

            for (String arg : args) {
                if (optionsDone) {
                    dirs.add(arg);
                    continue;
                }
                if (arg.equals("--")) {
                    optionsDone = true;
                } else if (arg.equals("-p") || arg.equals("--parents")) {
                    parents = true;
                } else if (arg.equals("-v") || arg.equals("--verbose")) {
                    verbose = true;
                } else if (arg.equals("--help")) {
                    return help();
                } else if (arg.startsWith("-") && !arg.equals("-")) {
                    return new ExecResult("", "rmdir: invalid option -- '" + arg.substring(1) + "'\n", 1);
                } else {
                    dirs.add(arg);
                }
            }

            if (dirs.isEmpty()) {
                return new ExecResult("", "rmdir: missing operand\n", 1);
            }

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            int exitCode = 0;

            for (String dir : dirs) {
                var result = removeDir(ctx, dir, parents, verbose);
                stdout.append(result.stdout);
                stderr.append(result.stderr);
                if (result.exitCode != 0) {
                    exitCode = result.exitCode;
                }
            }

            return new ExecResult(stdout.toString(), stderr.toString(), exitCode);
        });
    }

    private Result removeDir(CommandContext ctx, String dir, boolean parents, boolean verbose) {
        String fullPath = dir.startsWith("/") ? dir : ctx.cwd() + "/" + dir;

        var result = removeSingleDir(ctx, fullPath, dir, verbose);
        if (result.exitCode != 0) {
            return result;
        }

        if (parents) {
            String currentPath = fullPath;
            String currentDir = dir;

            while (true) {
                String parentPath = getParentPath(currentPath);
                String parentDir = getParentPath(currentDir);

                if (parentPath.equals(currentPath) || parentPath.equals("/")
                    || parentPath.equals(".") || parentDir.equals(".") || parentDir.isEmpty()) {
                    break;
                }

                var parentResult = removeSingleDir(ctx, parentPath, parentDir, verbose);
                if (parentResult.exitCode != 0) {
                    break;
                }

                currentPath = parentPath;
                currentDir = parentDir;
            }
        }

        return result;
    }

    private Result removeSingleDir(CommandContext ctx, String fullPath, String displayPath, boolean verbose) {
        try {
            boolean exists = ctx.fs().exists(fullPath).join();
            if (!exists) {
                return new Result("", "rmdir: failed to remove '" + displayPath + "': No such file or directory\n", 1);
            }

            FsStat stat = ctx.fs().stat(fullPath).join();
            if (!stat.isDirectory()) {
                return new Result("", "rmdir: failed to remove '" + displayPath + "': Not a directory\n", 1);
            }

            List<String> entries = ctx.fs().readdir(fullPath).join();
            if (!entries.isEmpty()) {
                return new Result("", "rmdir: failed to remove '" + displayPath + "': Directory not empty\n", 1);
            }

            ctx.fs().rm(fullPath, new RmOptions(false, false)).join();

            String stdout = verbose ? "rmdir: removing directory, '" + displayPath + "'\n" : "";
            return new Result(stdout, "", 0);
        } catch (Exception e) {
            return new Result("", "rmdir: failed to remove '" + displayPath + "': " + e.getMessage() + "\n", 1);
        }
    }

    private String getParentPath(String path) {
        int end = path.length();
        while (end > 0 && path.charAt(end - 1) == '/') {
            end--;
        }

        if (end == 0) {
            return "/";
        }

        int lastSlash = path.lastIndexOf('/', end - 1);
        if (lastSlash == -1) {
            return ".";
        }
        if (lastSlash == 0) {
            return "/";
        }
        return path.substring(0, lastSlash);
    }

    private ExecResult help() {
        String help = "Usage: rmdir [-pv] DIRECTORY...\n" +
            "Remove empty directories.\n\n" +
            "  -p, --parents   Remove DIRECTORY and its ancestors\n" +
            "  -v, --verbose   Output a diagnostic for every directory processed\n" +
            "      --help      display this help and exit\n";
        return new ExecResult(help, "", 0);
    }

    private record Result(String stdout, String stderr, int exitCode) {}
}
