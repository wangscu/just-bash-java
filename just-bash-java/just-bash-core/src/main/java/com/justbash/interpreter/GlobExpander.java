package com.justbash.interpreter;

import com.justbash.fs.IFileSystem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GlobExpander {

    /** Expand glob patterns in a string against the filesystem. */
    public static List<String> expand(String pattern, IFileSystem fs,
                                      InterpreterState state) {
        if (!hasGlobChars(pattern)) {
            return List.of(pattern);
        }

        // Determine directory and filename pattern
        String dir;
        String filePattern;
        int lastSlash = pattern.lastIndexOf('/');
        if (lastSlash >= 0) {
            dir = pattern.substring(0, lastSlash);
            if (dir.isEmpty()) dir = "/";
            filePattern = pattern.substring(lastSlash + 1);
        } else {
            dir = state.cwd;
            filePattern = pattern;
        }

        try {
            List<String> entries = fs.readdir(dir).join();
            List<String> matches = new ArrayList<>();
            for (String entry : entries) {
                if (entry.equals(".") || entry.equals("..")) continue;
                if (!state.shoptOptions.dotglob && entry.startsWith(".") &&
                    !filePattern.startsWith(".")) {
                    continue;
                }
                if (globMatch(entry, filePattern)) {
                    if (lastSlash >= 0) {
                        matches.add(dir + "/" + entry);
                    } else {
                        matches.add(entry);
                    }
                }
            }

            Collections.sort(matches);
            return matches;
        } catch (Exception e) {
            System.err.println("[GLOB DEBUG] exception: " + e.getMessage());
            e.printStackTrace();
            return List.of(pattern);
        }
    }

    private static boolean hasGlobChars(String s) {
        boolean inBracket = false;
        boolean hasBracket = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '[') {
                inBracket = true;
                hasBracket = true;
            } else if (c == ']') {
                inBracket = false;
            } else if (!inBracket && (c == '*' || c == '?')) {
                return true;
            }
        }
        return hasBracket || inBracket;
    }

    private static boolean globMatch(String text, String pattern) {
        StringBuilder regex = new StringBuilder();
        boolean inBracket = false;

        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\\') {
                if (i + 1 < pattern.length()) {
                    regex.append("\\").append(pattern.charAt(i + 1));
                    i++;
                }
            } else if (c == '*') {
                regex.append(".*");
            } else if (c == '?') {
                regex.append(".");
            } else if (c == '[') {
                int close = pattern.indexOf(']', i + 1);
                if (close == -1) {
                    regex.append("\\[");
                } else {
                    String content = pattern.substring(i + 1, close);
                    if (content.startsWith("!") || content.startsWith("^")) {
                        regex.append("[^").append(escapeForCharacterClass(content.substring(1))).append("]");
                    } else {
                        regex.append("[").append(escapeForCharacterClass(content)).append("]");
                    }
                    i = close;
                }
            } else {
                if ("\\.^$+{}|()".indexOf(c) >= 0) {
                    regex.append("\\");
                }
                regex.append(c);
            }
        }

        return text.matches(regex.toString());
    }

    private static String escapeForCharacterClass(String s) {
        // In character classes, only ] ^ - \ need escaping
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == ']' || c == '^' || c == '-' || c == '\\') {
                sb.append("\\");
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
