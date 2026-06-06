package com.justbash.commands.file;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FileCommand implements Command {
    @Override
    public String name() {
        return "file";
    }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> files = new ArrayList<>();
            for (String arg : args) {
                if (arg.startsWith("-")) {
                    continue;
                } else {
                    files.add(arg);
                }
            }

            if (files.isEmpty()) {
                return new ExecResult("", "file: missing operand\n", 1);
            }

            StringBuilder out = new StringBuilder();
            for (String file : files) {
                try {
                    String path = file.startsWith("/") ? file : ctx.cwd() + "/" + file;
                    var stat = ctx.fs().stat(path).join();
                    if (stat.isDirectory()) {
                        out.append(file).append(": directory\n");
                    } else if (stat.isSymbolicLink()) {
                        out.append(file).append(": symbolic link\n");
                    } else {
                        String content = ctx.fs().readFile(path).join();
                        String type = guessType(content);
                        out.append(file).append(": ").append(type).append('\n');
                    }
                } catch (Exception e) {
                    out.append(file).append(": cannot open (No such file or directory)\n");
                }
            }

            return new ExecResult(out.toString(), "", 0);
        });
    }

    private String guessType(String content) {
        if (content.isEmpty()) return "empty";
        // Check for ASCII text vs binary
        boolean hasBinary = false;
        boolean hasText = false;
        for (int i = 0; i < Math.min(content.length(), 1024); i++) {
            char c = content.charAt(i);
            if (c == '\0') {
                hasBinary = true;
                break;
            }
            if (c >= 32 && c < 127 || c == '\n' || c == '\r' || c == '\t') {
                hasText = true;
            }
        }
        if (hasBinary) return "data";

        // Heuristics for specific types
        String trimmed = content.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return "JSON data";
        if (trimmed.startsWith("<?xml") || trimmed.startsWith("<!DOCTYPE")) return "XML document text";
        if (trimmed.startsWith("#!/bin/bash") || trimmed.startsWith("#!/bin/sh") || trimmed.startsWith("#!/usr/bin/env bash")) return "Bourne-Again shell script, ASCII text executable";
        if (trimmed.startsWith("#!/usr/bin/python") || trimmed.startsWith("#!/usr/bin/env python")) return "Python script, ASCII text executable";
        if (trimmed.startsWith("#") || trimmed.startsWith("//")) return "ASCII text";
        if (trimmed.startsWith("<!DOCTYPE html") || trimmed.contains("<html")) return "HTML document, ASCII text";

        return hasText ? "ASCII text" : "data";
    }
}
