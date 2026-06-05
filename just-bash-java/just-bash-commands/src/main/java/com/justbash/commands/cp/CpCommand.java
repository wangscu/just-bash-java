package com.justbash.commands.cp;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import com.justbash.fs.IFileSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CpCommand implements Command {
    @Override public String name() { return "cp"; }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            boolean recursive = false;
            boolean verbose = false;
            List<String> paths = new ArrayList<>();

            for (String arg : args) {
                if (arg.equals("-r") || arg.equals("-R") || arg.equals("--recursive")) recursive = true;
                else if (arg.equals("-v") || arg.equals("--verbose")) verbose = true;
                else if (arg.equals("--")) continue;
                else if (arg.startsWith("-")) { /* ignore */ }
                else paths.add(arg);
            }

            if (paths.size() < 2) {
                return new ExecResult("", "cp: missing file operand\n", 1);
            }

            String dest = paths.get(paths.size() - 1);
            List<String> sources = paths.subList(0, paths.size() - 1);
            String stderr = "";
            String stdout = "";
            int exitCode = 0;

            for (String src : sources) {
                try {
                    String srcPath = src.startsWith("/") ? src : ctx.cwd() + "/" + src;
                    String destPath = dest.startsWith("/") ? dest : ctx.cwd() + "/" + dest;

                    String content = ctx.fs().readFile(srcPath).join();
                    ctx.fs().writeFile(destPath, new IFileSystem.StringContent(content)).join();
                    if (verbose) stdout += "'" + src + "' -> '" + dest + "'\n";
                } catch (Exception e) {
                    stderr += "cp: cannot stat '" + src + "': No such file or directory\n";
                    exitCode = 1;
                }
            }
            return new ExecResult(stdout, stderr, exitCode);
        });
    }
}
