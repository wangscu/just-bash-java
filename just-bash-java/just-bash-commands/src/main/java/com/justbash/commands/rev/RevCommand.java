package com.justbash.commands.rev;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RevCommand implements Command {
    @Override
    public String name() {
        return "rev";
    }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> files = new ArrayList<>();
            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if (arg.equals("--")) {
                    files.addAll(args.subList(i + 1, args.size()));
                    break;
                } else if (arg.startsWith("-") && !arg.equals("-")) {
                    return new ExecResult("", "rev: invalid option '" + arg + "'\n", 1);
                } else {
                    files.add(arg);
                }
            }

            StringBuilder output = new StringBuilder();
            if (files.isEmpty()) {
                String content = ctx.stdin().decodeUtf8();
                output.append(processContent(content));
            } else {
                for (String file : files) {
                    if (file.equals("-")) {
                        output.append(processContent(ctx.stdin().decodeUtf8()));
                    } else {
                        try {
                            String path = file.startsWith("/") ? file : ctx.cwd() + "/" + file;
                            String content = ctx.fs().readFile(path).join();
                            output.append(processContent(content));
                        } catch (Exception e) {
                            return new ExecResult(output.toString(), "rev: " + file + ": No such file or directory\n", 1);
                        }
                    }
                }
            }
            return new ExecResult(output.toString(), "", 0);
        });
    }

    private String processContent(String content) {
        if (content.isEmpty()) return "";
        String[] lines = content.split("\n", -1);
        boolean hadTrailingNewline = content.endsWith("\n");
        int end = lines.length;
        if (hadTrailingNewline && end > 0 && lines[end - 1].isEmpty()) {
            end--;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < end; i++) {
            sb.append(new StringBuilder(lines[i]).reverse().toString()).append('\n');
        }
        return sb.toString();
    }
}
