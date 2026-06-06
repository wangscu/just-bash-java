package com.justbash.commands.jq;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class JqCommand implements Command {
    @Override
    public String name() {
        return "jq";
    }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            boolean compact = false;
            boolean rawOutput = false;
            String filter = null;
            List<String> files = new ArrayList<>();

            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if (arg.equals("-c")) {
                    compact = true;
                } else if (arg.equals("-r")) {
                    rawOutput = true;
                } else if (arg.equals("--")) {
                    files.addAll(args.subList(i + 1, args.size()));
                    break;
                } else if (arg.startsWith("-")) {
                    // ignore unknown options for MVP
                } else {
                    if (filter == null) {
                        filter = arg;
                    } else {
                        files.add(arg);
                    }
                }
            }

            if (filter == null) {
                return new ExecResult("", "jq: error: no filter specified\n", 2);
            }

            String content;
            if (files.isEmpty()) {
                content = ctx.stdin().decodeUtf8();
            } else {
                try {
                    String path = files.get(0).startsWith("/") ? files.get(0) : ctx.cwd() + "/" + files.get(0);
                    content = ctx.fs().readFile(path).join();
                } catch (Exception e) {
                    return new ExecResult("", "jq: " + files.get(0) + ": No such file or directory\n", 2);
                }
            }

            try {
                String result = evaluateJq(content, filter, compact, rawOutput);
                return new ExecResult(result + "\n", "", 0);
            } catch (Exception e) {
                return new ExecResult("", "jq: " + e.getMessage() + "\n", 2);
            }
        });
    }

    private String evaluateJq(String content, String filter, boolean compact, boolean rawOutput) throws Exception {
        // Parse JSON
        Object parsed = parseJson(content.trim());

        // Apply filter
        Object result = applyFilter(parsed, filter.trim());

        // Serialize result
        if (rawOutput && result instanceof String s) {
            return s;
        }
        return toJson(result, compact ? "" : "  ");
    }

    private Object parseJson(String json) throws Exception {
        json = json.trim();
        if (json.equals("null")) return null;
        if (json.equals("true")) return true;
        if (json.equals("false")) return false;
        if (json.startsWith("\"")) {
            return parseString(json);
        }
        if (json.startsWith("[")) {
            return parseArray(json);
        }
        if (json.startsWith("{")) {
            return parseObject(json);
        }
        // Number
        try {
            if (json.contains(".")) return Double.parseDouble(json);
            return Long.parseLong(json);
        } catch (NumberFormatException e) {
            throw new Exception("invalid JSON");
        }
    }

    private String parseString(String json) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        while (i < json.length() - 1) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case '"' -> { sb.append('"'); i += 2; continue; }
                    case '\\' -> { sb.append('\\'); i += 2; continue; }
                    case '/' -> { sb.append('/'); i += 2; continue; }
                    case 'b' -> { sb.append('\b'); i += 2; continue; }
                    case 'f' -> { sb.append('\f'); i += 2; continue; }
                    case 'n' -> { sb.append('\n'); i += 2; continue; }
                    case 'r' -> { sb.append('\r'); i += 2; continue; }
                    case 't' -> { sb.append('\t'); i += 2; continue; }
                    case 'u' -> {
                        if (i + 5 < json.length()) {
                            String hex = json.substring(i + 2, i + 6);
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 6;
                            continue;
                        }
                    }
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private List<Object> parseArray(String json) throws Exception {
        List<Object> list = new ArrayList<>();
        String inner = json.substring(1, json.length() - 1).trim();
        if (inner.isEmpty()) return list;

        int depth = 0, start = 0;
        boolean inString = false;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '"' && (i == 0 || inner.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == '[' || c == '{') depth++;
                else if (c == ']' || c == '}') depth--;
                else if (c == ',' && depth == 0) {
                    list.add(parseJson(inner.substring(start, i).trim()));
                    start = i + 1;
                }
            }
        }
        list.add(parseJson(inner.substring(start).trim()));
        return list;
    }

    private Map<String, Object> parseObject(String json) throws Exception {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        String inner = json.substring(1, json.length() - 1).trim();
        if (inner.isEmpty()) return map;

        int depth = 0, start = 0;
        boolean inString = false;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '"' && (i == 0 || inner.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == '[' || c == '{') depth++;
                else if (c == ']' || c == '}') depth--;
                else if (c == ',' && depth == 0) {
                    parseKeyValue(map, inner.substring(start, i).trim());
                    start = i + 1;
                }
            }
        }
        parseKeyValue(map, inner.substring(start).trim());
        return map;
    }

    private void parseKeyValue(Map<String, Object> map, String kv) throws Exception {
        int colonIdx = kv.indexOf(':');
        if (colonIdx < 0) throw new Exception("invalid object");
        String key = (String) parseJson(kv.substring(0, colonIdx).trim());
        Object value = parseJson(kv.substring(colonIdx + 1).trim());
        map.put(key, value);
    }

    private Object applyFilter(Object data, String filter) throws Exception {
        filter = filter.trim();
        if (filter.equals(".")) return data;

        // Handle pipe
        if (filter.contains("|")) {
            String[] parts = splitPipes(filter);
            Object result = data;
            for (String part : parts) {
                result = applyFilter(result, part.trim());
            }
            return result;
        }

        // Handle .key or ."key"
        if (filter.startsWith(".")) {
            String rest = filter.substring(1);
            // Array iteration
            if (rest.equals("[]") && data instanceof List<?> list) {
                return list;
            }
            // Handle .key.subkey
            int dotIdx = -1;
            int bracketDepth = 0;
            for (int i = 0; i < rest.length(); i++) {
                char c = rest.charAt(i);
                if (c == '[') bracketDepth++;
                else if (c == ']') bracketDepth--;
                else if (c == '.' && bracketDepth == 0) {
                    dotIdx = i;
                    break;
                }
            }

            String key = dotIdx >= 0 ? rest.substring(0, dotIdx) : rest;
            String remainder = dotIdx >= 0 ? rest.substring(dotIdx) : "";

            // Handle bracket access like .foo[0]
            int bracketIdx = key.indexOf('[');
            if (bracketIdx >= 0) {
                String field = key.substring(0, bracketIdx);
                Object obj = data;
                if (!field.isEmpty() && obj instanceof Map) {
                    obj = ((Map<?, ?>) obj).get(field);
                }
                obj = applyBracketAccess(obj, key.substring(bracketIdx));
                if (!remainder.isEmpty()) {
                    return applyFilter(obj, "." + remainder.substring(1));
                }
                return obj;
            }

            if (data instanceof Map<?, ?> map) {
                Object value = map.get(stripQuotes(key));
                if (!remainder.isEmpty()) {
                    return applyFilter(value, "." + remainder.substring(1));
                }
                return value;
            }
            return null;
        }

        // Handle array iteration .[]
        if (filter.equals("[]")) {
            return data;
        }

        // Handle select()
        if (filter.startsWith("select(")) {
            String inner = filter.substring(7, filter.length() - 1).trim();
            if (data instanceof List<?> list) {
                List<Object> filtered = new ArrayList<>();
                for (Object item : list) {
                    if (isTruthy(applyFilter(item, inner))) {
                        filtered.add(item);
                    }
                }
                return filtered;
            }
            return isTruthy(applyFilter(data, inner)) ? data : null;
        }

        // Handle length
        if (filter.equals("length")) {
            if (data instanceof List<?> list) return list.size();
            if (data instanceof Map<?, ?> map) return map.size();
            if (data instanceof String s) return s.length();
            return 0;
        }

        // Handle keys
        if (filter.equals("keys")) {
            if (data instanceof Map<?, ?> map) {
                List<Object> keys = new ArrayList<>();
                for (Object k : map.keySet()) keys.add(k);
                return keys;
            }
            return new ArrayList<>();
        }

        // Handle comparison operators
        return evalComparison(data, filter);
    }

    private Object evalComparison(Object data, String filter) throws Exception {
        // Handle ==, !=, <, >, <=, >=
        for (String op : new String[]{"==", "!=", "<=", ">=", "<", ">"}) {
            int idx = filter.indexOf(op);
            if (idx > 0) {
                String left = filter.substring(0, idx).trim();
                String right = filter.substring(idx + op.length()).trim();
                Object lval = applyFilter(data, left);
                Object rval;
                if (right.startsWith(".")) {
                    rval = applyFilter(data, right);
                } else {
                    rval = parseJson(right);
                }
                boolean result = compare(lval, rval, op);
                return result;
            }
        }
        return data;
    }

    private boolean compare(Object a, Object b, String op) {
        if (a == null && b == null) return op.equals("==");
        if (a == null || b == null) return op.equals("!=");
        if (a instanceof Number na && b instanceof Number nb) {
            double da = na.doubleValue(), db = nb.doubleValue();
            return switch (op) {
                case "==" -> da == db;
                case "!=" -> da != db;
                case "<" -> da < db;
                case "<=" -> da <= db;
                case ">" -> da > db;
                case ">=" -> da >= db;
                default -> false;
            };
        }
        String sa = a.toString(), sb = b.toString();
        int cmp = sa.compareTo(sb);
        return switch (op) {
            case "==" -> cmp == 0;
            case "!=" -> cmp != 0;
            case "<" -> cmp < 0;
            case "<=" -> cmp <= 0;
            case ">" -> cmp > 0;
            case ">=" -> cmp >= 0;
            default -> false;
        };
    }

    private Object applyBracketAccess(Object data, String brackets) throws Exception {
        int closeIdx = brackets.indexOf(']');
        if (closeIdx < 0) return data;
        String indexStr = brackets.substring(1, closeIdx);
        String rest = closeIdx + 1 < brackets.length() ? brackets.substring(closeIdx + 1) : "";

        Object result;
        if (indexStr.startsWith("\"")) {
            String key = (String) parseJson(indexStr);
            if (data instanceof Map<?, ?> map) {
                result = map.get(key);
            } else {
                result = null;
            }
        } else {
            try {
                int idx = Integer.parseInt(indexStr);
                if (data instanceof List<?> list) {
                    result = idx >= 0 && idx < list.size() ? list.get(idx) : null;
                } else {
                    result = null;
                }
            } catch (NumberFormatException e) {
                result = null;
            }
        }

        if (!rest.isEmpty() && rest.startsWith("[")) {
            return applyBracketAccess(result, rest);
        }
        return result;
    }

    private String[] splitPipes(String filter) {
        List<String> parts = new ArrayList<>();
        int depth = 0, start = 0;
        boolean inString = false;
        for (int i = 0; i < filter.length(); i++) {
            char c = filter.charAt(i);
            if (c == '"') inString = !inString;
            if (!inString) {
                if (c == '(') depth++;
                else if (c == ')') depth--;
                else if (c == '|' && depth == 0) {
                    parts.add(filter.substring(start, i));
                    start = i + 1;
                }
            }
        }
        parts.add(filter.substring(start));
        return parts.toArray(new String[0]);
    }

    private boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0;
        if (value instanceof String s) return !s.isEmpty();
        if (value instanceof List<?> l) return !l.isEmpty();
        if (value instanceof Map<?, ?> m) return !m.isEmpty();
        return true;
    }

    private String stripQuotes(String s) {
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private String toJson(Object value, String indent) {
        if (value == null) return "null";
        if (value instanceof Boolean b) return b.toString();
        if (value instanceof Number n) return n.toString();
        if (value instanceof String s) return "\"" + escapeJson(s) + "\"";
        if (value instanceof List<?> list) {
            if (list.isEmpty()) return "[]";
            StringBuilder sb = new StringBuilder("[");
            if (!indent.isEmpty()) sb.append('\n');
            String childIndent = indent.isEmpty() ? "" : indent + "  ";
            for (int i = 0; i < list.size(); i++) {
                if (!indent.isEmpty()) sb.append(childIndent);
                sb.append(toJson(list.get(i), childIndent));
                if (i < list.size() - 1) sb.append(",");
                if (!indent.isEmpty()) sb.append('\n');
                else if (i < list.size() - 1) sb.append(" ");
            }
            if (!indent.isEmpty()) sb.append(indent);
            sb.append(']');
            return sb.toString();
        }
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) return "{}";
            StringBuilder sb = new StringBuilder("{");
            if (!indent.isEmpty()) sb.append('\n');
            String childIndent = indent.isEmpty() ? "" : indent + "  ";
            int i = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!indent.isEmpty()) sb.append(childIndent);
                sb.append("\"").append(escapeJson(entry.getKey().toString())).append("\": ");
                sb.append(toJson(entry.getValue(), childIndent));
                if (i < map.size() - 1) sb.append(",");
                if (!indent.isEmpty()) sb.append('\n');
                else if (i < map.size() - 1) sb.append(" ");
                i++;
            }
            if (!indent.isEmpty()) sb.append(indent);
            sb.append('}');
            return sb.toString();
        }
        return "\"" + escapeJson(value.toString()) + "\"";
    }

    private String escapeJson(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
