package com.justbash.commands.awk;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AwkCommand implements Command {
    @Override
    public String name() { return "awk"; }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            String fieldSepStr = " ";
            java.util.Map<String, String> vars = new java.util.HashMap<>();
            int programIdx = 0;

            List<String> scriptFiles = new ArrayList<>();

            // Parse options
            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if (arg.equals("-F") && i + 1 < args.size()) {
                    fieldSepStr = processEscapes(args.get(++i));
                    programIdx = i + 1;
                } else if (arg.startsWith("-F")) {
                    fieldSepStr = processEscapes(arg.substring(2));
                    programIdx = i + 1;
                } else if (arg.equals("-v") && i + 1 < args.size()) {
                    String assignment = args.get(++i);
                    int eqIdx = assignment.indexOf('=');
                    if (eqIdx > 0) {
                        String varName = assignment.substring(0, eqIdx);
                        String varValue = processEscapes(assignment.substring(eqIdx + 1));
                        vars.put(varName, varValue);
                    }
                    programIdx = i + 1;
                } else if (arg.equals("-f") && i + 1 < args.size()) {
                    scriptFiles.add(args.get(++i));
                    programIdx = i + 1;
                } else if (arg.startsWith("-") && arg.length() > 1) {
                    char optChar = arg.charAt(1);
                    if (optChar != 'F' && optChar != 'v' && optChar != 'f') {
                        return new ExecResult("", "awk: invalid option -- '" + optChar + "'\n", 1);
                    }
                    programIdx = i + 1;
                } else if (!arg.startsWith("-")) {
                    programIdx = i;
                    break;
                }
            }

            String program = "";
            if (!scriptFiles.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (String scriptFile : scriptFiles) {
                    String scriptPath = scriptFile.startsWith("/") ? scriptFile : ctx.cwd() + "/" + scriptFile;
                    try {
                        String content = ctx.fs().readFile(scriptPath).join();
                        if (sb.length() > 0) sb.append("\n");
                        sb.append(content);
                    } catch (Exception e) {
                        return new ExecResult("", "awk: cannot open " + scriptFile + " for reading\n", 1);
                    }
                }
                program = sb.toString();
            } else if (programIdx < args.size()) {
                program = args.get(programIdx);
            } else {
                return new ExecResult("", "awk: missing program\n", 1);
            }

            List<String> files = new ArrayList<>();
            int fileStartIdx = programIdx;
            if (!scriptFiles.isEmpty()) {
                fileStartIdx = programIdx;
            } else {
                fileStartIdx = programIdx + 1;
            }
            for (int i = fileStartIdx; i < args.size(); i++) {
                files.add(args.get(i));
            }

            if (program.isEmpty()) {
                return new ExecResult("", "awk: missing program\n", 1);
            }

            // Parse program
            AwkParser parser = new AwkParser();
            AwkTypes.AwkProgram ast;
            try {
                ast = parser.parse(program);
            } catch (Exception e) {
                return new ExecResult("", "awk: " + e.getMessage() + "\n", 1);
            }

            // Create runtime context
            AwkTypes.RuntimeContext runtimeCtx = new AwkTypes.RuntimeContext();
            runtimeCtx.FS = fieldSepStr;
            runtimeCtx.vars.putAll(vars);

            // Set up ARGC/ARGV
            runtimeCtx.vars.put("ARGC", String.valueOf(files.size() + 1));
            runtimeCtx.vars.put("ARGV_0", "awk");
            for (int i = 0; i < files.size(); i++) {
                runtimeCtx.vars.put("ARGV_" + (i + 1), files.get(i));
            }

            // Create interpreter
            AwkInterpreter interp = new AwkInterpreter(runtimeCtx, ctx);
            interp.execute(ast);

            boolean hasMainRules = false;
            boolean hasEndBlocks = false;
            for (AwkTypes.AwkRule rule : ast.rules) {
                if (rule.pattern == null ||
                    (rule.pattern.type() != AwkTypes.PatternType.BEGIN &&
                     rule.pattern.type() != AwkTypes.PatternType.END)) {
                    hasMainRules = true;
                }
                if (rule.pattern != null && rule.pattern.type() == AwkTypes.PatternType.END) {
                    hasEndBlocks = true;
                }
            }

            try {
                interp.executeBegin();
                if (runtimeCtx.shouldExit) {
                    interp.executeEnd();
                    return new ExecResult(interp.getOutput(), "", interp.getExitCode());
                }

                if (!hasMainRules && !hasEndBlocks) {
                    return new ExecResult(interp.getOutput(), "", interp.getExitCode());
                }

                if (!files.isEmpty()) {
                    for (String file : files) {
                        if (runtimeCtx.shouldExit || runtimeCtx.shouldNextFile) break;
                        String filePath = file.startsWith("/") ? file : ctx.cwd() + "/" + file;
                        try {
                            String content = ctx.fs().readFile(filePath).join();
                            String[] lines = content.split("\n", -1);
                            List<String> lineList = new ArrayList<>();
                            for (String line : lines) {
                                if (!line.isEmpty() || lineList.size() < lines.length - 1) {
                                    lineList.add(line);
                                }
                            }
                            runtimeCtx.FILENAME = file;
                            runtimeCtx.FNR = 0;
                            runtimeCtx.lines = lineList;
                            runtimeCtx.lineIndex = -1;
                            runtimeCtx.shouldNextFile = false;

                            while (runtimeCtx.lineIndex < lineList.size() - 1) {
                                if (runtimeCtx.shouldExit || runtimeCtx.shouldNextFile) break;
                                runtimeCtx.lineIndex++;
                                interp.executeLine(lineList.get(runtimeCtx.lineIndex));
                            }
                        } catch (Exception e) {
                            return new ExecResult("", "awk: " + file + ": No such file or directory\n", 1);
                        }
                    }
                } else {
                    // Read from stdin
                    String content = ctx.stdin().decodeUtf8();
                    String[] lines = content.split("\n", -1);
                    List<String> lineList = new ArrayList<>();
                    for (String line : lines) {
                        if (!line.isEmpty() || lineList.size() < lines.length - 1) {
                            lineList.add(line);
                        }
                    }
                    runtimeCtx.FILENAME = "";
                    runtimeCtx.FNR = 0;
                    runtimeCtx.lines = lineList;
                    runtimeCtx.lineIndex = -1;

                    while (runtimeCtx.lineIndex < lineList.size() - 1) {
                        if (runtimeCtx.shouldExit) break;
                        runtimeCtx.lineIndex++;
                        interp.executeLine(lineList.get(runtimeCtx.lineIndex));
                    }
                }

                interp.executeEnd();
                return new ExecResult(interp.getOutput(), "", interp.getExitCode());
            } catch (Exception e) {
                return new ExecResult(interp.getOutput(), "awk: " + e.getMessage() + "\n", 2);
            }
        });
    }

    private static String processEscapes(String str) {
        return str.replace("\\t", "\t")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\\\", "\\");
    }
}
