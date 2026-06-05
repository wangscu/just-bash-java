package com.justbash.commands.cat;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CatCommand implements Command {
    @Override
    public String name() { return "cat"; }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder stdout = new StringBuilder();
            String stderr = "";
            int exitCode = 0;
            boolean showLineNumbers = false;

            List<String> files = new ArrayList<>();
            for (String arg : args) {
                if (arg.equals("-n") || arg.equals("--number")) {
                    showLineNumbers = true;
                } else if (arg.equals("--")) {
                    continue;
                } else if (arg.startsWith("-")) {
                    // Ignore unknown options for MVP
                } else {
                    files.add(arg);
                }
            }

            if (files.isEmpty()) {
                String stdin = ctx.stdin().decodeUtf8();
                stdout.append(stdin);
            } else {
                int lineNum = 1;
                for (String file : files) {
                    try {
                        String path = file.startsWith("/") ? file : ctx.cwd() + "/" + file;
                        String content = ctx.fs().readFile(path).join();
                        if (showLineNumbers) {
                            String[] lines = content.split("\n", -1);
                            for (int i = 0; i < lines.length; i++) {
                                stdout.append(String.format("%6d\t%s", lineNum, lines[i]));
                                if (i < lines.length - 1 || content.endsWith("\n")) {
                                    stdout.append("\n");
                                }
                                lineNum++;
                            }
                        } else {
                            stdout.append(content);
                        }
                    } catch (Exception e) {
                        stderr += "cat: " + file + ": No such file or directory\n";
                        exitCode = 1;
                    }
                }
            }
            return new ExecResult(stdout.toString(), stderr, exitCode);
        });
    }
}
