package com.justbash.commands.ls;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import com.justbash.fs.DirentEntry;
import com.justbash.fs.FsStat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LsCommand implements Command {
    @Override
    public String name() {
        return "ls";
    }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            boolean longFormat = false;
            boolean allFiles = false;
            List<String> paths = new ArrayList<>();

            for (String arg : args) {
                if (arg.equals("-l")) {
                    longFormat = true;
                } else if (arg.equals("-a") || arg.equals("-A")) {
                    allFiles = true;
                } else if (arg.equals("-la") || arg.equals("-al")) {
                    longFormat = true;
                    allFiles = true;
                } else if (arg.equals("--")) {
                    continue;
                } else if (arg.startsWith("-")) {
                    // ignore unknown options
                } else {
                    paths.add(arg);
                }
            }

            if (paths.isEmpty()) {
                paths.add(".");
            }

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            int exitCode = 0;

            for (String path : paths) {
                try {
                    String fullPath = ctx.fs().resolvePath(ctx.cwd(), path);
                    if (fullPath.endsWith("/.")) {
                        fullPath = fullPath.substring(0, fullPath.length() - 2);
                    }
                    FsStat stat = ctx.fs().stat(fullPath).join();

                    if (stat.isDirectory()) {
                        List<DirentEntry> entries = ctx.fs().readdirWithFileTypes(fullPath).join();
                        List<DirentEntry> sorted = new ArrayList<>(entries);
                        sorted.sort(Comparator.comparing(DirentEntry::name));

                        if (!allFiles) {
                            sorted.removeIf(e -> e.name().startsWith("."));
                        }

                        for (DirentEntry entry : sorted) {
                            if (longFormat) {
                                String type = entry.isDirectory() ? "d" : "-";
                                String perms = entry.isDirectory() ? "rwxr-xr-x" : "rw-r--r--";
                                stdout.append(type)
                                    .append(perms)
                                    .append(" 1 user user ")
                                    .append(String.format("%5d", 0))
                                    .append(" ")
                                    .append(entry.name());
                                if (entry.isDirectory()) {
                                    stdout.append("/");
                                }
                                stdout.append("\n");
                            } else {
                                stdout.append(entry.name());
                                if (entry.isDirectory()) {
                                    stdout.append("/");
                                }
                                stdout.append("\n");
                            }
                        }
                    } else {
                        if (longFormat) {
                            stdout.append("-rw-r--r-- 1 user user ")
                                .append(String.format("%5d", stat.size()))
                                .append(" ")
                                .append(path)
                                .append("\n");
                        } else {
                            stdout.append(path).append("\n");
                        }
                    }
                } catch (Exception e) {
                    stderr.append("ls: cannot access '")
                        .append(path)
                        .append("': No such file or directory\n");
                    exitCode = 2;
                }
            }
            return new ExecResult(stdout.toString(), stderr.toString(), exitCode);
        });
    }
}
