package com.justbash.commands.tail;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TailCommand implements Command {
    @Override
    public String name() {
        return "tail";
    }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            int numLines = 10;
            List<String> files = new ArrayList<>();

            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if (arg.equals("-n") && i + 1 < args.size()) {
                    try {
                        numLines = Integer.parseInt(args.get(++i));
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                } else if (arg.startsWith("-n")) {
                    try {
                        numLines = Integer.parseInt(arg.substring(2));
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                } else if (arg.startsWith("-") && arg.length() > 1 && Character.isDigit(arg.charAt(1))) {
                    try {
                        numLines = Integer.parseInt(arg.substring(1));
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                } else if (arg.equals("--")) {
                    continue;
                } else if (arg.startsWith("-")) {
                    // ignore
                } else {
                    files.add(arg);
                }
            }

            if (files.isEmpty()) {
                String stdin = ctx.stdin().decodeUtf8();
                return tailContent(stdin, numLines, "");
            }

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            int exitCode = 0;
            boolean multiple = files.size() > 1;

            for (String file : files) {
                try {
                    String path = ctx.fs().resolvePath(ctx.cwd(), file);
                    String content = ctx.fs().readFile(path).join();
                    if (multiple) {
                        stdout.append("==> ").append(file).append(" <==\n");
                    }
                    var result = tailContent(content, numLines, "");
                    stdout.append(result.stdout());
                } catch (Exception e) {
                    stderr.append("tail: cannot open '")
                        .append(file)
                        .append("' for reading: No such file or directory\n");
                    exitCode = 1;
                }
            }
            return new ExecResult(stdout.toString(), stderr.toString(), exitCode);
        });
    }

    private ExecResult tailContent(String content, int numLines, String header) {
        String[] lines = content.split("\n", -1);
        int actualLines = content.endsWith("\n") ? lines.length - 1 : lines.length;
        StringBuilder out = new StringBuilder(header);
        int start = Math.max(0, actualLines - numLines);
        for (int i = start; i < actualLines; i++) {
            out.append(lines[i]);
            out.append("\n");
        }
        return new ExecResult(out.toString(), "", 0);
    }
}
