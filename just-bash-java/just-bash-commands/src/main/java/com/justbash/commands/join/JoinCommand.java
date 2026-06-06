package com.justbash.commands.join;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class JoinCommand implements Command {
    @Override
    public String name() {
        return "join";
    }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            int joinField1 = 0; // 0-indexed first field
            int joinField2 = 0;
            String delimiter = null; // null = whitespace (default)
            List<String> files = new ArrayList<>();

            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if (arg.equals("-1") && i + 1 < args.size()) {
                    try {
                        joinField1 = Integer.parseInt(args.get(++i)) - 1;
                    } catch (NumberFormatException e) {
                        return new ExecResult("", "join: invalid field number: '" + args.get(i) + "'\n", 1);
                    }
                } else if (arg.equals("-2") && i + 1 < args.size()) {
                    try {
                        joinField2 = Integer.parseInt(args.get(++i)) - 1;
                    } catch (NumberFormatException e) {
                        return new ExecResult("", "join: invalid field number: '" + args.get(i) + "'\n", 1);
                    }
                } else if (arg.equals("-t") && i + 1 < args.size()) {
                    delimiter = args.get(++i);
                } else if (arg.startsWith("-t") && arg.length() > 2) {
                    delimiter = arg.substring(2);
                } else if (arg.equals("--")) {
                    files.addAll(args.subList(i + 1, args.size()));
                    break;
                } else if (arg.startsWith("-")) {
                    return new ExecResult("", "join: invalid option '" + arg + "'\n", 1);
                } else {
                    files.add(arg);
                }
            }

            if (files.size() < 2) {
                return new ExecResult("", "join: missing operand\n", 1);
            }

            List<String[]> lines1, lines2;
            try {
                String path1 = files.get(0).startsWith("/") ? files.get(0) : ctx.cwd() + "/" + files.get(0);
                lines1 = parseFile(ctx.fs().readFile(path1).join(), delimiter);
            } catch (Exception e) {
                return new ExecResult("", "join: " + files.get(0) + ": No such file or directory\n", 1);
            }
            try {
                String path2 = files.get(1).startsWith("/") ? files.get(1) : ctx.cwd() + "/" + files.get(1);
                lines2 = parseFile(ctx.fs().readFile(path2).join(), delimiter);
            } catch (Exception e) {
                return new ExecResult("", "join: " + files.get(1) + ": No such file or directory\n", 1);
            }

            // Build index for file2
            java.util.LinkedHashMap<String, List<String[]>> index = new java.util.LinkedHashMap<>();
            for (String[] fields : lines2) {
                String key = joinField2 < fields.length ? fields[joinField2] : "";
                index.computeIfAbsent(key, k -> new ArrayList<>()).add(fields);
            }

            String outDelim = delimiter != null ? delimiter : " ";
            StringBuilder output = new StringBuilder();

            for (String[] fields1 : lines1) {
                String key = joinField1 < fields1.length ? fields1[joinField1] : "";
                List<String[]> matches = index.get(key);
                if (matches != null) {
                    for (String[] fields2 : matches) {
                        output.append(joinLine(fields1, fields2, joinField1, joinField2, outDelim)).append('\n');
                    }
                }
            }

            return new ExecResult(output.toString(), "", 0);
        });
    }

    private List<String[]> parseFile(String content, String delimiter) {
        List<String[]> result = new ArrayList<>();
        if (content.isEmpty()) return result;
        String[] lines = content.split("\n", -1);
        int end = lines.length;
        if (content.endsWith("\n") && end > 0 && lines[end - 1].isEmpty()) {
            end--;
        }
        for (int i = 0; i < end; i++) {
            String line = lines[i];
            if (delimiter != null) {
                result.add(line.split(delimiter, -1));
            } else {
                result.add(line.trim().split("\\s+"));
            }
        }
        return result;
    }

    private String joinLine(String[] fields1, String[] fields2, int joinField1, int joinField2, String delimiter) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        // Output fields from file1
        for (int i = 0; i < fields1.length; i++) {
            if (!first) sb.append(delimiter);
            sb.append(fields1[i]);
            first = false;
        }
        // Output non-join fields from file2
        for (int i = 0; i < fields2.length; i++) {
            if (i == joinField2) continue;
            if (!first) sb.append(delimiter);
            sb.append(fields2[i]);
            first = false;
        }
        return sb.toString();
    }
}
