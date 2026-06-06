package com.justbash.commands.nl;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class NlCommand implements Command {
    @Override
    public String name() {
        return "nl";
    }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            String bodyStyle = "t"; // a=all, t=non-empty, n=none
            String numberFormat = "rn"; // ln=left, rn=right, rz=right-zero
            int width = 6;
            String separator = "\t";
            int startNumber = 1;
            int increment = 1;
            List<String> files = new ArrayList<>();

            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if (arg.equals("-b") && i + 1 < args.size()) {
                    bodyStyle = args.get(++i);
                    if (!bodyStyle.equals("a") && !bodyStyle.equals("t") && !bodyStyle.equals("n")) {
                        return new ExecResult("", "nl: invalid body numbering style: '" + bodyStyle + "'\n", 1);
                    }
                } else if (arg.startsWith("-b") && arg.length() > 2) {
                    bodyStyle = arg.substring(2);
                    if (!bodyStyle.equals("a") && !bodyStyle.equals("t") && !bodyStyle.equals("n")) {
                        return new ExecResult("", "nl: invalid body numbering style: '" + bodyStyle + "'\n", 1);
                    }
                } else if (arg.equals("-n") && i + 1 < args.size()) {
                    numberFormat = args.get(++i);
                    if (!numberFormat.equals("ln") && !numberFormat.equals("rn") && !numberFormat.equals("rz")) {
                        return new ExecResult("", "nl: invalid line numbering format: '" + numberFormat + "'\n", 1);
                    }
                } else if (arg.startsWith("-n") && arg.length() > 2) {
                    numberFormat = arg.substring(2);
                    if (!numberFormat.equals("ln") && !numberFormat.equals("rn") && !numberFormat.equals("rz")) {
                        return new ExecResult("", "nl: invalid line numbering format: '" + numberFormat + "'\n", 1);
                    }
                } else if (arg.equals("-w") && i + 1 < args.size()) {
                    try {
                        width = Integer.parseInt(args.get(++i));
                        if (width < 1) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        return new ExecResult("", "nl: invalid line number field width: '" + args.get(i) + "'\n", 1);
                    }
                } else if (arg.startsWith("-w") && arg.length() > 2) {
                    try {
                        width = Integer.parseInt(arg.substring(2));
                        if (width < 1) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        return new ExecResult("", "nl: invalid line number field width: '" + arg.substring(2) + "'\n", 1);
                    }
                } else if (arg.equals("-s") && i + 1 < args.size()) {
                    separator = args.get(++i);
                } else if (arg.startsWith("-s") && arg.length() > 2) {
                    separator = arg.substring(2);
                } else if (arg.equals("-v") && i + 1 < args.size()) {
                    try {
                        startNumber = Integer.parseInt(args.get(++i));
                    } catch (NumberFormatException e) {
                        return new ExecResult("", "nl: invalid starting line number: '" + args.get(i) + "'\n", 1);
                    }
                } else if (arg.startsWith("-v") && arg.length() > 2) {
                    try {
                        startNumber = Integer.parseInt(arg.substring(2));
                    } catch (NumberFormatException e) {
                        return new ExecResult("", "nl: invalid starting line number: '" + arg.substring(2) + "'\n", 1);
                    }
                } else if (arg.equals("-i") && i + 1 < args.size()) {
                    try {
                        increment = Integer.parseInt(args.get(++i));
                    } catch (NumberFormatException e) {
                        return new ExecResult("", "nl: invalid line number increment: '" + args.get(i) + "'\n", 1);
                    }
                } else if (arg.startsWith("-i") && arg.length() > 2) {
                    try {
                        increment = Integer.parseInt(arg.substring(2));
                    } catch (NumberFormatException e) {
                        return new ExecResult("", "nl: invalid line number increment: '" + arg.substring(2) + "'\n", 1);
                    }
                } else if (arg.equals("--")) {
                    files.addAll(args.subList(i + 1, args.size()));
                    break;
                } else if (arg.startsWith("-")) {
                    return new ExecResult("", "nl: invalid option '" + arg + "'\n", 1);
                } else {
                    files.add(arg);
                }
            }

            int lineNumber = startNumber;
            StringBuilder output = new StringBuilder();

            if (files.isEmpty()) {
                String content = ctx.stdin().decodeUtf8();
                var result = processContent(content, bodyStyle, numberFormat, width, separator, increment, lineNumber);
                output.append(result.output);
            } else {
                for (String file : files) {
                    try {
                        String path = file.startsWith("/") ? file : ctx.cwd() + "/" + file;
                        String content = ctx.fs().readFile(path).join();
                        var result = processContent(content, bodyStyle, numberFormat, width, separator, increment, lineNumber);
                        output.append(result.output);
                        lineNumber = result.nextNumber;
                    } catch (Exception e) {
                        return new ExecResult(output.toString(), "nl: " + file + ": No such file or directory\n", 1);
                    }
                }
            }

            return new ExecResult(output.toString(), "", 0);
        });
    }

    private Result processContent(String content, String bodyStyle, String numberFormat,
                                   int width, String separator, int increment, int startNumber) {
        StringBuilder sb = new StringBuilder();
        int lineNumber = startNumber;
        if (content.isEmpty()) {
            return new Result("", lineNumber);
        }
        String[] lines = content.split("\n", -1);
        boolean hadTrailingNewline = content.endsWith("\n");
        int end = lines.length;
        if (hadTrailingNewline && end > 0 && lines[end - 1].isEmpty()) {
            end--;
        }
        for (int i = 0; i < end; i++) {
            String line = lines[i];
            boolean shouldNumber = switch (bodyStyle) {
                case "a" -> true;
                case "t" -> !line.trim().isEmpty();
                case "n" -> false;
                default -> !line.trim().isEmpty();
            };
            if (shouldNumber) {
                String formatted = formatNumber(lineNumber, numberFormat, width);
                sb.append(formatted).append(separator).append(line).append('\n');
                lineNumber += increment;
            } else {
                sb.append(" ".repeat(width)).append(separator).append(line).append('\n');
            }
        }
        return new Result(sb.toString(), lineNumber);
    }

    private String formatNumber(int num, String format, int width) {
        String s = String.valueOf(num);
        return switch (format) {
            case "ln" -> String.format("%-" + width + "s", s);
            case "rn" -> String.format("%" + width + "s", s);
            case "rz" -> String.format("%0" + width + "d", num);
            default -> String.format("%" + width + "s", s);
        };
    }

    private record Result(String output, int nextNumber) {}
}
