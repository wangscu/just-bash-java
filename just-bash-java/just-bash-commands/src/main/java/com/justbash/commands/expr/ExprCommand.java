package com.justbash.commands.expr;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExprCommand implements Command {
    @Override
    public String name() {
        return "expr";
    }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            if (args.isEmpty()) {
                return new ExecResult("", "expr: missing operand\n", 2);
            }

            StringBuilder expr = new StringBuilder();
            for (String arg : args) {
                if (expr.length() > 0) expr.append(' ');
                expr.append(arg);
            }

            try {
                String result = evaluate(expr.toString().trim());
                return new ExecResult(result + "\n", "", result.equals("0") || result.isEmpty() ? 1 : 0);
            } catch (Exception e) {
                return new ExecResult("", "expr: " + e.getMessage() + "\n", 2);
            }
        });
    }

    private String evaluate(String expr) {
        // Handle regex match: expr : regex
        int colonIdx = -1;
        int depth = 0;
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ':' && depth == 0) {
                colonIdx = i;
                break;
            }
        }

        if (colonIdx >= 0) {
            String left = expr.substring(0, colonIdx).trim();
            String right = expr.substring(colonIdx + 1).trim();
            String val = evaluate(left);
            // Convert basic regex to Java regex
            String regex = right.replace("\\(", "(").replace("\\)", ")")
                .replace("\\{", "{").replace("\\}", "}")
                .replace("\\.", ".").replace("\\*", "*")
                .replace("\\+", "+").replace("\\?", "?")
                .replace("\\[", "[").replace("\\]", "]")
                .replace("\\^", "^").replace("\\$", "$");
            // Basic BRE anchoring
            if (!regex.startsWith("^")) regex = "^" + regex;
            Matcher m = Pattern.compile(regex).matcher(val);
            if (m.find()) {
                if (m.groupCount() > 0) {
                    return m.group(1);
                }
                return String.valueOf(m.group().length());
            }
            return "0";
        }

        // Handle match: match string regex
        if (expr.startsWith("match ")) {
            String[] parts = splitArgs(expr.substring(6).trim());
            if (parts.length < 2) return "0";
            String val = evaluate(parts[0]);
            String regex = parts[1].replace("\\(", "(").replace("\\)", ")")
                .replace("\\.", ".").replace("\\*", "*");
            if (!regex.startsWith("^")) regex = "^" + regex;
            Matcher m = Pattern.compile(regex).matcher(val);
            if (m.find()) {
                if (m.groupCount() > 0) return m.group(1);
                return String.valueOf(m.group().length());
            }
            return "0";
        }

        // Handle index: index string chars
        if (expr.startsWith("index ")) {
            String[] parts = splitArgs(expr.substring(6).trim());
            if (parts.length < 2) return "0";
            String val = evaluate(parts[0]);
            String chars = evaluate(parts[1]);
            for (int i = 0; i < val.length(); i++) {
                if (chars.indexOf(val.charAt(i)) >= 0) {
                    return String.valueOf(i + 1);
                }
            }
            return "0";
        }

        // Handle length: length string
        if (expr.startsWith("length ")) {
            String val = evaluate(expr.substring(7).trim());
            return String.valueOf(val.length());
        }

        // Handle substr: substr string pos length
        if (expr.startsWith("substr ")) {
            String[] parts = splitArgs(expr.substring(7).trim());
            if (parts.length < 3) return "";
            String val = evaluate(parts[0]);
            int pos = Integer.parseInt(evaluate(parts[1])) - 1;
            int len = Integer.parseInt(evaluate(parts[2]));
            if (pos < 0) pos = 0;
            if (pos >= val.length()) return "";
            int end = Math.min(pos + len, val.length());
            return val.substring(pos, end);
        }

        // Arithmetic: handle + - * / %
        return evalArithmetic(expr);
    }

    private String evalArithmetic(String expr) {
        // Tokenize
        List<String> tokens = tokenize(expr);
        if (tokens.isEmpty()) return "0";

        // Simple left-to-right for MVP (no proper precedence)
        // But handle parentheses
        return evalTokens(tokens, 0, tokens.size());
    }

    private List<String> tokenize(String expr) {
        List<String> tokens = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == ' ') {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else if (c == '(' || c == ')') {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                tokens.add(String.valueOf(c));
            } else if ("+-*/%".indexOf(c) >= 0) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                tokens.add(String.valueOf(c));
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) tokens.add(current.toString());
        return tokens;
    }

    private String evalTokens(List<String> tokens, int start, int end) {
        if (start >= end) return "0";

        // Handle parentheses
        if (tokens.get(start).equals("(") && end > start + 1) {
            int depth = 1, closeIdx = start + 1;
            while (closeIdx < end && depth > 0) {
                if (tokens.get(closeIdx).equals("(")) depth++;
                else if (tokens.get(closeIdx).equals(")")) depth--;
                closeIdx++;
            }
            if (depth == 0) {
                String inner = evalTokens(tokens, start + 1, closeIdx - 1);
                if (closeIdx < end) {
                    List<String> newTokens = new java.util.ArrayList<>();
                    newTokens.add(inner);
                    newTokens.addAll(tokens.subList(closeIdx, end));
                    return evalTokens(newTokens, 0, newTokens.size());
                }
                return inner;
            }
        }

        // Try to parse as integer first, then string comparison
        try {
            long val = Long.parseLong(tokens.get(start));
            if (start + 1 >= end) return String.valueOf(val);

            String op = tokens.get(start + 1);
            String right = evalTokens(tokens, start + 2, end);

            switch (op) {
                case "+" -> { return String.valueOf(val + Long.parseLong(right)); }
                case "-" -> { return String.valueOf(val - Long.parseLong(right)); }
                case "*" -> { return String.valueOf(val * Long.parseLong(right)); }
                case "/" -> {
                    long div = Long.parseLong(right);
                    if (div == 0) throw new RuntimeException("division by zero");
                    return String.valueOf(val / div);
                }
                case "%" -> {
                    long mod = Long.parseLong(right);
                    if (mod == 0) throw new RuntimeException("division by zero");
                    return String.valueOf(val % mod);
                }
                case "<" -> { return val < Long.parseLong(right) ? "1" : "0"; }
                case "<=" -> { return val <= Long.parseLong(right) ? "1" : "0"; }
                case ">" -> { return val > Long.parseLong(right) ? "1" : "0"; }
                case ">=" -> { return val >= Long.parseLong(right) ? "1" : "0"; }
                case "=" -> { return val == Long.parseLong(right) ? "1" : "0"; }
                case "!=" -> { return val != Long.parseLong(right) ? "1" : "0"; }
                case "&" -> {
                    if (val == 0) return "0";
                    return !right.equals("0") ? right : "0";
                }
                case "|" -> {
                    if (val != 0) return String.valueOf(val);
                    return !right.equals("0") ? right : "0";
                }
            }
        } catch (NumberFormatException e) {
            // String comparison
            String left = tokens.get(start);
            if (start + 1 >= end) return left;

            String op = tokens.get(start + 1);
            String right = evalTokens(tokens, start + 2, end);

            int cmp = left.compareTo(right);
            switch (op) {
                case "<" -> { return cmp < 0 ? "1" : "0"; }
                case "<=" -> { return cmp <= 0 ? "1" : "0"; }
                case ">" -> { return cmp > 0 ? "1" : "0"; }
                case ">=" -> { return cmp >= 0 ? "1" : "0"; }
                case "=" -> { return cmp == 0 ? "1" : "0"; }
                case "!=" -> { return cmp != 0 ? "1" : "0"; }
                case "&" -> { return left.isEmpty() || right.isEmpty() ? "0" : right; }
                case "|" -> { return !left.isEmpty() ? left : right; }
            }
        }

        return tokens.get(start);
    }

    private String[] splitArgs(String s) {
        List<String> parts = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            if (c == ' ' && depth == 0) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) parts.add(current.toString());
        return parts.toArray(new String[0]);
    }
}
