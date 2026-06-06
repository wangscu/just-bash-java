package com.justbash.commands.rg;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class RgCommand implements Command {
    @Override
    public String name() {
        return "rg";
    }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            boolean ignoreCase = false;
            boolean invertMatch = false;
            boolean showLineNumbers = true;
            boolean countOnly = false;
            boolean listFilesOnly = false;
            boolean fixedString = false;
            boolean noHeading = false;
            String pattern = null;
            List<String> paths = new ArrayList<>();

            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if (arg.equals("-i")) {
                    ignoreCase = true;
                } else if (arg.equals("-v")) {
                    invertMatch = true;
                } else if (arg.equals("-n")) {
                    showLineNumbers = true;
                } else if (arg.equals("-N")) {
                    showLineNumbers = false;
                } else if (arg.equals("-c")) {
                    countOnly = true;
                } else if (arg.equals("-l")) {
                    listFilesOnly = true;
                } else if (arg.equals("-F")) {
                    fixedString = true;
                } else if (arg.equals("-f")) {
                    // not implemented in MVP
                    i++;
                } else if (arg.equals("--no-heading")) {
                    noHeading = true;
                } else if (arg.startsWith("-") && arg.length() > 1) {
                    boolean unknown = false;
                    for (char c : arg.substring(1).toCharArray()) {
                        switch (c) {
                            case 'i' -> ignoreCase = true;
                            case 'v' -> invertMatch = true;
                            case 'n' -> showLineNumbers = true;
                            case 'N' -> showLineNumbers = false;
                            case 'c' -> countOnly = true;
                            case 'l' -> listFilesOnly = true;
                            case 'F' -> fixedString = true;
                            default -> unknown = true;
                        }
                    }
                    if (unknown && pattern == null) {
                        // might be a negative number or something; treat as pattern
                        pattern = arg;
                    }
                } else {
                    if (pattern == null) {
                        pattern = arg;
                    } else {
                        paths.add(arg);
                    }
                }
            }

            if (pattern == null) {
                return new ExecResult("", "rg: missing pattern\n", 1);
            }

            int flags = ignoreCase ? Pattern.CASE_INSENSITIVE : 0;
            Pattern regex;
            try {
                if (fixedString) {
                    regex = Pattern.compile(Pattern.quote(pattern), flags);
                } else {
                    regex = Pattern.compile(pattern, flags);
                }
            } catch (PatternSyntaxException e) {
                return new ExecResult("", "rg: invalid pattern\n", 1);
            }

            if (paths.isEmpty()) {
                paths.add(ctx.cwd());
            }

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            int exitCode = 1;
            boolean multiplePaths = paths.size() > 1;

            for (String pathStr : paths) {
                try {
                    String path = pathStr.startsWith("/") ? pathStr : ctx.cwd() + "/" + pathStr;
                    var stat = ctx.fs().stat(path).join();
                    if (stat.isDirectory()) {
                        var result = searchDir(ctx, path, "", regex, showLineNumbers, invertMatch,
                            countOnly, listFilesOnly, multiplePaths || noHeading);
                        if (result.exitCode() == 0) exitCode = 0;
                        stdout.append(result.stdout());
                        stderr.append(result.stderr());
                    } else {
                        String content = ctx.fs().readFile(path).join();
                        var result = searchContent(content, pathStr, regex, showLineNumbers,
                            invertMatch, countOnly, listFilesOnly, multiplePaths || noHeading);
                        if (result.exitCode() == 0) exitCode = 0;
                        stdout.append(result.stdout());
                    }
                } catch (Exception e) {
                    stderr.append("rg: ").append(pathStr).append(": No such file or directory\n");
                }
            }

            return new ExecResult(stdout.toString(), stderr.toString(), exitCode);
        });
    }

    private ExecResult searchDir(CommandContext ctx, String dir, String relPath, Pattern regex,
                                  boolean showLineNumbers, boolean invertMatch, boolean countOnly,
                                  boolean listFilesOnly, boolean showFilename) throws Exception {
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        int exitCode = 1;

        var entries = ctx.fs().readdirWithFileTypes(dir).join();
        for (var entry : entries) {
            String name = entry.name();
            if (name.startsWith(".")) continue;
            String filePath = ctx.fs().resolvePath(dir, name);
            String displayPath = relPath.isEmpty() ? name : relPath + "/" + name;
            try {
                var stat = ctx.fs().stat(filePath).join();
                if (stat.isDirectory()) {
                    var result = searchDir(ctx, filePath, displayPath, regex, showLineNumbers,
                        invertMatch, countOnly, listFilesOnly, showFilename);
                    if (result.exitCode() == 0) exitCode = 0;
                    stdout.append(result.stdout());
                    stderr.append(result.stderr());
                } else {
                    String content = ctx.fs().readFile(filePath).join();
                    var result = searchContent(content, displayPath, regex, showLineNumbers,
                        invertMatch, countOnly, listFilesOnly, showFilename);
                    if (result.exitCode() == 0) exitCode = 0;
                    stdout.append(result.stdout());
                }
            } catch (Exception e) {
                // skip unreadable files
            }
        }
        return new ExecResult(stdout.toString(), stderr.toString(), exitCode);
    }

    private ExecResult searchContent(String content, String filename, Pattern regex,
                                      boolean showLineNumbers, boolean invertMatch,
                                      boolean countOnly, boolean listFilesOnly,
                                      boolean showFilename) {
        String[] lines = content.split("\n", -1);
        StringBuilder out = new StringBuilder();
        int count = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            boolean matches = regex.matcher(line).find();
            if (invertMatch) matches = !matches;

            if (matches) {
                count++;
                if (listFilesOnly) {
                    out.append(filename).append('\n');
                    break;
                }
                if (countOnly) continue;

                if (showFilename) {
                    out.append(filename).append(":");
                }
                if (showLineNumbers) {
                    out.append(i + 1).append(":");
                }
                out.append(line).append('\n');
            }
        }

        if (countOnly) {
            if (showFilename) {
                out.append(filename).append(":");
            }
            out.append(count).append('\n');
        }

        return new ExecResult(out.toString(), "", count > 0 ? 0 : 1);
    }
}
