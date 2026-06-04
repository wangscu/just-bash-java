package com.justbash.interpreter;

import com.justbash.ast.word.*;
import java.util.ArrayList;
import java.util.List;

public class ExpansionEngine {

    /** Expand a WordNode into a list of strings (one per word after expansion) */
    public List<String> expandWord(WordNode word, InterpreterState state) {
        StringBuilder sb = new StringBuilder();
        for (var part : word.parts()) {
            if (part instanceof LiteralPart lp) {
                sb.append(lp.value());
            } else if (part instanceof SingleQuotedPart sqp) {
                sb.append(sqp.value());
            } else if (part instanceof DoubleQuotedPart dqp) {
                // For MVP: treat double-quoted as literal (no parameter expansion yet)
                for (var inner : dqp.parts()) {
                    if (inner instanceof LiteralPart lp) sb.append(lp.value());
                }
            } else if (part instanceof ParameterExpansionPart pep) {
                // Basic parameter expansion: $VAR or ${VAR}
                String value = state.env.getOrDefault(pep.parameter(), "");
                sb.append(value);
            } else if (part instanceof TildeExpansionPart tep) {
                sb.append(state.env.getOrDefault("HOME", "/home/user"));
            }
            // Ignore other part types for MVP (command substitution, arithmetic, etc.)
        }
        return List.of(sb.toString());
    }

    /** Expand a list of WordNodes into a flat list of strings */
    public List<String> expandWords(List<WordNode> words, InterpreterState state) {
        List<String> result = new ArrayList<>();
        for (var word : words) {
            result.addAll(expandWord(word, state));
        }
        return result;
    }
}
