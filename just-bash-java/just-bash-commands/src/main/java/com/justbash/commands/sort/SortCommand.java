package com.justbash.commands.sort;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SortCommand implements Command {
    @Override
    public String name() {
        return "sort";
    }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            boolean reverse = false;
            boolean numeric = false;
            boolean unique = false;
            boolean ignoreCase = false;
            boolean ignoreLeadingBlanks = false;
            String fieldDelimiter = null;
            String outputFile = null;
            boolean checkOnly = false;
            List<String> files = new ArrayList<>();

            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if (arg.equals("-r") || arg.equals("--reverse")) {
                    reverse = true;
                } else if (arg.equals("-n") || arg.equals("--numeric-sort")) {
                    numeric = true;
                } else if (arg.equals("-u") || arg.equals("--unique")) {
                    unique = true;
                } else if (arg.equals("-f") || arg.equals("--ignore-case")) {
                    ignoreCase = true;
                } else if (arg.equals("-b") || arg.equals("--ignore-leading-blanks")) {
                    ignoreLeadingBlanks = true;
                } else if (arg.equals("-c") || arg.equals("--check")) {
                    checkOnly = true;
                } else if (arg.equals("-t") || arg.equals("--field-separator")) {
                    fieldDelimiter = args.get(++i);
                } else if (arg.startsWith("-t") && arg.length() > 2) {
                    fieldDelimiter = arg.substring(2);
                } else if (arg.equals("-o") || arg.equals("--output")) {
                    outputFile = args.get(++i);
                } else if (arg.startsWith("-o") && arg.length() > 2) {
                    outputFile = arg.substring(2);
                } else if (arg.equals("--")) {
                    continue;
                } else if (arg.startsWith("-") && !arg.equals("-")) {
                    boolean hasUnknown = false;
                    for (char c : arg.substring(1).toCharArray()) {
                        switch (c) {
                            case 'r' -> reverse = true;
                            case 'n' -> numeric = true;
                            case 'u' -> unique = true;
                            case 'f' -> ignoreCase = true;
                            case 'b' -> ignoreLeadingBlanks = true;
                            case 'c' -> checkOnly = true;
                            default -> hasUnknown = true;
                        }
                    }
                    if (hasUnknown) {
                        return new ExecResult("", "sort: invalid option -- '" + arg.substring(1) + "'\n", 2);
                    }
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
                        return new ExecResult("", "sort: " + file + ": No such file or directory\n", 2);
                    }
                }
                content = sb.toString();
            }

            String[] rawLines = content.split("\n", -1);
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < rawLines.length; i++) {
                if (i == rawLines.length - 1 && rawLines[i].isEmpty() && content.endsWith("\n")) {
                    continue;
                }
                lines.add(rawLines[i]);
            }

            Comparator<String> comparator = createComparator(numeric, ignoreCase, ignoreLeadingBlanks, fieldDelimiter);
            if (reverse) {
                comparator = comparator.reversed();
            }

            if (checkOnly) {
                String checkFile = files.isEmpty() ? "-" : files.get(0);
                for (int i = 1; i < lines.size(); i++) {
                    if (comparator.compare(lines.get(i - 1), lines.get(i)) > 0) {
                        return new ExecResult("", "sort: " + checkFile + ":" + (i + 1) + ": disorder: " + lines.get(i) + "\n", 1);
                    }
                }
                return new ExecResult("", "", 0);
            }

            lines.sort(comparator);

            if (unique) {
                List<String> uniqueLines = new ArrayList<>();
                String prev = null;
                for (String line : lines) {
                    String cmpKey = ignoreCase ? line.toLowerCase() : line;
                    if (prev == null || !cmpKey.equals(prev)) {
                        uniqueLines.add(line);
                        prev = cmpKey;
                    }
                }
                lines = uniqueLines;
            }

            String output = String.join("\n", lines);
            if (!lines.isEmpty()) {
                output += "\n";
            }

            if (outputFile != null) {
                try {
                    String outPath = ctx.fs().resolvePath(ctx.cwd(), outputFile);
                    ctx.fs().writeFile(outPath, new com.justbash.fs.IFileSystem.StringContent(output)).join();
                    return new ExecResult("", "", 0);
                } catch (Exception e) {
                    return new ExecResult("", "sort: " + outputFile + ": " + e.getMessage() + "\n", 2);
                }
            }

            return new ExecResult(output, "", 0);
        });
    }

    private Comparator<String> createComparator(boolean numeric, boolean ignoreCase, boolean ignoreLeadingBlanks, String fieldDelimiter) {
        return (a, b) -> {
            String sa = a;
            String sb = b;
            if (ignoreLeadingBlanks) {
                sa = sa.replaceFirst("^\\s+", "");
                sb = sb.replaceFirst("^\\s+", "");
            }
            if (numeric) {
                try {
                    double da = Double.parseDouble(sa.trim());
                    double db = Double.parseDouble(sb.trim());
                    return Double.compare(da, db);
                } catch (NumberFormatException e) {
                    // Fall through to string comparison
                }
            }
            if (ignoreCase) {
                return sa.compareToIgnoreCase(sb);
            }
            return sa.compareTo(sb);
        };
    }
}
