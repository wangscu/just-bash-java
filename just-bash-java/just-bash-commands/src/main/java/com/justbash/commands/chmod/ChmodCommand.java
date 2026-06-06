package com.justbash.commands.chmod;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import com.justbash.fs.FsStat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ChmodCommand implements Command {
    @Override
    public String name() { return "chmod"; }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            if (args.size() < 2) {
                return new ExecResult("", "chmod: missing operand\n", 1);
            }

            boolean recursive = false;
            boolean verbose = false;
            int argIdx = 0;

            while (argIdx < args.size() && args.get(argIdx).startsWith("-")) {
                String arg = args.get(argIdx);
                if (arg.equals("-R") || arg.equals("--recursive")) {
                    recursive = true;
                    argIdx++;
                } else if (arg.equals("-v") || arg.equals("--verbose")) {
                    verbose = true;
                    argIdx++;
                } else if (arg.equals("--")) {
                    argIdx++;
                    break;
                } else if (arg.matches("^-[Rv]+$")) {
                    if (arg.contains("R")) recursive = true;
                    if (arg.contains("v")) verbose = true;
                    argIdx++;
                } else if (arg.matches("^[+-]?[rwxugo]+") || arg.matches("^\\d+$")) {
                    break;
                } else {
                    return new ExecResult("", "chmod: invalid option -- '" + arg.substring(1) + "'\n", 1);
                }
            }

            if (args.size() - argIdx < 2) {
                return new ExecResult("", "chmod: missing operand\n", 1);
            }

            String modeArg = args.get(argIdx);
            List<String> files = args.subList(argIdx + 1, args.size());
            boolean isNumericMode = modeArg.matches("^[0-7]+$");

            Integer numericMode = null;
            if (isNumericMode) {
                numericMode = Integer.parseInt(modeArg, 8);
            } else {
                try {
                    parseMode(modeArg, 0644);
                } catch (Exception e) {
                    return new ExecResult("", "chmod: invalid mode: '" + modeArg + "'\n", 1);
                }
            }

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            boolean anyError = false;

            for (String file : files) {
                String filePath = file.startsWith("/") ? file : ctx.cwd() + "/" + file;
                try {
                    int modeValue;
                    if (isNumericMode && numericMode != null) {
                        modeValue = numericMode;
                    } else {
                        FsStat stat = ctx.fs().stat(filePath).join();
                        modeValue = parseMode(modeArg, stat.mode());
                    }

                    ctx.fs().chmod(filePath, modeValue).join();
                    if (verbose) {
                        stdout.append(String.format("mode of '%s' changed to %04o%n", file, modeValue));
                    }

                    if (recursive) {
                        FsStat stat = ctx.fs().stat(filePath).join();
                        if (stat.isDirectory()) {
                            stdout.append(chmodRecursive(ctx, filePath,
                                isNumericMode ? numericMode : null,
                                isNumericMode ? null : modeArg, verbose));
                        }
                    }
                } catch (Exception e) {
                    stderr.append("chmod: cannot access '").append(file).append("': No such file or directory\n");
                    anyError = true;
                }
            }

            return new ExecResult(stdout.toString(), stderr.toString(), anyError ? 1 : 0);
        });
    }

    private String chmodRecursive(CommandContext ctx, String dir, Integer numericMode,
                                   String symbolicMode, boolean verbose) {
        StringBuilder output = new StringBuilder();
        try {
            List<String> entries = ctx.fs().readdir(dir).join();
            for (String entry : entries) {
                String fullPath = dir.equals("/") ? "/" + entry : dir + "/" + entry;
                int modeValue;
                if (numericMode != null) {
                    modeValue = numericMode;
                } else if (symbolicMode != null) {
                    FsStat stat = ctx.fs().stat(fullPath).join();
                    modeValue = parseMode(symbolicMode, stat.mode());
                } else {
                    modeValue = 0644;
                }

                ctx.fs().chmod(fullPath, modeValue).join();
                if (verbose) {
                    output.append(String.format("mode of '%s' changed to %04o%n", fullPath, modeValue));
                }

                FsStat stat = ctx.fs().stat(fullPath).join();
                if (stat.isDirectory()) {
                    output.append(chmodRecursive(ctx, fullPath, numericMode, symbolicMode, verbose));
                }
            }
        } catch (Exception e) {
            // Ignore read errors for MVP
        }
        return output.toString();
    }

    static int parseMode(String modeStr, int currentMode) {
        if (modeStr.matches("^[0-7]+$")) {
            return Integer.parseInt(modeStr, 8);
        }

        int mode = currentMode & 07777;
        String[] parts = modeStr.split(",");
        for (String part : parts) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([ugoa]*)([+\\-=])([rwxXst]*)$").matcher(part);
            if (!m.matches()) {
                throw new IllegalArgumentException("Invalid mode: " + modeStr);
            }

            String who = m.group(1);
            String op = m.group(2);
            String perms = m.group(3);

            if (who == null || who.isEmpty() || who.equals("a")) {
                who = "ugo";
            }

            int permBits = 0;
            if (perms.contains("r")) permBits |= 04;
            if (perms.contains("w")) permBits |= 02;
            if (perms.contains("x") || perms.contains("X")) permBits |= 01;

            int specialBits = 0;
            if (perms.contains("s")) {
                if (who.contains("u")) specialBits |= 04000;
                if (who.contains("g")) specialBits |= 02000;
            }
            if (perms.contains("t")) {
                specialBits |= 01000;
            }

            for (char w : who.toCharArray()) {
                int shift;
                if (w == 'u') shift = 6;
                else if (w == 'g') shift = 3;
                else if (w == 'o') shift = 0;
                else continue;
                int bits = permBits << shift;
                if ("+".equals(op)) {
                    mode |= bits;
                } else if ("-".equals(op)) {
                    mode &= ~bits;
                } else if ("=".equals(op)) {
                    mode &= ~(07 << shift);
                    mode |= bits;
                }
            }

            if ("+".equals(op)) {
                mode |= specialBits;
            } else if ("-".equals(op)) {
                mode &= ~specialBits;
            } else if ("=".equals(op)) {
                if (perms.contains("s")) {
                    if (who.contains("u")) {
                        mode &= ~04000;
                        mode |= specialBits & 04000;
                    }
                    if (who.contains("g")) {
                        mode &= ~02000;
                        mode |= specialBits & 02000;
                    }
                }
                if (perms.contains("t")) {
                    mode &= ~01000;
                    mode |= specialBits & 01000;
                }
            }
        }
        return mode;
    }
}
