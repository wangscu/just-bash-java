package com.justbash.commands.rm;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import com.justbash.fs.RmOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RmCommand implements Command {
    @Override public String name() { return "rm"; }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            boolean recursive = false;
            boolean force = false;
            boolean verbose = false;
            List<String> paths = new ArrayList<>();

            for (String arg : args) {
                if (arg.equals("-r") || arg.equals("-R") || arg.equals("--recursive")) recursive = true;
                else if (arg.equals("-f") || arg.equals("--force")) force = true;
                else if (arg.equals("-v") || arg.equals("--verbose")) verbose = true;
                else if (arg.equals("--")) continue;
                else if (arg.startsWith("-")) { /* ignore */ }
                else paths.add(arg);
            }

            if (paths.isEmpty() && !force) {
                return new ExecResult("", "rm: missing operand\n", 1);
            }
            if (paths.isEmpty()) return new ExecResult("", "", 0);

            String stdout = "";
            String stderr = "";
            int exitCode = 0;

            for (String path : paths) {
                try {
                    String fullPath = path.startsWith("/") ? path : ctx.cwd() + "/" + path;
                    var stat = ctx.fs().stat(fullPath).join();
                    if (stat != null && stat.isDirectory() && !recursive) {
                        stderr += "rm: cannot remove '" + path + "': Is a directory\n";
                        exitCode = 1;
                        continue;
                    }
                    ctx.fs().rm(fullPath, new RmOptions(recursive, force)).join();
                    if (verbose) stdout += "removed '" + path + "'\n";
                } catch (Exception e) {
                    if (!force) {
                        stderr += "rm: cannot remove '" + path + "': No such file or directory\n";
                        exitCode = 1;
                    }
                }
            }
            return new ExecResult(stdout, stderr, exitCode);
        });
    }
}
