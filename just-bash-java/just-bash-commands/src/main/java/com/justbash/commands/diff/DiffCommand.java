package com.justbash.commands.diff;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DiffCommand implements Command {
    @Override
    public String name() {
        return "diff";
    }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> files = new ArrayList<>();
            for (String arg : args) {
                if (arg.startsWith("-")) {
                    continue; // ignore options for MVP
                } else {
                    files.add(arg);
                }
            }

            if (files.size() < 2) {
                return new ExecResult("", "diff: missing operand\n", 2);
            }

            String content1, content2;
            try {
                String path1 = files.get(0).startsWith("/") ? files.get(0) : ctx.cwd() + "/" + files.get(0);
                content1 = ctx.fs().readFile(path1).join();
            } catch (Exception e) {
                return new ExecResult("", "diff: " + files.get(0) + ": No such file or directory\n", 2);
            }
            try {
                String path2 = files.get(1).startsWith("/") ? files.get(1) : ctx.cwd() + "/" + files.get(1);
                content2 = ctx.fs().readFile(path2).join();
            } catch (Exception e) {
                return new ExecResult("", "diff: " + files.get(1) + ": No such file or directory\n", 2);
            }

            if (content1.equals(content2)) {
                return new ExecResult("", "", 0);
            }

            String[] lines1 = content1.split("\n", -1);
            String[] lines2 = content2.split("\n", -1);
            int end1 = lines1.length;
            int end2 = lines2.length;
            if (content1.endsWith("\n") && end1 > 0 && lines1[end1 - 1].isEmpty()) end1--;
            if (content2.endsWith("\n") && end2 > 0 && lines2[end2 - 1].isEmpty()) end2--;

            // Simple line-by-line diff
            StringBuilder out = new StringBuilder();
            int i = 0, j = 0;
            while (i < end1 || j < end2) {
                if (i < end1 && j < end2 && lines1[i].equals(lines2[j])) {
                    i++;
                    j++;
                } else {
                    int start1 = i, start2 = j;
                    while (i < end1 && (j >= end2 || !lines1[i].equals(lines2[j]))) i++;
                    while (j < end2 && (i >= end1 || !lines1[i].equals(lines2[j]))) j++;
                    if (start1 < i && start2 < j) {
                        out.append(String.format("%dc%d\n", start1 + 1, start2 + 1));
                        for (int k = start1; k < i; k++) out.append("< ").append(lines1[k]).append('\n');
                        out.append("---\n");
                        for (int k = start2; k < j; k++) out.append("> ").append(lines2[k]).append('\n');
                    } else if (start1 < i) {
                        out.append(String.format("%dd%d\n", start1 + 1, start2));
                        for (int k = start1; k < i; k++) out.append("< ").append(lines1[k]).append('\n');
                    } else {
                        out.append(String.format("%da%d\n", start1, start2 + 1));
                        for (int k = start2; k < j; k++) out.append("> ").append(lines2[k]).append('\n');
                    }
                }
            }

            return new ExecResult(out.toString(), "", 1);
        });
    }
}
