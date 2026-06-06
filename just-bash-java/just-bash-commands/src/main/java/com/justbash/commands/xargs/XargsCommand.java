package com.justbash.commands.xargs;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class XargsCommand implements Command {
    @Override
    public String name() {
        return "xargs";
    }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            int maxArgs = Integer.MAX_VALUE;
            boolean printOnly = false;
            String delimiter = null;
            List<String> cmdArgs = new ArrayList<>();
            boolean foundPlaceholders = false;

            int i = 0;
            while (i < args.size()) {
                String arg = args.get(i);
                if (arg.equals("-n") && i + 1 < args.size()) {
                    try {
                        maxArgs = Integer.parseInt(args.get(++i));
                    } catch (NumberFormatException e) {
                        return new ExecResult("", "xargs: invalid number\n", 1);
                    }
                } else if (arg.equals("-t")) {
                    printOnly = true;
                } else if (arg.equals("-d") && i + 1 < args.size()) {
                    delimiter = args.get(++i);
                } else if (arg.equals("-I") && i + 1 < args.size()) {
                    // placeholder for replacement string - simplified MVP
                    i++;
                } else if (arg.equals("-0")) {
                    delimiter = "\0";
                } else if (arg.equals("--")) {
                    i++;
                    break;
                } else if (arg.startsWith("-")) {
                    // ignore unknown options
                } else {
                    break;
                }
                i++;
            }

            while (i < args.size()) {
                cmdArgs.add(args.get(i++));
            }

            if (cmdArgs.isEmpty()) {
                cmdArgs.add("echo");
            }

            String stdin = ctx.stdin().decodeUtf8();
            String[] items;
            if (delimiter != null) {
                items = stdin.split(delimiter.equals("\0") ? "\0" : delimiter, -1);
            } else {
                items = stdin.split("\\s+");
            }

            // Filter empty items
            List<String> nonEmptyItems = new ArrayList<>();
            for (String item : items) {
                if (!item.isEmpty()) {
                    nonEmptyItems.add(item);
                }
            }

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            int exitCode = 0;

            // Process in batches of maxArgs
            for (int batchStart = 0; batchStart < nonEmptyItems.size(); batchStart += maxArgs) {
                List<String> batch = new ArrayList<>(cmdArgs);
                int batchEnd = Math.min(batchStart + maxArgs, nonEmptyItems.size());
                for (int j = batchStart; j < batchEnd; j++) {
                    batch.add(nonEmptyItems.get(j));
                }

                if (printOnly) {
                    stdout.append(String.join(" ", batch)).append('\n');
                    continue;
                }

                // Execute command via ctx.exec()
                if (ctx.exec().isPresent()) {
                    String cmdLine = String.join(" ", batch);
                    try {
                        var result = ctx.exec().get().apply(cmdLine, null).join();
                        stdout.append(result.stdout());
                        stderr.append(result.stderr());
                        if (result.exitCode() != 0) {
                            exitCode = result.exitCode();
                        }
                    } catch (Exception e) {
                        stderr.append("xargs: ").append(e.getMessage()).append('\n');
                        exitCode = 1;
                    }
                }
            }

            return new ExecResult(stdout.toString(), stderr.toString(), exitCode);
        });
    }
}
