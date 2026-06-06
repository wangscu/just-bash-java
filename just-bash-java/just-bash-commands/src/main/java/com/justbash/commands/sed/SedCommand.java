package com.justbash.commands.sed;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SedCommand implements Command {
    @Override
    public String name() { return "sed"; }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> scripts = new ArrayList<>();
            List<String> scriptFiles = new ArrayList<>();
            boolean silent = false;
            boolean inPlace = false;
            boolean extendedRegex = false;
            List<String> files = new ArrayList<>();

            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if (arg.equals("-n") || arg.equals("--quiet") || arg.equals("--silent")) {
                    silent = true;
                } else if (arg.equals("-i") || arg.equals("--in-place")) {
                    inPlace = true;
                } else if (arg.startsWith("-i")) {
                    inPlace = true;
                } else if (arg.equals("-E") || arg.equals("-r") || arg.equals("--regexp-extended")) {
                    extendedRegex = true;
                } else if (arg.equals("-e")) {
                    if (i + 1 < args.size()) scripts.add(args.get(++i));
                } else if (arg.equals("-f")) {
                    if (i + 1 < args.size()) scriptFiles.add(args.get(++i));
                } else if (arg.equals("--")) {
                    continue;
                } else if (arg.equals("-")) {
                    files.add(arg);
                } else if (arg.startsWith("-") && arg.length() > 1) {
                    for (char c : arg.substring(1).toCharArray()) {
                        if (c == 'n') silent = true;
                        else if (c == 'i') inPlace = true;
                        else if (c == 'E' || c == 'r') extendedRegex = true;
                        else return new ExecResult("", "sed: invalid option -- '" + c + "'\n", 1);
                    }
                } else if (!arg.startsWith("-") && scripts.isEmpty() && scriptFiles.isEmpty()) {
                    scripts.add(arg);
                } else if (!arg.startsWith("-")) {
                    files.add(arg);
                }
            }

            // Read scripts from -f files
            for (String scriptFile : scriptFiles) {
                String scriptPath = scriptFile.startsWith("/") ? scriptFile : ctx.cwd() + "/" + scriptFile;
                try {
                    String content = ctx.fs().readFile(scriptPath).join();
                    for (String line : content.split("\n")) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                            scripts.add(trimmed);
                        }
                    }
                } catch (Exception e) {
                    return new ExecResult("", "sed: couldn't open file " + scriptFile + ": No such file or directory\n", 1);
                }
            }

            if (scripts.isEmpty()) {
                return new ExecResult("", "sed: no script specified\n", 1);
            }

            // Join scripts
            String combinedScript = String.join("\n", scripts);
            SedParser parser = new SedParser(combinedScript, extendedRegex);
            SedTypes.ParseResult parseResult = parser.parse();

            if (parseResult.error != null) {
                return new ExecResult("", "sed: " + parseResult.error + "\n", 1);
            }

            boolean effectiveSilent = silent || parseResult.silentMode;

            if (inPlace) {
                if (files.isEmpty()) {
                    return new ExecResult("", "sed: -i requires at least one file argument\n", 1);
                }
                for (String file : files) {
                    if (file.equals("-")) continue;
                    String filePath = file.startsWith("/") ? file : ctx.cwd() + "/" + file;
                    try {
                        String content = ctx.fs().readFile(filePath).join();
                        String output = processContent(content, parseResult.commands, effectiveSilent);
                        ctx.fs().writeFile(filePath, new com.justbash.fs.IFileSystem.StringContent(output)).join();
                    } catch (Exception e) {
                        return new ExecResult("", "sed: " + file + ": No such file or directory\n", 1);
                    }
                }
                return new ExecResult("", "", 0);
            }

            String content;
            if (files.isEmpty()) {
                content = ctx.stdin().decodeUtf8();
            } else {
                StringBuilder sb = new StringBuilder();
                boolean stdinConsumed = false;
                for (String file : files) {
                    String fileContent;
                    if (file.equals("-")) {
                        if (stdinConsumed) {
                            fileContent = "";
                        } else {
                            fileContent = ctx.stdin().decodeUtf8();
                            stdinConsumed = true;
                        }
                    } else {
                        String filePath = file.startsWith("/") ? file : ctx.cwd() + "/" + file;
                        try {
                            fileContent = ctx.fs().readFile(filePath).join();
                        } catch (Exception e) {
                            return new ExecResult("", "sed: " + file + ": No such file or directory\n", 1);
                        }
                    }
                    if (sb.length() > 0 && fileContent.length() > 0 && !sb.toString().endsWith("\n")) {
                        sb.append("\n");
                    }
                    sb.append(fileContent);
                }
                content = sb.toString();
            }

            String output = processContent(content, parseResult.commands, effectiveSilent);
            return new ExecResult(output, "", 0);
        });
    }

    private String processContent(String content, List<SedTypes.SedCmd> commands, boolean silent) {
        boolean inputEndsWithNewline = content.endsWith("\n");
        String[] lines = content.split("\n", -1);
        List<String> lineList = new ArrayList<>();
        for (String line : lines) {
            if (!line.isEmpty() || lineList.size() < lines.length - 1) {
                lineList.add(line);
            }
        }
        // If content ends with newline, the last element after split is empty - skip it
        if (inputEndsWithNewline && !lineList.isEmpty() && lines[lines.length - 1].isEmpty()) {
            // Already handled by the loop above if we don't add empty last element
        }
        // Rebuild properly
        lineList.clear();
        for (int i = 0; i < lines.length; i++) {
            if (i < lines.length - 1 || !lines[i].isEmpty()) {
                lineList.add(lines[i]);
            }
        }

        StringBuilder output = new StringBuilder();
        String holdSpace = "";
        String lastPattern = null;

        for (int lineIndex = 0; lineIndex < lineList.size(); lineIndex++) {
            SedTypes.SedState state = SedExecutor.createInitialState(lineList.size());
            state.patternSpace = lineList.get(lineIndex);
            state.holdSpace = holdSpace;
            state.lastPattern = lastPattern;
            state.lineNumber = lineIndex + 1;

            String[] linesArray = lineList.toArray(new String[0]);
            SedExecutor.executeCommands(commands, state, linesArray, lineIndex);
            lineIndex += state.linesConsumedInCycle;

            holdSpace = state.holdSpace;
            lastPattern = state.lastPattern;

            // Output
            if (!silent) {
                for (String ln : state.nCommandOutput) {
                    output.append(ln).append("\n");
                }
            }
            for (String ln : state.lineNumberOutput) {
                output.append(ln).append("\n");
            }

            // Handle inserts
            List<String> inserts = new ArrayList<>();
            List<String> appends = new ArrayList<>();
            for (String item : state.appendBuffer) {
                if (item.startsWith("__INSERT__")) {
                    inserts.add(item.substring(10));
                } else {
                    appends.add(item);
                }
            }

            for (String text : inserts) {
                output.append(text).append("\n");
            }

            if (!state.deleted && !state.quitSilent && !silent) {
                output.append(state.patternSpace).append("\n");
            }

            for (String text : appends) {
                output.append(text).append("\n");
            }

            if (state.quit || state.quitSilent) {
                if (state.exitCode != null) {
                    // Early exit with code
                }
                if (state.errorMessage != null) {
                    return "";
                }
                break;
            }
        }

        // Strip trailing newline if input didn't end with one
        if (!inputEndsWithNewline && output.length() > 0 && output.charAt(output.length() - 1) == '\n') {
            output.setLength(output.length() - 1);
        }

        return output.toString();
    }
}
