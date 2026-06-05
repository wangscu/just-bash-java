package com.justbash.commands.touch;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import com.justbash.fs.IFileSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TouchCommand implements Command {
    @Override public String name() { return "touch"; }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> files = new ArrayList<>();
            for (String arg : args) {
                if (arg.startsWith("-")) { /* ignore options for MVP */ }
                else files.add(arg);
            }

            if (files.isEmpty()) {
                return new ExecResult("", "touch: missing file operand\n", 1);
            }

            String stderr = "";
            int exitCode = 0;

            for (String file : files) {
                try {
                    String path = file.startsWith("/") ? file : ctx.cwd() + "/" + file;
                    try {
                        ctx.fs().readFile(path).join();
                    } catch (Exception e) {
                        ctx.fs().writeFile(path, new IFileSystem.StringContent("")).join();
                    }
                } catch (Exception e) {
                    stderr += "touch: cannot touch '" + file + "': " + e.getMessage() + "\n";
                    exitCode = 1;
                }
            }
            return new ExecResult("", stderr, exitCode);
        });
    }
}
