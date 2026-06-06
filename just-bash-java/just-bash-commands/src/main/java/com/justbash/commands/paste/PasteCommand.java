package com.justbash.commands.paste;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PasteCommand implements Command {
    @Override
    public String name() {
        return "paste";
    }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            String delimiters = "\t";
            List<String> files = new ArrayList<>();

            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if (arg.equals("-d") && i + 1 < args.size()) {
                    delimiters = args.get(++i);
                } else if (arg.startsWith("-d") && arg.length() > 2) {
                    delimiters = arg.substring(2);
                } else if (arg.equals("--")) {
                    files.addAll(args.subList(i + 1, args.size()));
                    break;
                } else if (arg.startsWith("-")) {
                    return new ExecResult("", "paste: invalid option '" + arg + "'\n", 1);
                } else {
                    files.add(arg);
                }
            }

            List<List<String>> fileLines = new ArrayList<>();
            int maxLines = 0;

            if (files.isEmpty()) {
                String content = ctx.stdin().decodeUtf8();
                List<String> lines = splitLines(content);
                fileLines.add(lines);
                maxLines = lines.size();
            } else {
                for (String file : files) {
                    try {
                        String path = file.startsWith("/") ? file : ctx.cwd() + "/" + file;
                        String content = ctx.fs().readFile(path).join();
                        List<String> lines = splitLines(content);
                        fileLines.add(lines);
                        maxLines = Math.max(maxLines, lines.size());
                    } catch (Exception e) {
                        return new ExecResult("", "paste: " + file + ": No such file or directory\n", 1);
                    }
                }
            }

            StringBuilder output = new StringBuilder();
            for (int lineIdx = 0; lineIdx < maxLines; lineIdx++) {
                for (int fileIdx = 0; fileIdx < fileLines.size(); fileIdx++) {
                    List<String> lines = fileLines.get(fileIdx);
                    if (lineIdx < lines.size()) {
                        output.append(lines.get(lineIdx));
                    }
                    if (fileIdx < fileLines.size() - 1) {
                        char delim = delimiters.charAt(fileIdx % delimiters.length());
                        output.append(delim);
                    }
                }
                output.append('\n');
            }

            return new ExecResult(output.toString(), "", 0);
        });
    }

    private List<String> splitLines(String content) {
        List<String> lines = new ArrayList<>();
        if (content.isEmpty()) {
            return lines;
        }
        String[] parts = content.split("\n", -1);
        int end = parts.length;
        if (content.endsWith("\n") && end > 0 && parts[end - 1].isEmpty()) {
            end--;
        }
        for (int i = 0; i < end; i++) {
            lines.add(parts[i]);
        }
        return lines;
    }
}
