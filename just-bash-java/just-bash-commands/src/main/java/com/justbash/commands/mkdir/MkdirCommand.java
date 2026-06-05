package com.justbash.commands.mkdir;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import com.justbash.fs.MkdirOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MkdirCommand implements Command {
    @Override public String name() { return "mkdir"; }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            boolean recursive = false;
            boolean verbose = false;
            List<String> dirs = new ArrayList<>();

            for (String arg : args) {
                if (arg.equals("-p") || arg.equals("--parents")) recursive = true;
                else if (arg.equals("-v") || arg.equals("--verbose")) verbose = true;
                else if (arg.equals("--")) continue;
                else if (arg.startsWith("-")) { /* ignore */ }
                else dirs.add(arg);
            }

            if (dirs.isEmpty()) {
                return new ExecResult("", "mkdir: missing operand\n", 1);
            }

            String stdout = "";
            String stderr = "";
            int exitCode = 0;

            for (String dir : dirs) {
                try {
                    String path = dir.startsWith("/") ? dir : ctx.cwd() + "/" + dir;
                    ctx.fs().mkdir(path, new MkdirOptions(recursive)).join();
                    if (verbose) stdout += "mkdir: created directory '" + dir + "'\n";
                } catch (Exception e) {
                    String msg = e.getMessage();
                    if (msg != null && msg.contains("exists")) {
                        stderr += "mkdir: cannot create directory '" + dir + "': File exists\n";
                    } else {
                        stderr += "mkdir: cannot create directory '" + dir + "': No such file or directory\n";
                    }
                    exitCode = 1;
                }
            }
            return new ExecResult(stdout, stderr, exitCode);
        });
    }
}
