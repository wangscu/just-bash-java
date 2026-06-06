package com.justbash.commands.comm;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CommCommand implements Command {
    @Override
    public String name() {
        return "comm";
    }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            boolean suppressCol1 = false;
            boolean suppressCol2 = false;
            boolean suppressCol3 = false;
            List<String> files = new ArrayList<>();

            for (String arg : args) {
                if (arg.equals("-1")) {
                    suppressCol1 = true;
                } else if (arg.equals("-2")) {
                    suppressCol2 = true;
                } else if (arg.equals("-3")) {
                    suppressCol3 = true;
                } else if (arg.equals("--")) {
                    continue;
                } else if (arg.startsWith("-")) {
                    return new ExecResult("", "comm: invalid option '" + arg + "'\n", 1);
                } else {
                    files.add(arg);
                }
            }

            if (files.size() < 2) {
                return new ExecResult("", "comm: missing operand\n", 1);
            }

            List<String> lines1, lines2;
            try {
                String path1 = files.get(0).startsWith("/") ? files.get(0) : ctx.cwd() + "/" + files.get(0);
                lines1 = splitLines(ctx.fs().readFile(path1).join());
            } catch (Exception e) {
                return new ExecResult("", "comm: " + files.get(0) + ": No such file or directory\n", 1);
            }
            try {
                String path2 = files.get(1).startsWith("/") ? files.get(1) : ctx.cwd() + "/" + files.get(1);
                lines2 = splitLines(ctx.fs().readFile(path2).join());
            } catch (Exception e) {
                return new ExecResult("", "comm: " + files.get(1) + ": No such file or directory\n", 1);
            }

            StringBuilder output = new StringBuilder();
            int i = 0, j = 0;
            while (i < lines1.size() || j < lines2.size()) {
                String line1 = i < lines1.size() ? lines1.get(i) : null;
                String line2 = j < lines2.size() ? lines2.get(j) : null;

                if (line1 == null) {
                    if (!suppressCol2) {
                        output.append(padCol1(suppressCol1)).append(line2).append('\n');
                    }
                    j++;
                } else if (line2 == null) {
                    if (!suppressCol1) {
                        output.append(line1).append('\n');
                    }
                    i++;
                } else {
                    int cmp = line1.compareTo(line2);
                    if (cmp < 0) {
                        if (!suppressCol1) {
                            output.append(line1).append('\n');
                        }
                        i++;
                    } else if (cmp > 0) {
                        if (!suppressCol2) {
                            output.append(padCol1(suppressCol1)).append(line2).append('\n');
                        }
                        j++;
                    } else {
                        if (!suppressCol3) {
                            output.append(padCol1(suppressCol1)).append(padCol2(suppressCol2)).append(line1).append('\n');
                        }
                        i++;
                        j++;
                    }
                }
            }

            return new ExecResult(output.toString(), "", 0);
        });
    }

    private String padCol1(boolean suppressed) {
        return suppressed ? "" : "\t";
    }

    private String padCol2(boolean suppressed) {
        return suppressed ? "" : "\t";
    }

    private List<String> splitLines(String content) {
        List<String> lines = new ArrayList<>();
        if (content.isEmpty()) return lines;
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
