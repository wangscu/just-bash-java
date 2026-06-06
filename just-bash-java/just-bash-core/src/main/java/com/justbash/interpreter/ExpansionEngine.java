package com.justbash.interpreter;

import com.justbash.ExecResult;
import com.justbash.ast.ScriptNode;
import com.justbash.ast.operations.*;
import com.justbash.ast.word.*;
import com.justbash.fs.IFileSystem;
import com.justbash.fs.WriteFileOptions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class ExpansionEngine {

    private IFileSystem fs;

    public void setFileSystem(IFileSystem fs) {
        this.fs = fs;
    }

    @FunctionalInterface
    public interface ScriptExecutor {
        ExecResult execute(ScriptNode script);
    }

    /** Expand a WordNode into a list of strings (one per word after expansion) */
    public List<String> expandWord(WordNode word, InterpreterState state) {
        return expandWord(word, state, null);
    }

    /** Expand a WordNode with command substitution support */
    public List<String> expandWord(WordNode word, InterpreterState state,
                                    ScriptExecutor executor) {
        List<StringBuilder> builders = new ArrayList<>();
        builders.add(new StringBuilder());

        for (var part : word.parts()) {
            if (part instanceof ParameterExpansionPart pep) {
                handleParameterExpansion(pep, builders, state, executor);
            } else {
                String expanded = expandSinglePart(part, state, executor);
                for (StringBuilder sb : builders) {
                    sb.append(expanded);
                }
            }
        }

        List<String> result = new ArrayList<>();
        for (StringBuilder sb : builders) {
            result.add(sb.toString());
        }
        return result;
    }

    private void handleParameterExpansion(ParameterExpansionPart pep,
                                          List<StringBuilder> builders,
                                          InterpreterState state,
                                          ScriptExecutor executor) {
        String param = pep.parameter();

        // Special parameter $# - positional parameter count
        if (param.equals("#")) {
            String count = state.env.getOrDefault("#", "0");
            for (StringBuilder sb : builders) {
                sb.append(count);
            }
            return;
        }

        // Check for array length ${#arr[@]} or ${#arr[*]} (only when no operation)
        if (param.startsWith("#") && param.length() > 1 && pep.operation().isEmpty()) {
            String rest = param.substring(1);
            if (rest.endsWith("[@]") || rest.endsWith("[*]")) {
                String arrName = rest.substring(0, rest.indexOf('['));
                List<String> arr = state.indexedArrays.getOrDefault(arrName, List.of());
                for (StringBuilder sb : builders) {
                    sb.append(arr.size());
                }
                return;
            }
            // ${#arr[0]} - length of array element
            int bracketPos = rest.indexOf('[');
            if (bracketPos > 0 && rest.endsWith("]")) {
                String arrName = rest.substring(0, bracketPos);
                String indexStr = rest.substring(bracketPos + 1, rest.length() - 1);
                int idx = parseArrayIndex(indexStr, state);
                List<String> arr = state.indexedArrays.getOrDefault(arrName, List.of());
                String elem = (idx >= 0 && idx < arr.size()) ? arr.get(idx) : "";
                for (StringBuilder sb : builders) {
                    sb.append(elem.length());
                }
                return;
            }
            // ${#var} - string length
            String varName = rest;
            String value = state.env.getOrDefault(varName, "");
            List<String> arr = state.indexedArrays.get(varName);
            if (arr != null && !arr.isEmpty()) {
                value = arr.get(0);
            }
            for (StringBuilder sb : builders) {
                sb.append(value.length());
            }
            return;
        }

        // Check for array element access ${arr[index]} (only when no operation)
        int bracketPos = param.indexOf('[');
        if (bracketPos > 0 && param.endsWith("]") && pep.operation().isEmpty()) {
            String arrName = param.substring(0, bracketPos);
            String indexStr = param.substring(bracketPos + 1, param.length() - 1);

            // Associative array
            if (state.associativeArrays.contains(arrName)) {
                Map<String, String> arr = state.associativeArrayData.getOrDefault(arrName, Map.of());
                if (indexStr.equals("@")) {
                    if (!arr.isEmpty()) {
                        List<StringBuilder> newBuilders = new ArrayList<>();
                        for (StringBuilder sb : builders) {
                            String prefix = sb.toString();
                            for (String elem : arr.values()) {
                                newBuilders.add(new StringBuilder(prefix + elem));
                            }
                        }
                        builders.clear();
                        builders.addAll(newBuilders);
                    }
                    return;
                } else if (indexStr.equals("*")) {
                    String ifs = state.env.getOrDefault("IFS", " \t\n");
                    String joiner = ifs.isEmpty() ? " " : ifs.substring(0, 1);
                    String joined = String.join(joiner, arr.values());
                    for (StringBuilder sb : builders) {
                        sb.append(joined);
                    }
                    return;
                } else {
                    String value = arr.getOrDefault(indexStr, "");
                    for (StringBuilder sb : builders) {
                        sb.append(value);
                    }
                    return;
                }
            }

            // Indexed array
            List<String> arr = state.indexedArrays.getOrDefault(arrName, List.of());

            if (indexStr.equals("@")) {
                if (!arr.isEmpty()) {
                    List<StringBuilder> newBuilders = new ArrayList<>();
                    for (StringBuilder sb : builders) {
                        String prefix = sb.toString();
                        for (String elem : arr) {
                            newBuilders.add(new StringBuilder(prefix + elem));
                        }
                    }
                    builders.clear();
                    builders.addAll(newBuilders);
                }
                return;
            } else if (indexStr.equals("*")) {
                String ifs = state.env.getOrDefault("IFS", " \t\n");
                String joiner = ifs.isEmpty() ? " " : ifs.substring(0, 1);
                String joined = String.join(joiner, arr);
                for (StringBuilder sb : builders) {
                    sb.append(joined);
                }
                return;
            } else {
                int idx = parseArrayIndex(indexStr, state);
                String value = (idx >= 0 && idx < arr.size()) ? arr.get(idx) : "";
                for (StringBuilder sb : builders) {
                    sb.append(value);
                }
                return;
            }
        }

        // Resolve parameter value
        String value = resolveParameterValue(param, state);

        // Apply operation if present
        if (pep.operation().isPresent()) {
            ParameterOperation op = pep.operation().get();

            // ArrayKeysOp needs special handling (can expand builders like @)
            if (op instanceof ArrayKeysOp ako) {
                Map<String, String> arr = state.associativeArrayData.getOrDefault(ako.array(), Map.of());
                if (ako.star()) {
                    String ifs = state.env.getOrDefault("IFS", " \t\n");
                    String joiner = ifs.isEmpty() ? " " : ifs.substring(0, 1);
                    String joined = String.join(joiner, arr.keySet());
                    for (StringBuilder sb : builders) {
                        sb.append(joined);
                    }
                } else {
                    if (!arr.isEmpty()) {
                        List<StringBuilder> newBuilders = new ArrayList<>();
                        for (StringBuilder sb : builders) {
                            String prefix = sb.toString();
                            for (String key : arr.keySet()) {
                                newBuilders.add(new StringBuilder(prefix + key));
                            }
                        }
                        builders.clear();
                        builders.addAll(newBuilders);
                    }
                }
                return;
            }

            String result = switch (op) {
                case PatternRemovalOp pro -> applyPatternRemoval(value, pro, state, executor);
                case PatternReplacementOp pro -> applyPatternReplacement(value, pro, state, executor);
                case CaseModificationOp cmo -> applyCaseModification(value, cmo, state, executor);
                case SubstringOp so -> applySubstring(value, so, state);
                case DefaultValueOp dvo -> applyDefaultValue(param, value, dvo, state, executor);
                case AssignDefaultOp ado -> applyAssignDefault(param, value, ado, state, executor);
                case ErrorIfUnsetOp eiuo -> applyErrorIfUnset(param, value, eiuo, state, executor);
                case UseAlternativeOp uao -> applyUseAlternative(param, value, uao, state, executor);
                default -> value;
            };
            for (StringBuilder sb : builders) {
                sb.append(result);
            }
            return;
        }

        // No operation - regular parameter expansion
        for (StringBuilder sb : builders) {
            sb.append(value);
        }
    }

    private String resolveParameterValue(String param, InterpreterState state) {
        List<String> arr = state.indexedArrays.get(param);
        if (arr != null && !arr.isEmpty()) {
            return arr.get(0);
        }
        return state.env.getOrDefault(param, "");
    }

    private String applyPatternRemoval(String value, PatternRemovalOp op,
                                       InterpreterState state, ScriptExecutor executor) {
        String pattern = expandWord(op.pattern(), state, executor).get(0);
        if (pattern.isEmpty()) return value;

        if (op.side() == PatternRemovalOp.PatternSide.PREFIX) {
            if (op.greedy()) {
                // Longest prefix match
                for (int i = value.length(); i >= 0; i--) {
                    if (GlobExpander.globMatch(value.substring(0, i), pattern)) {
                        return value.substring(i);
                    }
                }
            } else {
                // Shortest prefix match
                for (int i = 0; i <= value.length(); i++) {
                    if (GlobExpander.globMatch(value.substring(0, i), pattern)) {
                        return value.substring(i);
                    }
                }
            }
        } else {
            if (op.greedy()) {
                // Longest suffix match
                for (int i = 0; i <= value.length(); i++) {
                    if (GlobExpander.globMatch(value.substring(i), pattern)) {
                        return value.substring(0, i);
                    }
                }
            } else {
                // Shortest suffix match
                for (int i = value.length(); i >= 0; i--) {
                    if (GlobExpander.globMatch(value.substring(i), pattern)) {
                        return value.substring(0, i);
                    }
                }
            }
        }
        return value;
    }

    private String applyPatternReplacement(String value, PatternReplacementOp op,
                                           InterpreterState state, ScriptExecutor executor) {
        String pattern = expandWord(op.pattern(), state, executor).get(0);
        String replacement = op.replacement().isPresent()
            ? expandWord(op.replacement().get(), state, executor).get(0) : "";

        if (pattern.isEmpty()) return value;

        String regex = globToRegex(pattern);
        if (op.anchor().isPresent()) {
            switch (op.anchor().get()) {
                case START -> regex = "^" + regex;
                case END -> regex = regex + "$";
            }
        }

        String quotedRepl = java.util.regex.Matcher.quoteReplacement(replacement);
        if (op.all()) {
            return value.replaceAll(regex, quotedRepl);
        } else {
            return value.replaceFirst(regex, quotedRepl);
        }
    }

    private String applyCaseModification(String value, CaseModificationOp op,
                                         InterpreterState state, ScriptExecutor executor) {
        Optional<String> optPattern = op.pattern().map(p -> expandWord(p, state, executor).get(0));
        String pattern = optPattern.orElse("?"); // default pattern matches first char of each word

        if (op.direction() == CaseModificationOp.Direction.UPPER) {
            if (op.all()) {
                return value.toUpperCase();
            } else {
                // Uppercase first character matching pattern
                if (value.isEmpty()) return value;
                return value.substring(0, 1).toUpperCase() + value.substring(1);
            }
        } else {
            if (op.all()) {
                return value.toLowerCase();
            } else {
                // Lowercase first character matching pattern
                if (value.isEmpty()) return value;
                return value.substring(0, 1).toLowerCase() + value.substring(1);
            }
        }
    }

    private String applySubstring(String value, SubstringOp op, InterpreterState state) {
        long offsetLong = ArithmeticEvaluator.evaluate(op.offset(), state);
        int offset = (int) offsetLong;

        // Negative offset counts from end
        int start;
        if (offset < 0) {
            start = Math.max(0, value.length() + offset);
        } else {
            start = offset;
        }

        if (start >= value.length()) return "";

        if (op.length().isPresent()) {
            long lenLong = ArithmeticEvaluator.evaluate(op.length().get(), state);
            int len = (int) lenLong;
            if (len < 0) return "";
            int end = Math.min(value.length(), start + len);
            return value.substring(start, end);
        } else {
            return value.substring(start);
        }
    }

    private String applyDefaultValue(String param, String value, DefaultValueOp op,
                                     InterpreterState state, ScriptExecutor executor) {
        boolean isUnset = !state.env.containsKey(param) && !state.indexedArrays.containsKey(param);
        boolean isEmpty = value.isEmpty();
        if (op.checkEmpty() ? (isUnset || isEmpty) : isUnset) {
            return expandWord(op.word(), state, executor).get(0);
        }
        return value;
    }

    private String applyAssignDefault(String param, String value, AssignDefaultOp op,
                                      InterpreterState state, ScriptExecutor executor) {
        boolean isUnset = !state.env.containsKey(param) && !state.indexedArrays.containsKey(param);
        boolean isEmpty = value.isEmpty();
        if (op.checkEmpty() ? (isUnset || isEmpty) : isUnset) {
            String defaultValue = expandWord(op.word(), state, executor).get(0);
            state.env.put(param, defaultValue);
            return defaultValue;
        }
        return value;
    }

    private String applyErrorIfUnset(String param, String value, ErrorIfUnsetOp op,
                                     InterpreterState state, ScriptExecutor executor) {
        boolean isUnset = !state.env.containsKey(param) && !state.indexedArrays.containsKey(param);
        boolean isEmpty = value.isEmpty();
        if (op.checkEmpty() ? (isUnset || isEmpty) : isUnset) {
            String msg = op.word().isPresent()
                ? expandWord(op.word().get(), state, executor).get(0)
                : "parameter null or not set";
            throw new com.justbash.interpreter.errors.ExecutionException(
                "bash: " + param + ": " + msg);
        }
        return value;
    }

    private String applyUseAlternative(String param, String value, UseAlternativeOp op,
                                       InterpreterState state, ScriptExecutor executor) {
        boolean isUnset = !state.env.containsKey(param) && !state.indexedArrays.containsKey(param);
        boolean isEmpty = value.isEmpty();
        if (op.checkEmpty() ? !(isUnset || isEmpty) : !isUnset) {
            return expandWord(op.word(), state, executor).get(0);
        }
        return "";
    }

    private static String globToRegex(String pattern) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append(".");
                case '[' -> {
                    int close = pattern.indexOf(']', i + 1);
                    if (close == -1) {
                        regex.append("\\[");
                    } else {
                        String content = pattern.substring(i + 1, close);
                        if (content.startsWith("!") || content.startsWith("^")) {
                            regex.append("[^").append(escapeForClass(content.substring(1))).append("]");
                        } else {
                            regex.append("[").append(escapeForClass(content)).append("]");
                        }
                        i = close;
                    }
                }
                case '\\' -> {
                    if (i + 1 < pattern.length()) {
                        regex.append("\\").append(pattern.charAt(i + 1));
                        i++;
                    }
                }
                default -> {
                    if ("\\.^$+{}|()".indexOf(c) >= 0) {
                        regex.append("\\");
                    }
                    regex.append(c);
                }
            }
        }
        return regex.toString();
    }

    private static String escapeForClass(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == ']' || c == '^' || c == '-' || c == '\\') {
                sb.append("\\");
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private int parseArrayIndex(String indexStr, InterpreterState state) {
        try {
            return Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            String resolved = state.env.getOrDefault(indexStr, "0");
            try {
                return Integer.parseInt(resolved);
            } catch (NumberFormatException e2) {
                return 0;
            }
        }
    }

    private String expandSinglePart(WordPart part, InterpreterState state,
                                    ScriptExecutor executor) {
        return switch (part) {
            case LiteralPart lp -> lp.value();
            case SingleQuotedPart sqp -> sqp.value();
            case DoubleQuotedPart dqp -> {
                StringBuilder sb = new StringBuilder();
                for (var inner : dqp.parts()) {
                    if (inner instanceof LiteralPart lip) sb.append(lip.value());
                }
                yield sb.toString();
            }
            case TildeExpansionPart tep ->
                state.env.getOrDefault("HOME", "/home/user");
            case ArithmeticExpansionPart aep -> {
                long result = ArithmeticEvaluator.evaluate(
                    aep.expression().expression(), state);
                yield String.valueOf(result);
            }
            case CommandSubstitutionPart csp -> {
                if (executor != null) {
                    ExecResult result = executor.execute(csp.body());
                    String output = stripTrailingNewlines(result.stdout());
                    state.lastExitCode = result.exitCode();
                    state.env.put("?", String.valueOf(result.exitCode()));
                    yield output;
                }
                yield "";
            }
            case EscapedPart ep -> ep.value();
            case GlobPart gp -> gp.pattern();
            case BraceExpansionPart bep -> {
                // Should not reach here - brace expansion is applied at word level
                yield "";
            }
            case ProcessSubstitutionPart psp -> {
                if (fs == null) yield "";
                String tempPath = "/tmp/ps_" + System.currentTimeMillis()
                    + "_" + (int)(Math.random() * 100000);
                if (psp.direction() == ProcessSubstitutionPart.Direction.INPUT) {
                    if (executor != null) {
                        ExecResult result = executor.execute(psp.body());
                        try {
                            fs.writeFile(tempPath,
                                new IFileSystem.StringContent(result.stdout()),
                                WriteFileOptions.utf8()).join();
                        } catch (Exception e) {
                            yield tempPath;
                        }
                    }
                    yield tempPath;
                } else {
                    // OUTPUT: create empty temp file, register for later execution
                    try {
                        fs.writeFile(tempPath,
                            new IFileSystem.StringContent(""),
                            WriteFileOptions.utf8()).join();
                    } catch (Exception e) {
                        // ignore
                    }
                    state.pendingOutputProcessSubs.add(
                        new InterpreterState.PendingProcessSub(tempPath, psp.body()));
                    yield tempPath;
                }
            }
            case ParameterExpansionPart pep -> {
                // Handled separately in handleParameterExpansion
                yield "";
            }
        };
    }

    /** Expand a list of WordNodes into a flat list of strings */
    public List<String> expandWords(List<WordNode> words, InterpreterState state) {
        return expandWords(words, state, null);
    }

    public List<String> expandWords(List<WordNode> words, InterpreterState state,
                                    ScriptExecutor executor) {
        List<String> result = new ArrayList<>();
        for (var word : words) {
            result.addAll(expandWord(word, state, executor));
        }
        return result;
    }

    /** Apply brace expansion to each string in the list */
    public List<String> expandBraces(List<String> inputs) {
        List<String> result = new ArrayList<>();
        for (String s : inputs) {
            result.addAll(BraceExpander.expand(s));
        }
        return result;
    }

    /** Apply glob expansion to each string in the list */
    public List<String> expandGlobs(List<String> inputs, IFileSystem fs,
                                    InterpreterState state) {
        List<String> result = new ArrayList<>();
        for (String s : inputs) {
            List<String> matches = GlobExpander.expand(s, fs, state);
            if (matches.isEmpty()) {
                if (state.shoptOptions.nullglob) {
                    // nullglob: no matches → remove the word
                    continue;
                }
                // No matches and not nullglob → keep literal pattern
                result.add(s);
            } else {
                result.addAll(matches);
            }
        }
        return result;
    }

    private static String stripTrailingNewlines(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '\n') {
            end--;
        }
        return s.substring(0, end);
    }
}
