package com.justbash.commands.column;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ColumnCommand implements Command {
    @Override
    public String name() {
        return "column";
    }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            boolean tableMode = false;
            String delimiter = null;
            List<String> files = new ArrayList<>();

            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if (arg.equals("-t")) {
                    tableMode = true;
                } else if (arg.equals("-s") && i + 1 < args.size()) {
                    delimiter = args.get(++i);
                } else if (arg.startsWith("-s") && arg.length() > 2) {
                    delimiter = arg.substring(2);
                } else if (arg.equals("--")) {
                    files.addAll(args.subList(i + 1, args.size()));
                    break;
                } else if (arg.startsWith("-")) {
                    return new ExecResult("", "column: invalid option '" + arg + "'\n", 1);
                } else {
                    files.add(arg);
                }
            }

            String content;
            if (files.isEmpty()) {
                content = ctx.stdin().decodeUtf8();
            } else {
                StringBuilder sb = new StringBuilder();
                for (String file : files) {
                    try {
                        String path = file.startsWith("/") ? file : ctx.cwd() + "/" + file;
                        sb.append(ctx.fs().readFile(path).join());
                    } catch (Exception e) {
                        return new ExecResult("", "column: " + file + ": No such file or directory\n", 1);
                    }
                }
                content = sb.toString();
            }

            if (content.isEmpty()) {
                return new ExecResult("", "", 0);
            }

            String[] lines = content.split("\n", -1);
            int end = lines.length;
            if (content.endsWith("\n") && end > 0 && lines[end - 1].isEmpty()) {
                end--;
            }

            List<String[]> rows = new ArrayList<>();
            int maxCols = 0;
            for (int i = 0; i < end; i++) {
                String[] cols;
                if (delimiter != null) {
                    cols = lines[i].split(delimiter, -1);
                } else {
                    cols = lines[i].trim().split("\\s+");
                }
                rows.add(cols);
                maxCols = Math.max(maxCols, cols.length);
            }

            if (tableMode) {
                // Calculate column widths
                int[] widths = new int[maxCols];
                for (String[] cols : rows) {
                    for (int j = 0; j < cols.length; j++) {
                        widths[j] = Math.max(widths[j], cols[j].length());
                    }
                }

                StringBuilder output = new StringBuilder();
                for (String[] cols : rows) {
                    for (int j = 0; j < cols.length; j++) {
                        if (j > 0) output.append("  ");
                        output.append(String.format("%-" + widths[j] + "s", cols[j]));
                    }
                    output.append('\n');
                }
                return new ExecResult(output.toString(), "", 0);
            } else {
                // Default mode: just fill columns
                StringBuilder output = new StringBuilder();
                for (String[] cols : rows) {
                    output.append(String.join("  ", cols)).append('\n');
                }
                return new ExecResult(output.toString(), "", 0);
            }
        });
    }
}
