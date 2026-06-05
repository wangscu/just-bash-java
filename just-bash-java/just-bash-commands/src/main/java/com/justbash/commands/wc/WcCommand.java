package com.justbash.commands.wc;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class WcCommand implements Command {
    @Override
    public String name() {
        return "wc";
    }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            boolean linesOnly = false;
            boolean wordsOnly = false;
            boolean bytesOnly = false;
            List<String> files = new ArrayList<>();

            for (String arg : args) {
                if (arg.equals("-l")) {
                    linesOnly = true;
                } else if (arg.equals("-w")) {
                    wordsOnly = true;
                } else if (arg.equals("-c")) {
                    bytesOnly = true;
                } else if (arg.equals("--")) {
                    continue;
                } else if (arg.startsWith("-")) {
                    // ignore
                } else {
                    files.add(arg);
                }
            }

            boolean defaultMode = !linesOnly && !wordsOnly && !bytesOnly;

            if (files.isEmpty()) {
                String stdin = ctx.stdin().decodeUtf8();
                return countContent(stdin, "", linesOnly, wordsOnly, bytesOnly, defaultMode);
            }

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            int exitCode = 0;
            boolean multiple = files.size() > 1;
            long totalLines = 0;
            long totalWords = 0;
            long totalBytes = 0;

            for (String file : files) {
                try {
                    String path = ctx.fs().resolvePath(ctx.cwd(), file);
                    String content = ctx.fs().readFile(path).join();
                    var result = countContent(content, file, linesOnly, wordsOnly, bytesOnly, defaultMode);
                    stdout.append(result.stdout());
                    if (multiple && defaultMode) {
                        String[] parts = result.stdout().trim().split("\\s+");
                        if (parts.length >= 3) {
                            totalLines += Long.parseLong(parts[0]);
                            totalWords += Long.parseLong(parts[1]);
                            totalBytes += Long.parseLong(parts[2]);
                        }
                    }
                } catch (Exception e) {
                    stderr.append("wc: ").append(file).append(": No such file or directory\n");
                    exitCode = 1;
                }
            }

            if (multiple && defaultMode) {
                stdout.append(String.format("%7d %7d %7d total\n", totalLines, totalWords, totalBytes));
            }

            return new ExecResult(stdout.toString(), stderr.toString(), exitCode);
        });
    }

    private ExecResult countContent(
        String content,
        String filename,
        boolean linesOnly,
        boolean wordsOnly,
        boolean bytesOnly,
        boolean defaultMode
    ) {
        long lines;
        if (content.isEmpty()) {
            lines = 0;
        } else {
            lines = content.split("\n", -1).length;
            if (content.endsWith("\n")) {
                lines--;
            }
        }

        long words = 0;
        if (!content.trim().isEmpty()) {
            words = content.trim().split("\\s+").length;
        }
        long bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;

        StringBuilder out = new StringBuilder();
        if (defaultMode) {
            out.append(String.format("%7d %7d %7d", lines, words, bytes));
        } else {
            if (linesOnly) {
                out.append(String.format("%7d", lines));
            } else if (wordsOnly) {
                out.append(String.format("%7d", words));
            } else if (bytesOnly) {
                out.append(String.format("%7d", bytes));
            }
        }
        if (!filename.isEmpty()) {
            out.append(" ").append(filename);
        }
        out.append("\n");
        return new ExecResult(out.toString(), "", 0);
    }
}
