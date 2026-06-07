package com.justbash.commands.xan;

import com.justbash.commands.queryengine.AstNode;
import com.justbash.commands.queryengine.EvalContext;
import com.justbash.commands.queryengine.Evaluator;
import com.justbash.commands.queryengine.Parser;

import java.util.*;

public class AggParser {

    public record AggSpec(String func, String expr, String alias) {}

    public static List<AggSpec> parseAggExpr(String input) {
        List<AggSpec> specs = new ArrayList<>();
        int i = 0;
        while (i < input.length()) {
            while (i < input.length() && (input.charAt(i) == ' ' || input.charAt(i) == ',')) i++;
            if (i >= input.length()) break;

            // Parse function name
            int funcStart = i;
            while (i < input.length() && Character.isLetterOrDigit(input.charAt(i))) i++;
            String func = input.substring(funcStart, i).trim();

            while (i < input.length() && input.charAt(i) == ' ') i++;

            // Expect opening paren
            if (i >= input.length() || input.charAt(i) != '(') break;
            i++; // skip (

            // Parse expression inside parens (handling nested parens)
            int parenDepth = 1;
            int exprStart = i;
            while (i < input.length() && parenDepth > 0) {
                if (input.charAt(i) == '(') parenDepth++;
                else if (input.charAt(i) == ')') parenDepth--;
                if (parenDepth > 0) i++;
            }
            String innerExpr = input.substring(exprStart, i).trim();
            i++; // skip )

            while (i < input.length() && input.charAt(i) == ' ') i++;

            // Check for "as alias"
            String alias = null;
            if (i + 3 <= input.length() && input.substring(i, i + 3).equalsIgnoreCase("as ")) {
                i += 3;
                while (i < input.length() && input.charAt(i) == ' ') i++;
                int aliasStart = i;
                while (i < input.length() && (Character.isLetterOrDigit(input.charAt(i)) || input.charAt(i) == '_')) i++;
                alias = input.substring(aliasStart, i);
            }

            if (alias == null || alias.isEmpty()) {
                alias = innerExpr.isEmpty() ? func + "()" : func + "(" + innerExpr + ")";
            }

            specs.add(new AggSpec(func.toLowerCase(), innerExpr, alias));
        }
        return specs;
    }

    public static Object computeAgg(List<Map<String, Object>> data, AggSpec spec, EvalContext evalCtx) {
        String func = spec.func();
        String expr = spec.expr();

        if (func.equals("count") && expr.isEmpty()) {
            return (double) data.size();
        }

        List<Object> values = new ArrayList<>();
        if (isSimpleColumn(expr)) {
            for (Map<String, Object> row : data) {
                Object v = row.get(expr);
                if (v != null) values.add(v);
            }
        } else if (!expr.isEmpty()) {
            for (Map<String, Object> row : data) {
                try {
                    AstNode ast = Parser.parse("." + expr);
                    List<Object> results = Evaluator.evaluate(row, ast, evalCtx);
                    Object v = results.isEmpty() ? null : results.get(0);
                    if (v != null) values.add(v);
                } catch (Exception e) {
                    // skip
                }
            }
        }

        switch (func) {
            case "count": {
                if (isSimpleColumn(expr)) return (double) values.size();
                return (double) values.stream().filter(v -> v != null && !Boolean.FALSE.equals(v)).count();
            }
            case "sum": {
                double sum = 0;
                for (Object v : values) {
                    Double n = toDouble(v);
                    if (n != null) sum += n;
                }
                return sum;
            }
            case "mean":
            case "avg": {
                double sum = 0;
                int count = 0;
                for (Object v : values) {
                    Double n = toDouble(v);
                    if (n != null) { sum += n; count++; }
                }
                return count > 0 ? sum / count : null;
            }
            case "min": {
                Double min = null;
                for (Object v : values) {
                    Double n = toDouble(v);
                    if (n != null && (min == null || n < min)) min = n;
                }
                return min;
            }
            case "max": {
                Double max = null;
                for (Object v : values) {
                    Double n = toDouble(v);
                    if (n != null && (max == null || n > max)) max = n;
                }
                return max;
            }
            case "first":
                return values.isEmpty() ? null : String.valueOf(values.get(0));
            case "last":
                return values.isEmpty() ? null : String.valueOf(values.get(values.size() - 1));
            case "median": {
                List<Double> nums = new ArrayList<>();
                for (Object v : values) {
                    Double n = toDouble(v);
                    if (n != null) nums.add(n);
                }
                if (nums.isEmpty()) return null;
                Collections.sort(nums);
                int mid = nums.size() / 2;
                if (nums.size() % 2 == 0) {
                    return (nums.get(mid - 1) + nums.get(mid)) / 2.0;
                }
                return nums.get(mid);
            }
            case "mode": {
                Map<String, Integer> counts = new HashMap<>();
                for (Object v : values) {
                    String key = String.valueOf(v);
                    counts.put(key, counts.getOrDefault(key, 0) + 1);
                }
                int maxCount = 0;
                String mode = null;
                for (Map.Entry<String, Integer> e : counts.entrySet()) {
                    if (e.getValue() > maxCount) {
                        maxCount = e.getValue();
                        mode = e.getKey();
                    }
                }
                return mode;
            }
            case "cardinality": {
                Set<String> unique = new HashSet<>();
                for (Object v : values) unique.add(String.valueOf(v));
                return (double) unique.size();
            }
            case "values": {
                List<String> parts = new ArrayList<>();
                for (Object v : values) parts.add(String.valueOf(v));
                return String.join("|", parts);
            }
            case "distinct_values": {
                Set<String> unique = new TreeSet<>();
                for (Object v : values) unique.add(String.valueOf(v));
                return String.join("|", unique);
            }
            case "all": {
                if (data.isEmpty()) return true;
                for (Map<String, Object> row : data) {
                    try {
                        AstNode ast = Parser.parse("." + expr);
                        List<Object> results = Evaluator.evaluate(row, ast, evalCtx);
                        Object v = results.isEmpty() ? null : results.get(0);
                        if (v == null || Boolean.FALSE.equals(v)) return false;
                    } catch (Exception e) {
                        return false;
                    }
                }
                return true;
            }
            case "any": {
                for (Map<String, Object> row : data) {
                    try {
                        AstNode ast = Parser.parse("." + expr);
                        List<Object> results = Evaluator.evaluate(row, ast, evalCtx);
                        Object v = results.isEmpty() ? null : results.get(0);
                        if (v != null && !Boolean.FALSE.equals(v)) return true;
                    } catch (Exception e) {
                        // continue
                    }
                }
                return false;
            }
            default:
                return null;
        }
    }

    public static Map<String, Object> buildAggRow(List<Map<String, Object>> data, List<AggSpec> specs, EvalContext evalCtx) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (AggSpec spec : specs) {
            row.put(spec.alias(), computeAgg(data, spec, evalCtx));
        }
        return row;
    }

    private static boolean isSimpleColumn(String expr) {
        return expr != null && !expr.isEmpty() && expr.matches("\\w+");
    }

    private static Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
