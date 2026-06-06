package com.justbash.commands.tr;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class TrCommand implements Command {
    @Override
    public String name() {
        return "tr";
    }

    private static final Map<String, String> POSIX_CLASSES = Map.of(
        "[:alnum:]", "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789",
        "[:alpha:]", "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz",
        "[:blank:]", " \t",
        "[:digit:]", "0123456789",
        "[:lower:]", "abcdefghijklmnopqrstuvwxyz",
        "[:upper:]", "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
        "[:space:]", " \t\n\r\f",
        "[:xdigit:]", "0123456789ABCDEFabcdef"
    );

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            boolean complement = false;
            boolean delete = false;
            boolean squeeze = false;
            List<String> sets = new ArrayList<>();

            for (String arg : args) {
                if (arg.equals("-c") || arg.equals("-C") || arg.equals("--complement")) {
                    complement = true;
                } else if (arg.equals("-d") || arg.equals("--delete")) {
                    delete = true;
                } else if (arg.equals("-s") || arg.equals("--squeeze-repeats")) {
                    squeeze = true;
                } else if (arg.equals("--")) {
                    continue;
                } else if (arg.startsWith("-") && !arg.equals("-")) {
                    boolean unknown = false;
                    for (char c : arg.substring(1).toCharArray()) {
                        if (c == 'c' || c == 'C') complement = true;
                        else if (c == 'd') delete = true;
                        else if (c == 's') squeeze = true;
                        else unknown = true;
                    }
                    if (unknown) {
                        return new ExecResult("", "tr: invalid option -- '" + arg.substring(1) + "'\n", 1);
                    }
                } else {
                    sets.add(arg);
                }
            }

            if (sets.isEmpty()) {
                return new ExecResult("", "tr: missing operand\n", 1);
            }
            if (!delete && !squeeze && sets.size() < 2) {
                return new ExecResult("", "tr: missing operand after '" + sets.get(0) + "'\n", 1);
            }

            String set1, set2 = "";
            try {
                set1 = expandSet(sets.get(0));
                if (sets.size() > 1) set2 = expandSet(sets.get(1));
            } catch (Exception e) {
                return new ExecResult("", "tr: " + e.getMessage() + "\n", 1);
            }

            String content = ctx.stdin().decodeUtf8();
            StringBuilder output = new StringBuilder();

            if (delete) {
                for (int cp : content.codePoints().toArray()) {
                    String ch = new String(Character.toChars(cp));
                    boolean inSet = set1.contains(ch);
                    if (complement ? inSet : !inSet) {
                        output.append(ch);
                    }
                }
            } else if (squeeze && sets.size() == 1) {
                String prev = null;
                for (int cp : content.codePoints().toArray()) {
                    String ch = new String(Character.toChars(cp));
                    boolean inSet = set1.contains(ch);
                    boolean match = complement ? !inSet : inSet;
                    if (match && ch.equals(prev)) continue;
                    output.append(ch);
                    prev = ch;
                }
            } else {
                // Translate
                Map<String, String> transMap = new HashMap<>();
                for (int i = 0; i < set1.length(); ) {
                    String ch = set1.substring(i, i + Character.charCount(set1.codePointAt(i)));
                    String target = i < set2.length()
                        ? set2.substring(i, i + Character.charCount(set2.codePointAt(i)))
                        : set2.substring(set2.length() - Character.charCount(set2.codePointBefore(set2.length())));
                    transMap.put(ch, target);
                    i += ch.length();
                }

                for (int cp : content.codePoints().toArray()) {
                    String ch = new String(Character.toChars(cp));
                    if (complement) {
                        if (!set1.contains(ch)) {
                            String lastTarget = set2.isEmpty() ? "" : set2.substring(set2.length() - Character.charCount(set2.codePointBefore(set2.length())));
                            output.append(lastTarget);
                        } else {
                            output.append(ch);
                        }
                    } else {
                        output.append(transMap.getOrDefault(ch, ch));
                    }
                }

                if (squeeze) {
                    StringBuilder squeezed = new StringBuilder();
                    String prev = null;
                    for (int cp : output.toString().codePoints().toArray()) {
                        String ch = new String(Character.toChars(cp));
                        if (set2.contains(ch) && ch.equals(prev)) continue;
                        squeezed.append(ch);
                        prev = ch;
                    }
                    output = squeezed;
                }
            }

            return new ExecResult(output.toString(), "", 0);
        });
    }

    private String expandSet(String set) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < set.length()) {
            // POSIX classes
            if (set.charAt(i) == '[' && i + 1 < set.length() && set.charAt(i + 1) == ':') {
                boolean found = false;
                for (Map.Entry<String, String> entry : POSIX_CLASSES.entrySet()) {
                    if (set.startsWith(entry.getKey(), i)) {
                        result.append(entry.getValue());
                        i += entry.getKey().length();
                        found = true;
                        break;
                    }
                }
                if (found) continue;
            }

            // Escape sequences
            if (set.charAt(i) == '\\' && i + 1 < set.length()) {
                char next = set.charAt(i + 1);
                switch (next) {
                    case 'n' -> result.append('\n');
                    case 't' -> result.append('\t');
                    case 'r' -> result.append('\r');
                    default -> result.append(next);
                }
                i += 2;
                continue;
            }

            // Character ranges
            int cp = set.codePointAt(i);
            int charLen = Character.charCount(cp);
            if (i + charLen < set.length() && set.charAt(i + charLen) == '-' && i + charLen + 1 < set.length()) {
                int endCp = set.codePointAt(i + charLen + 1);
                int endLen = Character.charCount(endCp);
                if (endCp - cp > 65536) {
                    throw new RuntimeException("character range too large");
                }
                for (int c = cp; c <= endCp; c++) {
                    result.appendCodePoint(c);
                }
                i += charLen + 1 + endLen;
                continue;
            }

            result.appendCodePoint(cp);
            i += charLen;
        }
        return result.toString();
    }
}
