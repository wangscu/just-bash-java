package com.justbash.commands.fold;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FoldCommand implements Command {
    @Override
    public String name() {
        return "fold";
    }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            int width = 80;
            boolean breakAtSpaces = false;
            List<String> files = new ArrayList<>();

            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if (arg.equals("-w") && i + 1 < args.size()) {
                    try {
                        width = Integer.parseInt(args.get(++i));
                        if (width < 1) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        return new ExecResult("", "fold: invalid number of columns: '" + args.get(i) + "'\n", 1);
                    }
                } else if (arg.startsWith("-w") && arg.length() > 2) {
                    try {
                        width = Integer.parseInt(arg.substring(2));
                        if (width < 1) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        return new ExecResult("", "fold: invalid number of columns: '" + arg.substring(2) + "'\n", 1);
                    }
                } else if (arg.equals("-s")) {
                    breakAtSpaces = true;
                } else if (arg.equals("--")) {
                    files.addAll(args.subList(i + 1, args.size()));
                    break;
                } else if (arg.startsWith("-")) {
                    return new ExecResult("", "fold: invalid option '" + arg + "'\n", 1);
                } else {
                    files.add(arg);
                }
            }

            StringBuilder output = new StringBuilder();
            if (files.isEmpty()) {
                String content = ctx.stdin().decodeUtf8();
                output.append(foldContent(content, width, breakAtSpaces));
            } else {
                for (String file : files) {
                    try {
                        String path = file.startsWith("/") ? file : ctx.cwd() + "/" + file;
                        String content = ctx.fs().readFile(path).join();
                        output.append(foldContent(content, width, breakAtSpaces));
                    } catch (Exception e) {
                        return new ExecResult(output.toString(), "fold: " + file + ": No such file or directory\n", 1);
                    }
                }
            }
            return new ExecResult(output.toString(), "", 0);
        });
    }

    private String foldContent(String content, int width, boolean breakAtSpaces) {
        if (content.isEmpty()) return "";
        String[] lines = content.split("\n", -1);
        boolean hadTrailingNewline = content.endsWith("\n");
        int end = lines.length;
        if (hadTrailingNewline && end > 0 && lines[end - 1].isEmpty()) {
            end--;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < end; i++) {
            String line = lines[i];
            if (line.isEmpty()) {
                sb.append('\n');
                continue;
            }
            while (line.length() > width) {
                if (breakAtSpaces) {
                    int breakPos = width;
                    while (breakPos > 0 && line.charAt(breakPos) != ' ') {
                        breakPos--;
                    }
                    if (breakPos == 0) breakPos = width;
                    sb.append(line, 0, breakPos).append('\n');
                    line = line.substring(breakPos).replaceFirst("^ ", "");
                } else {
                    sb.append(line, 0, width).append('\n');
                    line = line.substring(width);
                }
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }
}
