package com.justbash.commands.split;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SplitCommand implements Command {
    @Override
    public String name() {
        return "split";
    }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            int linesPerFile = 1000;
            long bytesPerFile = -1;
            String prefix = "x";
            String file = null;

            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if (arg.equals("-l") && i + 1 < args.size()) {
                    try {
                        linesPerFile = Integer.parseInt(args.get(++i));
                        if (linesPerFile < 1) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        return new ExecResult("", "split: invalid number of lines: '" + args.get(i) + "'\n", 1);
                    }
                } else if (arg.startsWith("-l") && arg.length() > 2) {
                    try {
                        linesPerFile = Integer.parseInt(arg.substring(2));
                        if (linesPerFile < 1) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        return new ExecResult("", "split: invalid number of lines: '" + arg.substring(2) + "'\n", 1);
                    }
                } else if (arg.equals("-b") && i + 1 < args.size()) {
                    String val = args.get(++i);
                    bytesPerFile = parseBytes(val);
                    if (bytesPerFile < 0) {
                        return new ExecResult("", "split: invalid number of bytes: '" + val + "'\n", 1);
                    }
                } else if (arg.startsWith("-b") && arg.length() > 2) {
                    bytesPerFile = parseBytes(arg.substring(2));
                    if (bytesPerFile < 0) {
                        return new ExecResult("", "split: invalid number of bytes: '" + arg.substring(2) + "'\n", 1);
                    }
                } else if (arg.equals("--")) {
                    if (i + 1 < args.size()) {
                        file = args.get(i + 1);
                        if (i + 2 < args.size()) prefix = args.get(i + 2);
                    }
                    break;
                } else if (arg.startsWith("-")) {
                    return new ExecResult("", "split: invalid option '" + arg + "'\n", 1);
                } else if (file == null) {
                    file = arg;
                } else {
                    prefix = arg;
                }
            }

            String content;
            if (file == null || file.equals("-")) {
                content = ctx.stdin().decodeUtf8();
            } else {
                try {
                    String path = file.startsWith("/") ? file : ctx.cwd() + "/" + file;
                    content = ctx.fs().readFile(path).join();
                } catch (Exception e) {
                    return new ExecResult("", "split: " + file + ": No such file or directory\n", 1);
                }
            }

            try {
                if (bytesPerFile > 0) {
                    splitByBytes(ctx, content, bytesPerFile, prefix);
                } else {
                    splitByLines(ctx, content, linesPerFile, prefix);
                }
            } catch (Exception e) {
                return new ExecResult("", "split: " + e.getMessage() + "\n", 1);
            }

            return new ExecResult("", "", 0);
        });
    }

    private long parseBytes(String val) {
        try {
            long multiplier = 1;
            String num = val;
            if (val.endsWith("K") || val.endsWith("k")) {
                multiplier = 1024;
                num = val.substring(0, val.length() - 1);
            } else if (val.endsWith("M") || val.endsWith("m")) {
                multiplier = 1024 * 1024;
                num = val.substring(0, val.length() - 1);
            } else if (val.endsWith("G") || val.endsWith("g")) {
                multiplier = 1024L * 1024 * 1024;
                num = val.substring(0, val.length() - 1);
            }
            return Long.parseLong(num) * multiplier;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void splitByLines(CommandContext ctx, String content, int linesPerFile, String prefix) throws Exception {
        String[] lines = content.split("\n", -1);
        boolean hadTrailingNewline = content.endsWith("\n");
        int end = lines.length;
        if (hadTrailingNewline && end > 0 && lines[end - 1].isEmpty()) {
            end--;
        }

        int fileIdx = 0;
        for (int i = 0; i < end; i += linesPerFile) {
            String suffix = generateSuffix(fileIdx++);
            StringBuilder sb = new StringBuilder();
            for (int j = i; j < Math.min(i + linesPerFile, end); j++) {
                sb.append(lines[j]).append('\n');
            }
            String path = ctx.fs().resolvePath(ctx.cwd(), prefix + suffix);
            ctx.fs().writeFile(path, new com.justbash.fs.IFileSystem.StringContent(sb.toString())).join();
        }
    }

    private void splitByBytes(CommandContext ctx, String content, long bytesPerFile, String prefix) throws Exception {
        byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int fileIdx = 0;
        for (int i = 0; i < bytes.length; i += bytesPerFile) {
            String suffix = generateSuffix(fileIdx++);
            int end = (int) Math.min(i + bytesPerFile, bytes.length);
            byte[] chunk = new byte[end - i];
            System.arraycopy(bytes, i, chunk, 0, chunk.length);
            String path = ctx.fs().resolvePath(ctx.cwd(), prefix + suffix);
            ctx.fs().writeFile(path, new com.justbash.fs.IFileSystem.StringContent(new String(chunk, java.nio.charset.StandardCharsets.UTF_8))).join();
        }
    }

    private String generateSuffix(int n) {
        StringBuilder sb = new StringBuilder();
        do {
            sb.append((char) ('a' + (n % 26)));
            n /= 26;
        } while (n > 0);
        return sb.reverse().toString();
    }
}
