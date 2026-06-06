package com.justbash.commands.uniq;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class UniqCommand implements Command {
    @Override
    public String name() {
        return "uniq";
    }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            boolean count = false;
            boolean repeatedOnly = false;
            boolean uniqueOnly = false;
            boolean ignoreCase = false;
            List<String> files = new ArrayList<>();

            for (String arg : args) {
                if (arg.equals("-c") || arg.equals("--count")) {
                    count = true;
                } else if (arg.equals("-d") || arg.equals("--repeated")) {
                    repeatedOnly = true;
                } else if (arg.equals("-u") || arg.equals("--unique")) {
                    uniqueOnly = true;
                } else if (arg.equals("-i") || arg.equals("--ignore-case")) {
                    ignoreCase = true;
                } else if (arg.equals("--")) {
                    continue;
                } else if (arg.startsWith("-")) {
                    boolean unknown = false;
                    for (char c : arg.substring(1).toCharArray()) {
                        if (c == 'c') count = true;
                        else if (c == 'd') repeatedOnly = true;
                        else if (c == 'u') uniqueOnly = true;
                        else if (c == 'i') ignoreCase = true;
                        else unknown = true;
                    }
                    if (unknown) {
                        return new ExecResult("", "uniq: invalid option -- '" + arg.substring(1) + "'\n", 1);
                    }
                } else {
                    files.add(arg);
                }
            }

            String content;
            if (files.isEmpty()) {
                content = ctx.stdin().decodeUtf8();
            } else {
                try {
                    String path = files.get(0).startsWith("/") ? files.get(0) : ctx.cwd() + "/" + files.get(0);
                    content = ctx.fs().readFile(path).join();
                } catch (Exception e) {
                    return new ExecResult("", "uniq: " + files.get(0) + ": No such file or directory\n", 1);
                }
            }

            String[] rawLines = content.split("\n", -1);
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < rawLines.length; i++) {
                if (i == rawLines.length - 1 && rawLines[i].isEmpty() && content.endsWith("\n")) {
                    continue;
                }
                lines.add(rawLines[i]);
            }

            if (lines.isEmpty()) {
                return new ExecResult("", "", 0);
            }

            record LineGroup(String line, int count) {}
            List<LineGroup> groups = new ArrayList<>();
            String currentLine = lines.get(0);
            int currentCount = 1;

            for (int i = 1; i < lines.size(); i++) {
                if (compareLines(currentLine, lines.get(i), ignoreCase)) {
                    currentCount++;
                } else {
                    groups.add(new LineGroup(currentLine, currentCount));
                    currentLine = lines.get(i);
                    currentCount = 1;
                }
            }
            groups.add(new LineGroup(currentLine, currentCount));

            StringBuilder output = new StringBuilder();
            for (LineGroup group : groups) {
                if (repeatedOnly && group.count() <= 1) continue;
                if (uniqueOnly && group.count() > 1) continue;
                if (count) {
                    output.append(String.format("%7d %s\n", group.count(), group.line()));
                } else {
                    output.append(group.line()).append('\n');
                }
            }

            return new ExecResult(output.toString(), "", 0);
        });
    }

    private boolean compareLines(String a, String b, boolean ignoreCase) {
        return ignoreCase ? a.equalsIgnoreCase(b) : a.equals(b);
    }
}
