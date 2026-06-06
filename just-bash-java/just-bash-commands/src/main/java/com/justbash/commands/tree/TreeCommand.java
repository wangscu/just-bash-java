package com.justbash.commands.tree;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TreeCommand implements Command {
    @Override
    public String name() {
        return "tree";
    }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            String dir = ctx.cwd();
            int maxDepth = Integer.MAX_VALUE;
            boolean showHidden = false;

            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if (arg.equals("-L") && i + 1 < args.size()) {
                    try {
                        maxDepth = Integer.parseInt(args.get(++i));
                    } catch (NumberFormatException e) {
                        return new ExecResult("", "tree: invalid level\n", 1);
                    }
                } else if (arg.equals("-a")) {
                    showHidden = true;
                } else if (arg.startsWith("-")) {
                    // ignore other options
                } else {
                    dir = arg.startsWith("/") ? arg : ctx.cwd() + "/" + arg;
                }
            }

            StringBuilder out = new StringBuilder();
            try {
                out.append(dir).append('\n');
                var entries = ctx.fs().readdirWithFileTypes(dir).join();
                List<String> names = new ArrayList<>();
                for (var entry : entries) {
                    if (!showHidden && entry.name().startsWith(".")) continue;
                    names.add(entry.name());
                }
                names.sort(String::compareTo);

                int[] counts = new int[] { 0 };
                for (int i = 0; i < names.size(); i++) {
                    String name = names.get(i);
                    boolean isLast = i == names.size() - 1;
                    String prefix = isLast ? "└── " : "├── ";
                    out.append(prefix).append(name).append('\n');

                    String childPath = ctx.fs().resolvePath(dir, name);
                    try {
                        var stat = ctx.fs().stat(childPath).join();
                        if (stat.isDirectory() && maxDepth > 1) {
                            traverse(ctx, childPath, isLast ? "    " : "│   ", 1, maxDepth, showHidden, out, counts);
                        }
                    } catch (Exception e) {
                        // skip
                    }
                }
                return new ExecResult(out.toString(), "", 0);
            } catch (Exception e) {
                return new ExecResult("", "tree: " + dir + ": No such file or directory\n", 1);
            }
        });
    }

    private void traverse(CommandContext ctx, String dir, String indent, int depth, int maxDepth, boolean showHidden, StringBuilder out, int[] counts) throws Exception {
        var entries = ctx.fs().readdirWithFileTypes(dir).join();
        List<String> names = new ArrayList<>();
        for (var entry : entries) {
            if (!showHidden && entry.name().startsWith(".")) continue;
            names.add(entry.name());
        }
        names.sort(String::compareTo);

        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            boolean isLast = i == names.size() - 1;
            String prefix = isLast ? "└── " : "├── ";
            out.append(indent).append(prefix).append(name).append('\n');
            counts[0]++;

            String childPath = ctx.fs().resolvePath(dir, name);
            try {
                var stat = ctx.fs().stat(childPath).join();
                if (stat.isDirectory() && depth + 1 < maxDepth) {
                    traverse(ctx, childPath, indent + (isLast ? "    " : "│   "), depth + 1, maxDepth, showHidden, out, counts);
                }
            } catch (Exception e) {
                // skip
            }
        }
    }
}
