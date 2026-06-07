package com.justbash.commands.base64;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class Base64Command implements Command {
    @Override
    public String name() { return "base64"; }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            boolean decode = false;
            int wrapCols = 76;
            List<String> files = new ArrayList<>();
            boolean optionsDone = false;

            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if (optionsDone) {
                    files.add(arg);
                    continue;
                }
                if (arg.equals("--")) {
                    optionsDone = true;
                } else if (arg.equals("-d") || arg.equals("--decode")) {
                    decode = true;
                } else if (arg.equals("--help")) {
                    return help();
                } else if (arg.startsWith("-w")) {
                    String value;
                    if (arg.length() > 2) {
                        value = arg.substring(2);
                    } else if (i + 1 < args.size()) {
                        value = args.get(++i);
                    } else {
                        return new ExecResult("", "base64: option requires an argument -- 'w'\n", 1);
                    }
                    try {
                        wrapCols = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        return new ExecResult("", "base64: invalid wrap size: '" + value + "'\n", 1);
                    }
                } else if (arg.startsWith("--wrap")) {
                    String value;
                    if (arg.startsWith("--wrap=")) {
                        value = arg.substring("--wrap=".length());
                    } else if (i + 1 < args.size()) {
                        value = args.get(++i);
                    } else {
                        return new ExecResult("", "base64: option '--wrap' requires an argument\n", 1);
                    }
                    try {
                        wrapCols = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        return new ExecResult("", "base64: invalid wrap size: '" + value + "'\n", 1);
                    }
                } else if (arg.startsWith("-") && !arg.equals("-")) {
                    return new ExecResult("", "base64: invalid option -- '" + arg.substring(1) + "'\n", 1);
                } else {
                    files.add(arg);
                }
            }

            try {
                if (decode) {
                    return doDecode(ctx, files);
                }
                return doEncode(ctx, files, wrapCols);
            } catch (IllegalArgumentException e) {
                return new ExecResult("", "base64: invalid input\n", 1);
            }
        });
    }

    private ExecResult help() {
        String help = "Usage: base64 [OPTION]... [FILE]\n" +
            "Base64 encode or decode FILE, or standard input, to standard output.\n\n" +
            "  -d, --decode          decode data\n" +
            "  -w, --wrap=COLS       wrap encoded lines after COLS character (default 76, 0 to disable)\n" +
            "      --help            display this help and exit\n";
        return new ExecResult(help, "", 0);
    }

    private ExecResult doEncode(CommandContext ctx, List<String> files, int wrapCols) {
        byte[] data = readInput(ctx, files);
        if (data == null) {
            return fileNotFound();
        }

        String encoded = Base64.getEncoder().encodeToString(data);

        String output;
        if (wrapCols > 0 && !encoded.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < encoded.length(); i += wrapCols) {
                if (i > 0) {
                    sb.append('\n');
                }
                sb.append(encoded, i, Math.min(i + wrapCols, encoded.length()));
            }
            sb.append('\n');
            output = sb.toString();
        } else {
            output = encoded;
        }

        return new ExecResult(output, "", 0);
    }

    private ExecResult doDecode(CommandContext ctx, List<String> files) {
        byte[] data = readInput(ctx, files);
        if (data == null) {
            return fileNotFound();
        }

        String input = new String(data, java.nio.charset.StandardCharsets.UTF_8);
        String cleaned = input.replaceAll("\\s", "");

        if (cleaned.isEmpty()) {
            return new ExecResult("", "", 0);
        }

        byte[] decoded = Base64.getDecoder().decode(cleaned);

        char[] chars = new char[decoded.length];
        for (int i = 0; i < decoded.length; i++) {
            chars[i] = (char) (decoded[i] & 0xFF);
        }

        return new ExecResult(new String(chars), "", 0, Optional.of(ExecResult.StdoutKind.BYTES));
    }

    private byte[] readInput(CommandContext ctx, List<String> files) {
        if (files.isEmpty() || (files.size() == 1 && files.get(0).equals("-"))) {
            return ctx.stdin().toBytes();
        }

        List<byte[]> chunks = new ArrayList<>();
        for (String file : files) {
            if (file.equals("-")) {
                chunks.add(ctx.stdin().toBytes());
            } else {
                try {
                    String path = file.startsWith("/") ? file : ctx.cwd() + "/" + file;
                    byte[] content = ctx.fs().readFileBuffer(path).join();
                    chunks.add(content);
                } catch (Exception e) {
                    return null;
                }
            }
        }

        int total = 0;
        for (byte[] chunk : chunks) {
            total += chunk.length;
        }
        byte[] result = new byte[total];
        int offset = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, result, offset, chunk.length);
            offset += chunk.length;
        }
        return result;
    }

    private ExecResult fileNotFound() {
        return new ExecResult("", "base64: No such file or directory\n", 1);
    }
}
