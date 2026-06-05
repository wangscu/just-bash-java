package com.justbash.commands.grep;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class GrepCommand implements Command {
    @Override
    public String name() {
        return "grep";
    }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            boolean ignoreCase = false;
            boolean invertMatch = false;
            boolean showLineNumbers = false;
            boolean countOnly = false;
            boolean listFilesOnly = false;
            boolean recursive = false;
            boolean fixedString = false;

            List<String> patterns = new ArrayList<>();
            List<String> files = new ArrayList<>();

            // Parse args
            boolean afterDash = false;
            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if (afterDash) {
                    if (patterns.isEmpty()) {
                        patterns.add(arg);
                    } else {
                        files.add(arg);
                    }
                } else if (arg.equals("--")) {
                    afterDash = true;
                } else if (arg.equals("-i")) {
                    ignoreCase = true;
                } else if (arg.equals("-v")) {
                    invertMatch = true;
                } else if (arg.equals("-n")) {
                    showLineNumbers = true;
                } else if (arg.equals("-c")) {
                    countOnly = true;
                } else if (arg.equals("-l")) {
                    listFilesOnly = true;
                } else if (arg.equals("-r") || arg.equals("-R")) {
                    recursive = true;
                } else if (arg.equals("-F")) {
                    fixedString = true;
                } else if (arg.equals("-E")) {
                    // Java regex is always extended
                } else if (arg.startsWith("-") && arg.length() > 1) {
                    // Combined flags like -ivn
                    for (char c : arg.substring(1).toCharArray()) {
                        switch (c) {
                            case 'i' -> ignoreCase = true;
                            case 'v' -> invertMatch = true;
                            case 'n' -> showLineNumbers = true;
                            case 'c' -> countOnly = true;
                            case 'l' -> listFilesOnly = true;
                            case 'r', 'R' -> recursive = true;
                            case 'F' -> fixedString = true;
                            case 'E' -> { /* no-op */ }
                        }
                    }
                } else {
                    if (patterns.isEmpty()) {
                        patterns.add(arg);
                    } else {
                        files.add(arg);
                    }
                }
            }

            if (patterns.isEmpty()) {
                return new ExecResult("", "grep: missing pattern\n", 2);
            }

            String pattern = patterns.get(0);
            int flags = ignoreCase ? Pattern.CASE_INSENSITIVE : 0;
            Pattern regex;
            try {
                if (fixedString) {
                    regex = Pattern.compile(Pattern.quote(pattern), flags);
                } else {
                    regex = Pattern.compile(pattern, flags);
                }
            } catch (PatternSyntaxException e) {
                return new ExecResult("", "grep: invalid pattern\n", 2);
            }

            if (files.isEmpty()) {
                // Read from stdin
                String stdin = ctx.stdin().decodeUtf8();
                return grepContent(stdin, "(standard input)", regex,
                    showLineNumbers, invertMatch, countOnly, listFilesOnly, false);
            }

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            int exitCode = 1; // 1 = no matches found, 0 = some matches
            boolean multiple = files.size() > 1 || recursive;

            for (String file : files) {
                try {
                    String path = ctx.fs().resolvePath(ctx.cwd(), file);
                    var stat = ctx.fs().stat(path).join();

                    if (stat.isDirectory() && !recursive) {
                        stderr.append("grep: ").append(file).append(": Is a directory\n");
                        continue;
                    }

                    if (stat.isDirectory() && recursive) {
                        // MVP: just read directory entries and grep each file
                        var entries = ctx.fs().readdirWithFileTypes(path).join();
                        for (var entry : entries) {
                            if (entry.isDirectory()) {
                                continue;
                            }
                            String filePath = ctx.fs().resolvePath(path, entry.name());
                            try {
                                String content = ctx.fs().readFile(filePath).join();
                                var result = grepContent(content, file + "/" + entry.name(), regex,
                                    showLineNumbers, invertMatch, countOnly, listFilesOnly, multiple);
                                if (result.exitCode() == 0) {
                                    exitCode = 0;
                                }
                                stdout.append(result.stdout());
                            } catch (Exception e) {
                                // Skip unreadable files
                            }
                        }
                    } else {
                        String content = ctx.fs().readFile(path).join();
                        var result = grepContent(content, file, regex,
                            showLineNumbers, invertMatch, countOnly, listFilesOnly, multiple);
                        if (result.exitCode() == 0) {
                            exitCode = 0;
                        }
                        stdout.append(result.stdout());
                    }
                } catch (Exception e) {
                    stderr.append("grep: ").append(file).append(": No such file or directory\n");
                }
            }

            return new ExecResult(stdout.toString(), stderr.toString(), exitCode);
        });
    }

    private ExecResult grepContent(String content, String filename, Pattern regex,
                                    boolean showLineNumbers, boolean invertMatch,
                                    boolean countOnly, boolean listFilesOnly,
                                    boolean showFilename) {
        String[] lines = content.split("\n", -1);
        StringBuilder out = new StringBuilder();
        int count = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            boolean matches = regex.matcher(line).find();
            if (invertMatch) {
                matches = !matches;
            }

            if (matches) {
                count++;
                if (listFilesOnly) {
                    out.append(filename).append("\n");
                    break; // Only print filename once
                }
                if (countOnly) {
                    continue;
                }

                if (showFilename) {
                    out.append(filename).append(":");
                }
                if (showLineNumbers) {
                    out.append(i + 1).append(":");
                }
                out.append(line).append("\n");
            }
        }

        if (countOnly) {
            if (showFilename) {
                out.append(filename).append(":");
            }
            out.append(count).append("\n");
        }

        return new ExecResult(out.toString(), "", count > 0 ? 0 : 1);
    }
}
