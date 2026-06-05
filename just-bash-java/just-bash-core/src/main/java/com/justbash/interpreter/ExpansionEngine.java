package com.justbash.interpreter;

import com.justbash.ExecResult;
import com.justbash.ast.ScriptNode;
import com.justbash.ast.word.*;
import com.justbash.fs.IFileSystem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class ExpansionEngine {

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

        // Check for array length ${#arr[@]} or ${#arr[*]}
        if (param.startsWith("#") && param.length() > 1) {
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

        // Check for array element access ${arr[index]}
        int bracketPos = param.indexOf('[');
        if (bracketPos > 0 && param.endsWith("]")) {
            String arrName = param.substring(0, bracketPos);
            String indexStr = param.substring(bracketPos + 1, param.length() - 1);
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
                // If empty array, contributes zero words (builders unchanged but will be filtered)
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

        // Regular parameter or array first element
        List<String> arr = state.indexedArrays.get(param);
        String value;
        if (arr != null && !arr.isEmpty()) {
            value = arr.get(0);
        } else {
            value = state.env.getOrDefault(param, "");
        }
        for (StringBuilder sb : builders) {
            sb.append(value);
        }
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
            case ProcessSubstitutionPart psp -> "";
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
