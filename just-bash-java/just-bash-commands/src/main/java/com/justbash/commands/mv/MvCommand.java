package com.justbash.commands.mv;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import com.justbash.fs.IFileSystem;
import com.justbash.fs.RmOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MvCommand implements Command {
    @Override public String name() { return "mv"; }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> paths = new ArrayList<>();
            for (String arg : args) {
                if (arg.startsWith("-")) { /* ignore options for MVP */ }
                else paths.add(arg);
            }

            if (paths.size() < 2) {
                return new ExecResult("", "mv: missing file operand\n", 1);
            }

            String dest = paths.get(paths.size() - 1);
            String stderr = "";
            int exitCode = 0;

            for (int i = 0; i < paths.size() - 1; i++) {
                String src = paths.get(i);
                try {
                    String srcPath = src.startsWith("/") ? src : ctx.cwd() + "/" + src;
                    String destPath = dest.startsWith("/") ? dest : ctx.cwd() + "/" + dest;
                    String content = ctx.fs().readFile(srcPath).join();
                    ctx.fs().writeFile(destPath, new IFileSystem.StringContent(content)).join();
                    ctx.fs().rm(srcPath, new RmOptions(true, false)).join();
                } catch (Exception e) {
                    stderr += "mv: cannot stat '" + src + "': No such file or directory\n";
                    exitCode = 1;
                }
            }
            return new ExecResult("", stderr, exitCode);
        });
    }
}
