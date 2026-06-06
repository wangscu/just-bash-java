package com.justbash.commands.cut;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CutCommand implements Command {
    @Override
    public String name() {
        return "cut";
    }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            String delimiter = "\t";
            String fieldSpec = null;
            String charSpec = null;
            boolean suppressNoDelim = false;
            List<String> files = new ArrayList<>();

            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if (arg.equals("-d") && i + 1 < args.size()) {
                    delimiter = args.get(++i);
                } else if (arg.startsWith("-d") && arg.length() > 2) {
                    delimiter = arg.substring(2);
                } else if (arg.equals("-f") && i + 1 < args.size()) {
                    fieldSpec = args.get(++i);
                } else if (arg.startsWith("-f") && arg.length() > 2) {
                    fieldSpec = arg.substring(2);
                } else if (arg.equals("-c") && i + 1 < args.size()) {
                    charSpec = args.get(++i);
                } else if (arg.startsWith("-c") && arg.length() > 2) {
                    charSpec = arg.substring(2);
                } else if (arg.equals("-s") || arg.equals("--only-delimited")) {
                    suppressNoDelim = true;
                } else if (arg.equals("--")) {
                    continue;
                } else if (arg.startsWith("-")) {
                    boolean unknown = false;
                    for (char c : arg.substring(1).toCharArray()) {
                        if (c == 's') {
                            suppressNoDelim = true;
                        } else if ("dfc".indexOf(c) < 0) {
                            unknown = true;
                            break;
                        }
                    }
                    if (unknown) {
                        return new ExecResult("", "cut: invalid option -- '" + arg.substring(1) + "'\n", 1);
                    }
                } else {
                    files.add(arg);
                }
            }

            if (fieldSpec == null && charSpec == null) {
                return new ExecResult("", "cut: you must specify a list of bytes, characters, or fields\n", 1);
            }

            String spec = charSpec != null ? charSpec : fieldSpec;
            List<Range> ranges = parseRanges(spec);

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
                        return new ExecResult("", "cut: " + file + ": No such file or directory\n", 1);
                    }
                }
                content = sb.toString();
            }

            String[] lines = content.split("\n", -1);
            boolean hadTrailingNewline = content.endsWith("\n");
            if (hadTrailingNewline && lines.length > 0 && lines[lines.length - 1].isEmpty()) {
                String[] tmp = new String[lines.length - 1];
                System.arraycopy(lines, 0, tmp, 0, tmp.length);
                lines = tmp;
            }

            StringBuilder output = new StringBuilder();
            for (String line : lines) {
                if (charSpec != null) {
                    int[] codepoints = line.codePoints().toArray();
                    StringBuilder selected = new StringBuilder();
                    for (Range range : ranges) {
                        int start = range.start - 1;
                        int end = range.end == null ? codepoints.length : range.end;
                        for (int i = start; i < end && i < codepoints.length; i++) {
                            if (i >= 0) {
                                selected.appendCodePoint(codepoints[i]);
                            }
                        }
                    }
                    output.append(selected.toString()).append('\n');
                } else {
                    if (suppressNoDelim && !line.contains(delimiter)) {
                        continue;
                    }
                    String[] fields = line.split(delimiter, -1);
                    List<String> selected = new ArrayList<>();
                    for (Range range : ranges) {
                        int start = range.start - 1;
                        int end = range.end == null ? fields.length : range.end;
                        for (int i = start; i < end && i < fields.length; i++) {
                            if (i >= 0 && !selected.contains(fields[i])) {
                                selected.add(fields[i]);
                            }
                        }
                    }
                    output.append(String.join(delimiter, selected)).append('\n');
                }
            }

            return new ExecResult(output.toString(), "", 0);
        });
    }

    private List<Range> parseRanges(String spec) {
        List<Range> ranges = new ArrayList<>();
        for (String part : spec.split(",")) {
            if (part.contains("-")) {
                int dash = part.indexOf('-');
                String startStr = part.substring(0, dash);
                String endStr = part.substring(dash + 1);
                int start = startStr.isEmpty() ? 1 : Integer.parseInt(startStr);
                Integer end = endStr.isEmpty() ? null : Integer.parseInt(endStr);
                ranges.add(new Range(start, end));
            } else {
                int num = Integer.parseInt(part);
                ranges.add(new Range(num, num));
            }
        }
        return ranges;
    }

    private record Range(int start, Integer end) {}
}
