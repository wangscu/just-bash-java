package com.justbash.commands.queryengine;

import java.util.*;

/**
 * Builtin function dispatch for the query engine.
 *
 * <p>Handles all builtin function evaluation. Returns {@code null} for builtins
 * not handled here (the Evaluator will throw "Unknown function").
 */
public final class Builtins {

    private Builtins() {}


    /**
     * Evaluate a builtin function call.
     *
     * @param value  the input value
     * @param name   the builtin name
     * @param args   the argument AST nodes
     * @param ctx    the evaluation context
     * @param eval   the evaluate callback for evaluating arguments
     * @return the result list, or null if this builtin is not handled here
     */
    public static List<Object> evalBuiltin(Object value, String name, List<AstNode> args,
                                           EvalContext ctx, Evaluator.EvaluateCallback eval) {
        // Simple single-argument math functions
        List<Object> simpleMathResult = evalSimpleMathBuiltin(value, name);
        if (simpleMathResult != null) return simpleMathResult;

        // Type builtins
        List<Object> typeResult = evalTypeBuiltin(value, name);
        if (typeResult != null) return typeResult;

        // Math builtins with args
        List<Object> mathResult = evalMathBuiltin(value, name, args, ctx, eval);
        if (mathResult != null) return mathResult;

        // Format builtins
        List<Object> formatResult = evalFormatBuiltin(value, name, ctx);
        if (formatResult != null) return formatResult;

        // Object builtins
        List<Object> objectResult = evalObjectBuiltin(value, name, args, ctx, eval);
        if (objectResult != null) return objectResult;

        // Array builtins
        List<Object> arrayResult = evalArrayBuiltin(value, name, args, ctx, eval);
        if (arrayResult != null) return arrayResult;

        // String builtins
        List<Object> stringResult = evalStringBuiltin(value, name, args, ctx, eval);
        if (stringResult != null) return stringResult;

        // Index builtins
        List<Object> indexResult = evalIndexBuiltin(value, name, args, ctx, eval);
        if (indexResult != null) return indexResult;

        // Control builtins
        List<Object> controlResult = evalControlBuiltin(value, name, args, ctx, eval);
        if (controlResult != null) return controlResult;

        // Path builtins
        List<Object> pathResult = evalPathBuiltin(value, name, args, ctx, eval);
        if (pathResult != null) return pathResult;

        // Navigation builtins
        List<Object> navResult = evalNavigationBuiltin(value, name, args, ctx, eval);
        if (navResult != null) return navResult;

        // Date builtins
        List<Object> dateResult = evalDateBuiltin(value, name, args, ctx, eval);
        if (dateResult != null) return dateResult;

        // SQL builtins
        List<Object> sqlResult = evalSqlBuiltin(value, name, args, ctx, eval);
        if (sqlResult != null) return sqlResult;

        return null;
    }

    // ============================================================================
    // Simple Math Functions (single numeric argument)
    // ============================================================================

    private static final Map<String, java.util.function.Function<Double, Double>> SIMPLE_MATH = new LinkedHashMap<>();
    static {
        SIMPLE_MATH.put("floor", Math::floor);
        SIMPLE_MATH.put("ceil", Math::ceil);
        SIMPLE_MATH.put("round", (x) -> (double) Math.round(x));
        SIMPLE_MATH.put("sqrt", Math::sqrt);
        SIMPLE_MATH.put("log", Math::log);
        SIMPLE_MATH.put("log10", Math::log10);
        SIMPLE_MATH.put("log2", (x) -> Math.log(x) / Math.log(2));
        SIMPLE_MATH.put("exp", Math::exp);
        SIMPLE_MATH.put("sin", Math::sin);
        SIMPLE_MATH.put("cos", Math::cos);
        SIMPLE_MATH.put("tan", Math::tan);
        SIMPLE_MATH.put("asin", Math::asin);
        SIMPLE_MATH.put("acos", Math::acos);
        SIMPLE_MATH.put("atan", Math::atan);
        SIMPLE_MATH.put("sinh", Math::sinh);
        SIMPLE_MATH.put("cosh", Math::cosh);
        SIMPLE_MATH.put("tanh", Math::tanh);
        SIMPLE_MATH.put("asinh", (x) -> Math.log(x + Math.sqrt(x * x + 1)));
        SIMPLE_MATH.put("acosh", (x) -> Math.log(x + Math.sqrt(x * x - 1)));
        SIMPLE_MATH.put("atanh", (x) -> 0.5 * Math.log((1 + x) / (1 - x)));
        SIMPLE_MATH.put("cbrt", Math::cbrt);
        SIMPLE_MATH.put("expm1", Math::expm1);
        SIMPLE_MATH.put("log1p", Math::log1p);
        SIMPLE_MATH.put("trunc", (x) -> (double) (long) (double) x);
    }

    private static List<Object> evalSimpleMathBuiltin(Object value, String name) {
        java.util.function.Function<Double, Double> fn = SIMPLE_MATH.get(name);
        if (fn == null) return null;
        if (value instanceof Number) {
            return List.of(fn.apply(((Number) value).doubleValue()));
        }
        return List.of((Object) null);
    }

    // ============================================================================
    // Type Builtins
    // ============================================================================

    private static List<Object> evalTypeBuiltin(Object value, String name) {
        switch (name) {
            case "type":
                if (value == null) return List.of("null");
                if (value instanceof List) return List.of("array");
                if (value instanceof Boolean) return List.of("boolean");
                if (value instanceof Number) return List.of("number");
                if (value instanceof String) return List.of("string");
                if (value instanceof Map) return List.of("object");
                return List.of("null");

            case "infinite":
                return List.of(Double.POSITIVE_INFINITY);

            case "nan":
                return List.of(Double.NaN);

            case "isinfinite":
                return List.of(value instanceof Number && !Double.isFinite(((Number) value).doubleValue()));

            case "isnan":
                return List.of(value instanceof Number && Double.isNaN(((Number) value).doubleValue()));

            case "isnormal":
                return List.of(value instanceof Number && Double.isFinite(((Number) value).doubleValue()) && ((Number) value).doubleValue() != 0);

            case "isfinite":
                return List.of(value instanceof Number && Double.isFinite(((Number) value).doubleValue()));

            case "numbers":
                return value instanceof Number ? List.of(value) : List.of();

            case "strings":
                return value instanceof String ? List.of(value) : List.of();

            case "booleans":
                return value instanceof Boolean ? List.of(value) : List.of();

            case "nulls":
                return value == null ? new ArrayList<Object>(Collections.singletonList(null)) : List.of();

            case "arrays":
                return value instanceof List ? List.of(value) : List.of();

            case "objects":
                return value instanceof Map && !(value instanceof List) ? List.of(value) : List.of();

            case "iterables":
                return (value instanceof List || (value instanceof Map && !(value instanceof List))) ? List.of(value) : List.of();

            case "scalars":
                return !(value instanceof List) && !(value instanceof Map) ? List.of(value) : List.of();

            case "values":
                return value == null ? List.of() : List.of(value);

            case "not":
                return ValueOperations.isTruthy(value) ? List.of(false) : List.of(true);

            case "null":
                return List.of((Object) null);

            case "true":
                return List.of(true);

            case "false":
                return List.of(false);

            case "empty":
                return List.of();

            default:
                return null;
        }
    }

    // ============================================================================
    // Math Builtins (with arguments)
    // ============================================================================

    private static List<Object> evalMathBuiltin(Object value, String name, List<AstNode> args,
                                                EvalContext ctx, Evaluator.EvaluateCallback eval) {
        switch (name) {
            case "fabs":
            case "abs":
                if (value instanceof Number) return List.of(Math.abs(((Number) value).doubleValue()));
                if (value instanceof String) return List.of(value);
                return List.of((Object) null);

            case "exp10":
                if (value instanceof Number) return List.of(Math.pow(10, ((Number) value).doubleValue()));
                return List.of((Object) null);

            case "exp2":
                if (value instanceof Number) return List.of(Math.pow(2, ((Number) value).doubleValue()));
                return List.of((Object) null);

            case "pow": {
                if (args.size() < 2) return List.of((Object) null);
                Object base = eval.evaluate(value, args.get(0), ctx).get(0);
                Object exp = eval.evaluate(value, args.get(1), ctx).get(0);
                if (base instanceof Number && exp instanceof Number) {
                    return List.of(Math.pow(((Number) base).doubleValue(), ((Number) exp).doubleValue()));
                }
                return List.of((Object) null);
            }

            case "atan2": {
                if (args.size() < 2) return List.of((Object) null);
                Object y = eval.evaluate(value, args.get(0), ctx).get(0);
                Object x = eval.evaluate(value, args.get(1), ctx).get(0);
                if (y instanceof Number && x instanceof Number) {
                    return List.of(Math.atan2(((Number) y).doubleValue(), ((Number) x).doubleValue()));
                }
                return List.of((Object) null);
            }

            case "hypot": {
                if (!(value instanceof Number) || args.isEmpty()) return List.of((Object) null);
                Object y = eval.evaluate(value, args.get(0), ctx).get(0);
                if (y instanceof Number) {
                    return List.of(Math.hypot(((Number) value).doubleValue(), ((Number) y).doubleValue()));
                }
                return List.of((Object) null);
            }

            case "fma": {
                if (!(value instanceof Number) || args.size() < 2) return List.of((Object) null);
                Object y = eval.evaluate(value, args.get(0), ctx).get(0);
                Object z = eval.evaluate(value, args.get(1), ctx).get(0);
                if (y instanceof Number && z instanceof Number) {
                    return List.of(((Number) value).doubleValue() * ((Number) y).doubleValue() + ((Number) z).doubleValue());
                }
                return List.of((Object) null);
            }

            case "copysign": {
                if (!(value instanceof Number) || args.isEmpty()) return List.of((Object) null);
                Object y = eval.evaluate(value, args.get(0), ctx).get(0);
                if (y instanceof Number) {
                    double v = ((Number) value).doubleValue();
                    double yv = ((Number) y).doubleValue();
                    return List.of(Math.copySign(v, yv));
                }
                return List.of((Object) null);
            }

            case "drem":
            case "remainder": {
                if (!(value instanceof Number) || args.isEmpty()) return List.of((Object) null);
                Object y = eval.evaluate(value, args.get(0), ctx).get(0);
                if (y instanceof Number) {
                    double v = ((Number) value).doubleValue();
                    double yv = ((Number) y).doubleValue();
                    return List.of(v - Math.round(v / yv) * yv);
                }
                return List.of((Object) null);
            }

            case "fdim": {
                if (!(value instanceof Number) || args.isEmpty()) return List.of((Object) null);
                Object y = eval.evaluate(value, args.get(0), ctx).get(0);
                if (y instanceof Number) {
                    return List.of(Math.max(0, ((Number) value).doubleValue() - ((Number) y).doubleValue()));
                }
                return List.of((Object) null);
            }

            case "fmax": {
                if (!(value instanceof Number) || args.isEmpty()) return List.of((Object) null);
                Object y = eval.evaluate(value, args.get(0), ctx).get(0);
                if (y instanceof Number) {
                    return List.of(Math.max(((Number) value).doubleValue(), ((Number) y).doubleValue()));
                }
                return List.of((Object) null);
            }

            case "fmin": {
                if (!(value instanceof Number) || args.isEmpty()) return List.of((Object) null);
                Object y = eval.evaluate(value, args.get(0), ctx).get(0);
                if (y instanceof Number) {
                    return List.of(Math.min(((Number) value).doubleValue(), ((Number) y).doubleValue()));
                }
                return List.of((Object) null);
            }

            case "ldexp": {
                if (!(value instanceof Number) || args.isEmpty()) return List.of((Object) null);
                Object exp = eval.evaluate(value, args.get(0), ctx).get(0);
                if (exp instanceof Number) {
                    return List.of(((Number) value).doubleValue() * Math.pow(2, ((Number) exp).doubleValue()));
                }
                return List.of((Object) null);
            }

            case "scalbn":
            case "scalbln": {
                if (!(value instanceof Number) || args.isEmpty()) return List.of((Object) null);
                Object exp = eval.evaluate(value, args.get(0), ctx).get(0);
                if (exp instanceof Number) {
                    return List.of(((Number) value).doubleValue() * Math.pow(2, ((Number) exp).doubleValue()));
                }
                return List.of((Object) null);
            }

            case "nearbyint":
                if (value instanceof Number) return List.of((double) Math.round(((Number) value).doubleValue()));
                return List.of((Object) null);

            case "logb":
                if (value instanceof Number) {
                    double v = Math.abs(((Number) value).doubleValue());
                    return List.of((double) (int) (Math.log(v) / Math.log(2)));
                }
                return List.of((Object) null);

            case "significand":
                if (value instanceof Number) {
                    double v = ((Number) value).doubleValue();
                    double exp = Math.floor(Math.log(Math.abs(v)) / Math.log(2));
                    return List.of(v / Math.pow(2, exp));
                }
                return List.of((Object) null);

            case "frexp":
                if (value instanceof Number) {
                    double v = ((Number) value).doubleValue();
                    if (v == 0) return List.of(List.of(0.0, 0.0));
                    double exp = Math.floor(Math.log(Math.abs(v)) / Math.log(2)) + 1;
                    double mantissa = v / Math.pow(2, exp);
                    return List.of(List.of(mantissa, exp));
                }
                return List.of((Object) null);

            case "modf":
                if (value instanceof Number) {
                    double v = ((Number) value).doubleValue();
                    double intPart = (v >= 0) ? Math.floor(v) : Math.ceil(v);
                    double fracPart = v - intPart;
                    return List.of(List.of(fracPart, intPart));
                }
                return List.of((Object) null);

            default:
                return null;
        }
    }

    // ============================================================================
    // Format Builtins
    // ============================================================================

    private static List<Object> evalFormatBuiltin(Object value, String name, EvalContext ctx) {
        switch (name) {
            case "@base64":
                if (value instanceof String) {
                    return List.of(Base64.getEncoder().encodeToString(((String) value).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                }
                return List.of((Object) null);

            case "@base64d":
                if (value instanceof String) {
                    try {
                        return List.of(new String(Base64.getDecoder().decode((String) value), java.nio.charset.StandardCharsets.UTF_8));
                    } catch (IllegalArgumentException e) {
                        return List.of((Object) null);
                    }
                }
                return List.of((Object) null);

            case "@uri":
                if (value instanceof String) {
                    try {
                        String encoded = java.net.URLEncoder.encode((String) value, java.nio.charset.StandardCharsets.UTF_8);
                        // jq encodes space as %20, not +
                        encoded = encoded.replace("+", "%20");
                        return List.of(encoded);
                    } catch (Exception e) {
                        return List.of((Object) null);
                    }
                }
                return List.of((Object) null);

            case "@urid":
                if (value instanceof String) {
                    try {
                        return List.of(java.net.URLDecoder.decode((String) value, java.nio.charset.StandardCharsets.UTF_8));
                    } catch (Exception e) {
                        return List.of((Object) null);
                    }
                }
                return List.of((Object) null);

            case "@csv": {
                if (!(value instanceof List)) return List.of((Object) null);
                List<?> list = (List<?>) value;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(",");
                    Object v = list.get(i);
                    if (v == null) {
                        // empty
                    } else if (v instanceof Boolean) {
                        sb.append(v);
                    } else if (v instanceof Number) {
                        sb.append(v);
                    } else {
                        String s = v.toString();
                        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
                            sb.append("\"").append(s.replace("\"", "\"\"")).append("\"");
                        } else {
                            sb.append(s);
                        }
                    }
                }
                return List.of(sb.toString());
            }

            case "@tsv": {
                if (!(value instanceof List)) return List.of((Object) null);
                List<?> list = (List<?>) value;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append("\t");
                    Object v = list.get(i);
                    String s = v == null ? "" : v.toString();
                    s = s.replace("\t", "\\t").replace("\n", "\\n");
                    sb.append(s);
                }
                return List.of(sb.toString());
            }

            case "@json": {
                int maxDepth = ctx.limits().maxDepth();
                if (ValueOperations.getValueDepth(value, maxDepth + 1) > maxDepth) {
                    return List.of((Object) null);
                }
                return List.of(toJson(value));
            }

            case "@html":
                if (value instanceof String) {
                    String s = (String) value;
                    s = s.replace("&", "&amp;")
                         .replace("<", "&lt;")
                         .replace(">", "&gt;")
                         .replace("'", "&apos;")
                         .replace("\"", "&quot;");
                    return List.of(s);
                }
                return List.of((Object) null);

            case "@sh":
                if (value instanceof String) {
                    String s = (String) value;
                    return List.of("'" + s.replace("'", "'\\''") + "'");
                }
                return List.of((Object) null);

            case "@text":
                if (value instanceof String) return List.of(value);
                if (value == null) return List.of("");
                return List.of(value.toString());

            default:
                return null;
        }
    }

    // ============================================================================
    // Object Builtins
    // ============================================================================

    private static List<Object> evalObjectBuiltin(Object value, String name, List<AstNode> args,
                                                  EvalContext ctx, Evaluator.EvaluateCallback eval) {
        switch (name) {
            case "keys":
                if (value instanceof List) {
                    List<?> list = (List<?>) value;
                    List<Object> indices = new ArrayList<>();
                    for (int i = 0; i < list.size(); i++) indices.add((double) i);
                    return List.of(indices);
                }
                if (value instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) value;
                    List<String> keys = new ArrayList<>();
                    for (Object k : map.keySet()) keys.add(k.toString());
                    Collections.sort(keys);
                    return List.of(keys);
                }
                return List.of((Object) null);

            case "keys_unsorted":
                if (value instanceof List) {
                    List<?> list = (List<?>) value;
                    List<Object> indices = new ArrayList<>();
                    for (int i = 0; i < list.size(); i++) indices.add((double) i);
                    return List.of(indices);
                }
                if (value instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) value;
                    List<String> keys = new ArrayList<>();
                    for (Object k : map.keySet()) keys.add(k.toString());
                    return List.of(keys);
                }
                return List.of((Object) null);

            case "length":
                if (value instanceof String) return List.of((double) ((String) value).length());
                if (value instanceof List) return List.of((double) ((List<?>) value).size());
                if (value instanceof Map) return List.of((double) ((Map<?, ?>) value).size());
                if (value == null) return List.of(0.0);
                if (value instanceof Number) return List.of(Math.abs(((Number) value).doubleValue()));
                return List.of((Object) null);

            case "utf8bytelength": {
                if (value instanceof String) {
                    return List.of((double) ((String) value).getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
                }
                String typeName = value == null ? "null" : (value instanceof List ? "array" : value.getClass().getSimpleName().toLowerCase());
                String valueStr = typeName.equals("array") || typeName.equals("object") ? toJson(value) : String.valueOf(value);
                throw new RuntimeException(typeName + " (" + valueStr + ") only strings have UTF-8 byte length");
            }

            case "to_entries": {
                Map<String, Object> obj = SafeObject.asQueryRecord(value);
                if (obj != null) {
                    List<Map<String, Object>> entries = new ArrayList<>();
                    for (Map.Entry<String, Object> e : obj.entrySet()) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("key", e.getKey());
                        entry.put("value", e.getValue());
                        entries.add(entry);
                    }
                    return List.of(entries);
                }
                return List.of((Object) null);
            }

            case "from_entries": {
                if (value instanceof List) {
                    Map<String, Object> result = new LinkedHashMap<>();
                    for (Object item : (List<?>) value) {
                        Map<String, Object> obj = SafeObject.asQueryRecord(item);
                        if (obj != null) {
                            Object key = obj.containsKey("key") ? obj.get("key") :
                                         obj.containsKey("Key") ? obj.get("Key") :
                                         obj.containsKey("name") ? obj.get("name") :
                                         obj.containsKey("Name") ? obj.get("Name") :
                                         obj.containsKey("k") ? obj.get("k") : null;
                            Object val = obj.containsKey("value") ? obj.get("value") :
                                         obj.containsKey("Value") ? obj.get("Value") :
                                         obj.containsKey("v") ? obj.get("v") : null;
                            if (key != null) {
                                String strKey = key.toString();
                                if (SafeObject.isSafeKey(strKey)) {
                                    SafeObject.safeSet(result, strKey, val);
                                }
                            }
                        }
                    }
                    return List.of(result);
                }
                return List.of((Object) null);
            }

            case "with_entries": {
                if (args.isEmpty()) return List.of(value);
                Map<String, Object> obj = SafeObject.asQueryRecord(value);
                if (obj != null) {
                    List<Map<String, Object>> entries = new ArrayList<>();
                    for (Map.Entry<String, Object> e : obj.entrySet()) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("key", e.getKey());
                        entry.put("value", e.getValue());
                        entries.add(entry);
                    }
                    List<Object> mapped = new ArrayList<>();
                    for (Map<String, Object> entry : entries) {
                        mapped.addAll(eval.evaluate(entry, args.get(0), ctx));
                    }
                    Map<String, Object> result = new LinkedHashMap<>();
                    for (Object item : mapped) {
                        Map<String, Object> m = SafeObject.asQueryRecord(item);
                        if (m != null) {
                            Object key = m.containsKey("key") ? m.get("key") :
                                         m.containsKey("name") ? m.get("name") :
                                         m.containsKey("k") ? m.get("k") : null;
                            Object val = m.containsKey("value") ? m.get("value") :
                                         m.containsKey("v") ? m.get("v") : null;
                            if (key != null) {
                                String strKey = key.toString();
                                if (SafeObject.isSafeKey(strKey)) {
                                    SafeObject.safeSet(result, strKey, val);
                                }
                            }
                        }
                    }
                    return List.of(result);
                }
                return List.of((Object) null);
            }

            case "reverse":
                if (value instanceof List) {
                    List<Object> copy = new ArrayList<>((List<?>) value);
                    Collections.reverse(copy);
                    return List.of(copy);
                }
                if (value instanceof String) {
                    return List.of(new StringBuilder((String) value).reverse().toString());
                }
                return List.of((Object) null);

            case "flatten": {
                if (!(value instanceof List)) return List.of((Object) null);
                List<Object> depths;
                if (!args.isEmpty()) {
                    depths = new ArrayList<>();
                    for (Object d : eval.evaluate(value, args.get(0), ctx)) {
                        if (d instanceof Number) depths.add(((Number) d).intValue());
                    }
                } else {
                    depths = List.of(Integer.MAX_VALUE);
                }
                List<Object> results = new ArrayList<>();
                for (Object d : depths) {
                    int depth = d instanceof Number ? ((Number) d).intValue() : Integer.MAX_VALUE;
                    if (depth < 0) throw new RuntimeException("flatten depth must not be negative");
                    results.add(flattenList((List<?>) value, depth));
                }
                return results;
            }

            case "unique":
                if (value instanceof List) {
                    Set<String> seen = new LinkedHashSet<>();
                    List<Object> result = new ArrayList<>();
                    for (Object item : (List<?>) value) {
                        String key = toJson(item);
                        if (seen.add(key)) result.add(item);
                    }
                    return List.of(result);
                }
                return List.of((Object) null);

            case "tojson":
            case "tojsonstream": {
                int maxDepth = ctx.limits().maxDepth();
                if (ValueOperations.getValueDepth(value, maxDepth + 1) > maxDepth) {
                    return List.of((Object) null);
                }
                return List.of(toJson(value));
            }

            case "fromjson": {
                if (value instanceof String) {
                    String trimmed = ((String) value).trim().toLowerCase();
                    if (trimmed.equals("nan")) return List.of(Double.NaN);
                    if (trimmed.equals("inf") || trimmed.equals("infinity")) return List.of(Double.POSITIVE_INFINITY);
                    if (trimmed.equals("-inf") || trimmed.equals("-infinity")) return List.of(Double.NEGATIVE_INFINITY);
                    try {
                        return List.of(parseJson((String) value));
                    } catch (Exception e) {
                        throw new RuntimeException("Invalid JSON: " + value);
                    }
                }
                return List.of(value);
            }

            case "tostring":
                if (value instanceof String) return List.of(value);
                return List.of(toJson(value));

            case "tonumber": {
                if (value instanceof Number) return List.of(value);
                if (value instanceof String) {
                    try {
                        double n = Double.parseDouble((String) value);
                        if (Double.isNaN(n)) {
                            throw new RuntimeException("\"" + value + "\" cannot be parsed as a number");
                        }
                        return List.of(n);
                    } catch (NumberFormatException e) {
                        throw new RuntimeException("\"" + value + "\" cannot be parsed as a number");
                    }
                }
                throw new RuntimeException(value.getClass().getSimpleName().toLowerCase() + " cannot be parsed as a number");
            }

            case "toboolean": {
                if (value instanceof Boolean) return List.of(value);
                if (value instanceof String) {
                    if ("true".equals(value)) return List.of(true);
                    if ("false".equals(value)) return List.of(false);
                    throw new RuntimeException("string (\"" + value + "\") cannot be parsed as a boolean");
                }
                String typeName = value == null ? "null" : (value instanceof List ? "array" : value.getClass().getSimpleName().toLowerCase());
                String valueStr = typeName.equals("array") || typeName.equals("object") ? toJson(value) : String.valueOf(value);
                throw new RuntimeException(typeName + " (" + valueStr + ") cannot be parsed as a boolean");
            }

            case "tostream": {
                List<Object> results = new ArrayList<>();
                walkStream(value, List.of(), results);
                results.add(List.of());
                return results;
            }

            case "fromstream": {
                if (args.isEmpty()) return List.of(value);
                List<Object> streamItems = eval.evaluate(value, args.get(0), ctx);
                Object result = null;
                for (Object item : streamItems) {
                    if (!(item instanceof List)) continue;
                    List<?> arr = (List<?>) item;
                    if (arr.size() == 1 && arr.get(0) instanceof List && ((List<?>) arr.get(0)).isEmpty()) {
                        continue; // end marker
                    }
                    if (arr.size() != 2) continue;
                    List<?> path = arr.get(0) instanceof List ? (List<?>) arr.get(0) : null;
                    Object val = arr.get(1);
                    if (path == null) continue;
                    result = setStreamPath(result, path, val);
                }
                return List.of(result);
            }

            case "truncate_stream": {
                int depth = value instanceof Number ? (int) Math.floor(((Number) value).doubleValue()) : 0;
                if (args.isEmpty()) return List.of();
                List<Object> streamItems = eval.evaluate(value, args.get(0), ctx);
                List<Object> results = new ArrayList<>();
                for (Object item : streamItems) {
                    if (!(item instanceof List)) continue;
                    List<?> arr = (List<?>) item;
                    if (arr.size() == 1 && arr.get(0) instanceof List) {
                        List<?> path = (List<?>) arr.get(0);
                        if (path.size() > depth) {
                            results.add(List.of(path.subList(depth, path.size())));
                        }
                    } else if (arr.size() == 2 && arr.get(0) instanceof List) {
                        List<?> path = (List<?>) arr.get(0);
                        if (path.size() > depth) {
                            results.add(List.of(path.subList(depth, path.size()), arr.get(1)));
                        }
                    }
                }
                return results;
            }

            default:
                return null;
        }
    }

    // ============================================================================
    // Array Builtins
    // ============================================================================

    @SuppressWarnings("unchecked")
    private static List<Object> evalArrayBuiltin(Object value, String name, List<AstNode> args,
                                                 EvalContext ctx, Evaluator.EvaluateCallback eval) {
        switch (name) {
            case "sort":
                if (value instanceof List) {
                    List<Object> copy = new ArrayList<>((List<Object>) value);
                    copy.sort(ValueOperations::compareJq);
                    return List.of(copy);
                }
                return List.of((Object) null);

            case "sort_by": {
                if (!(value instanceof List) || args.isEmpty()) return List.of((Object) null);
                List<Object> copy = new ArrayList<>((List<Object>) value);
                copy.sort((a, b) -> {
                    Object aKey = eval.evaluate(a, args.get(0), ctx).get(0);
                    Object bKey = eval.evaluate(b, args.get(0), ctx).get(0);
                    return ValueOperations.compareJq(aKey, bKey);
                });
                return List.of(copy);
            }

            case "bsearch": {
                if (!(value instanceof List)) {
                    String typeName = value == null ? "null" : (value instanceof Map ? "object" : value.getClass().getSimpleName().toLowerCase());
                    throw new RuntimeException(typeName + " (" + toJson(value) + ") cannot be searched from");
                }
                if (args.isEmpty()) return List.of((Object) null);
                List<Object> targets = eval.evaluate(value, args.get(0), ctx);
                List<Object> results = new ArrayList<>();
                List<Object> list = (List<Object>) value;
                for (Object target : targets) {
                    int lo = 0, hi = list.size();
                    while (lo < hi) {
                        int mid = (lo + hi) >>> 1;
                        if (ValueOperations.compareJq(list.get(mid), target) < 0) {
                            lo = mid + 1;
                        } else {
                            hi = mid;
                        }
                    }
                    if (lo < list.size() && ValueOperations.compareJq(list.get(lo), target) == 0) {
                        results.add((double) lo);
                    } else {
                        results.add((double) (-lo - 1));
                    }
                }
                return results;
            }

            case "unique_by": {
                if (!(value instanceof List) || args.isEmpty()) return List.of((Object) null);
                Map<String, Object> seen = new LinkedHashMap<>();
                Map<String, Object> keyMap = new LinkedHashMap<>();
                for (Object item : (List<?>) value) {
                    Object keyVal = eval.evaluate(item, args.get(0), ctx).get(0);
                    String keyStr = toJson(keyVal);
                    if (!seen.containsKey(keyStr)) {
                        seen.put(keyStr, item);
                        keyMap.put(keyStr, keyVal);
                    }
                }
                List<Map.Entry<String, Object>> entries = new ArrayList<>(keyMap.entrySet());
                entries.sort((a, b) -> ValueOperations.compareJq(a.getValue(), b.getValue()));
                List<Object> result = new ArrayList<>();
                for (Map.Entry<String, Object> e : entries) {
                    result.add(seen.get(e.getKey()));
                }
                return List.of(result);
            }

            case "group_by": {
                if (!(value instanceof List) || args.isEmpty()) return List.of((Object) null);
                Map<String, List<Object>> groups = new LinkedHashMap<>();
                for (Object item : (List<?>) value) {
                    Object key = eval.evaluate(item, args.get(0), ctx).get(0);
                    String keyStr = toJson(key);
                    groups.computeIfAbsent(keyStr, k -> new ArrayList<>()).add(item);
                }
                return List.of(new ArrayList<>(groups.values()));
            }

            case "max": {
                if (value instanceof List && !((List<?>) value).isEmpty()) {
                    List<Object> list = (List<Object>) value;
                    Object max = list.get(0);
                    for (int i = 1; i < list.size(); i++) {
                        if (ValueOperations.compareJq(list.get(i), max) > 0) max = list.get(i);
                    }
                    return List.of(max);
                }
                return List.of((Object) null);
            }

            case "max_by": {
                if (!(value instanceof List) || ((List<?>) value).isEmpty() || args.isEmpty()) return List.of((Object) null);
                List<Object> list = (List<Object>) value;
                Object max = list.get(0);
                Object maxKey = eval.evaluate(max, args.get(0), ctx).get(0);
                for (int i = 1; i < list.size(); i++) {
                    Object key = eval.evaluate(list.get(i), args.get(0), ctx).get(0);
                    if (ValueOperations.compareJq(key, maxKey) > 0) {
                        max = list.get(i);
                        maxKey = key;
                    }
                }
                return List.of(max);
            }

            case "min": {
                if (value instanceof List && !((List<?>) value).isEmpty()) {
                    List<Object> list = (List<Object>) value;
                    Object min = list.get(0);
                    for (int i = 1; i < list.size(); i++) {
                        if (ValueOperations.compareJq(list.get(i), min) < 0) min = list.get(i);
                    }
                    return List.of(min);
                }
                return List.of((Object) null);
            }

            case "min_by": {
                if (!(value instanceof List) || ((List<?>) value).isEmpty() || args.isEmpty()) return List.of((Object) null);
                List<Object> list = (List<Object>) value;
                Object min = list.get(0);
                Object minKey = eval.evaluate(min, args.get(0), ctx).get(0);
                for (int i = 1; i < list.size(); i++) {
                    Object key = eval.evaluate(list.get(i), args.get(0), ctx).get(0);
                    if (ValueOperations.compareJq(key, minKey) < 0) {
                        min = list.get(i);
                        minKey = key;
                    }
                }
                return List.of(min);
            }

            case "add": {
                if (args.size() >= 1) {
                    List<Object> collected = eval.evaluate(value, args.get(0), ctx);
                    return List.of(addValues(collected));
                }
                if (value instanceof List) {
                    return List.of(addValues((List<Object>) value));
                }
                return List.of((Object) null);
            }

            case "any": {
                if (args.size() >= 2) {
                    try {
                        List<Object> genValues = eval.evaluate(value, args.get(0), ctx);
                        for (Object v : genValues) {
                            List<Object> cond = eval.evaluate(v, args.get(1), ctx);
                            if (cond.stream().anyMatch(ValueOperations::isTruthy)) return List.of(true);
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                    return List.of(false);
                }
                if (args.size() == 1) {
                    if (value instanceof List) {
                        for (Object item : (List<?>) value) {
                            if (ValueOperations.isTruthy(eval.evaluate(item, args.get(0), ctx).get(0))) return List.of(true);
                        }
                        return List.of(false);
                    }
                    return List.of(false);
                }
                if (value instanceof List) {
                    for (Object item : (List<?>) value) {
                        if (ValueOperations.isTruthy(item)) return List.of(true);
                    }
                    return List.of(false);
                }
                return List.of(false);
            }

            case "all": {
                if (args.size() >= 2) {
                    try {
                        List<Object> genValues = eval.evaluate(value, args.get(0), ctx);
                        for (Object v : genValues) {
                            List<Object> cond = eval.evaluate(v, args.get(1), ctx);
                            if (!cond.stream().anyMatch(ValueOperations::isTruthy)) return List.of(false);
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                    return List.of(true);
                }
                if (args.size() == 1) {
                    if (value instanceof List) {
                        for (Object item : (List<?>) value) {
                            if (!ValueOperations.isTruthy(eval.evaluate(item, args.get(0), ctx).get(0))) return List.of(false);
                        }
                        return List.of(true);
                    }
                    return List.of(true);
                }
                if (value instanceof List) {
                    for (Object item : (List<?>) value) {
                        if (!ValueOperations.isTruthy(item)) return List.of(false);
                    }
                    return List.of(true);
                }
                return List.of(true);
            }

            case "select": {
                if (args.isEmpty()) return List.of(value);
                List<Object> conds = eval.evaluate(value, args.get(0), ctx);
                return conds.stream().anyMatch(ValueOperations::isTruthy) ? List.of(value) : List.of();
            }

            case "map": {
                if (args.isEmpty() || !(value instanceof List)) return List.of((Object) null);
                List<Object> results = new ArrayList<>();
                for (Object item : (List<?>) value) {
                    results.addAll(eval.evaluate(item, args.get(0), ctx));
                }
                return List.of(results);
            }

            case "map_values": {
                if (args.isEmpty()) return List.of((Object) null);
                if (value instanceof List) {
                    List<Object> results = new ArrayList<>();
                    for (Object item : (List<?>) value) {
                        results.addAll(eval.evaluate(item, args.get(0), ctx));
                    }
                    return List.of(results);
                }
                if (value instanceof Map) {
                    Map<String, Object> result = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                        String k = e.getKey().toString();
                        if (!SafeObject.isSafeKey(k)) continue;
                        List<Object> mapped = eval.evaluate(e.getValue(), args.get(0), ctx);
                        if (!mapped.isEmpty()) SafeObject.safeSet(result, k, mapped.get(0));
                    }
                    return List.of(result);
                }
                return List.of((Object) null);
            }

            case "has": {
                if (args.isEmpty()) return List.of(false);
                Object key = eval.evaluate(value, args.get(0), ctx).get(0);
                if (value instanceof List && key instanceof Number) {
                    int idx = ((Number) key).intValue();
                    return List.of(idx >= 0 && idx < ((List<?>) value).size());
                }
                if (value instanceof Map && key instanceof String) {
                    return List.of(((Map<?, ?>) value).containsKey(key));
                }
                return List.of(false);
            }

            case "in": {
                if (args.isEmpty()) return List.of(false);
                Object obj = eval.evaluate(value, args.get(0), ctx).get(0);
                if (obj instanceof List && value instanceof Number) {
                    int idx = ((Number) value).intValue();
                    return List.of(idx >= 0 && idx < ((List<?>) obj).size());
                }
                if (obj instanceof Map && value instanceof String) {
                    return List.of(((Map<?, ?>) obj).containsKey(value));
                }
                return List.of(false);
            }

            case "contains": {
                if (args.isEmpty()) return List.of(false);
                Object other = eval.evaluate(value, args.get(0), ctx).get(0);
                return List.of(ValueOperations.containsDeep(value, other));
            }

            case "inside": {
                if (args.isEmpty()) return List.of(false);
                Object other = eval.evaluate(value, args.get(0), ctx).get(0);
                return List.of(ValueOperations.containsDeep(other, value));
            }

            default:
                return null;
        }
    }

    // ============================================================================
    // String Builtins
    // ============================================================================

    private static List<Object> evalStringBuiltin(Object value, String name, List<AstNode> args,
                                                  EvalContext ctx, Evaluator.EvaluateCallback eval) {
        switch (name) {
            case "join": {
                if (!(value instanceof List)) return List.of((Object) null);
                List<?> list = (List<?>) value;
                for (Object x : list) {
                    if (x instanceof List || (x instanceof Map && !(x instanceof List))) {
                        throw new RuntimeException("cannot join: contains arrays or objects");
                    }
                }
                List<Object> seps = args.isEmpty() ? List.of("") : eval.evaluate(value, args.get(0), ctx);
                List<Object> results = new ArrayList<>();
                for (Object sep : seps) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < list.size(); i++) {
                        if (i > 0) sb.append(sep == null ? "" : sep.toString());
                        Object x = list.get(i);
                        sb.append(x == null ? "" : (x instanceof String ? (String) x : x.toString()));
                    }
                    results.add(sb.toString());
                }
                return results;
            }

            case "split": {
                if (!(value instanceof String) || args.isEmpty()) return List.of((Object) null);
                Object sep = eval.evaluate(value, args.get(0), ctx).get(0);
                String sepStr = sep == null ? "" : sep.toString();
                String[] parts = ((String) value).split(java.util.regex.Pattern.quote(sepStr), -1);
                return List.of(new ArrayList<>(Arrays.asList(parts)));
            }

            case "ascii_downcase":
                if (value instanceof String) return List.of(((String) value).toLowerCase());
                return List.of((Object) null);

            case "ascii_upcase":
                if (value instanceof String) return List.of(((String) value).toUpperCase());
                return List.of((Object) null);

            case "ltrimstr": {
                if (!(value instanceof String) || args.isEmpty()) return List.of(value);
                Object prefix = eval.evaluate(value, args.get(0), ctx).get(0);
                String p = prefix == null ? "" : prefix.toString();
                String s = (String) value;
                return List.of(s.startsWith(p) ? s.substring(p.length()) : s);
            }

            case "rtrimstr": {
                if (!(value instanceof String) || args.isEmpty()) return List.of(value);
                Object suffix = eval.evaluate(value, args.get(0), ctx).get(0);
                String s = (String) value;
                String suf = suffix == null ? "" : suffix.toString();
                if (suf.isEmpty()) return List.of(s);
                return List.of(s.endsWith(suf) ? s.substring(0, s.length() - suf.length()) : s);
            }

            case "trim":
                if (value instanceof String) return List.of(((String) value).trim());
                throw new RuntimeException("trim input must be a string");

            case "ltrim":
                if (value instanceof String) return List.of(((String) value).stripLeading());
                throw new RuntimeException("trim input must be a string");

            case "rtrim":
                if (value instanceof String) return List.of(((String) value).stripTrailing());
                throw new RuntimeException("trim input must be a string");

            case "startswith": {
                if (!(value instanceof String) || args.isEmpty()) return List.of(false);
                Object prefix = eval.evaluate(value, args.get(0), ctx).get(0);
                return List.of(((String) value).startsWith(prefix == null ? "" : prefix.toString()));
            }

            case "endswith": {
                if (!(value instanceof String) || args.isEmpty()) return List.of(false);
                Object suffix = eval.evaluate(value, args.get(0), ctx).get(0);
                return List.of(((String) value).endsWith(suffix == null ? "" : suffix.toString()));
            }

            case "ascii":
                if (value instanceof String && !((String) value).isEmpty()) {
                    return List.of((double) ((String) value).charAt(0));
                }
                return List.of((Object) null);

            case "explode": {
                if (value instanceof String) {
                    List<Object> codes = new ArrayList<>();
                    for (int cp : ((String) value).codePoints().toArray()) {
                        codes.add((double) cp);
                    }
                    return List.of(codes);
                }
                return List.of((Object) null);
            }

            case "implode": {
                if (!(value instanceof List)) throw new RuntimeException("implode input must be an array");
                List<?> list = (List<?>) value;
                StringBuilder sb = new StringBuilder();
                for (Object cp : list) {
                    if (cp instanceof String) {
                        throw new RuntimeException("string (\"" + cp + "\") can't be imploded, unicode codepoint needs to be numeric");
                    }
                    if (!(cp instanceof Number) || Double.isNaN(((Number) cp).doubleValue())) {
                        throw new RuntimeException("number (null) can't be imploded, unicode codepoint needs to be numeric");
                    }
                    int code = (int) ((Number) cp).longValue();
                    if (code < 0 || code > 0x10FFFF || (code >= 0xD800 && code <= 0xDFFF)) {
                        sb.appendCodePoint(0xFFFD);
                    } else {
                        sb.appendCodePoint(code);
                    }
                }
                return List.of(sb.toString());
            }

            default:
                return null;
        }
    }

    // ============================================================================
    // Index Builtins
    // ============================================================================

    private static List<Object> evalIndexBuiltin(Object value, String name, List<AstNode> args,
                                                 EvalContext ctx, Evaluator.EvaluateCallback eval) {
        switch (name) {
            case "index": {
                if (args.isEmpty()) return List.of((Object) null);
                List<Object> needles = eval.evaluate(value, args.get(0), ctx);
                List<Object> results = new ArrayList<>();
                for (Object needle : needles) {
                    if (value instanceof String && needle instanceof String) {
                        String s = (String) value;
                        String n = (String) needle;
                        if (n.isEmpty() && s.isEmpty()) {
                            results.add(null);
                        } else {
                            int idx = s.indexOf(n);
                            results.add(idx >= 0 ? (double) idx : null);
                        }
                    } else if (value instanceof List) {
                        List<?> list = (List<?>) value;
                        boolean found = false;
                        if (needle instanceof List) {
                            List<?> needleList = (List<?>) needle;
                            for (int i = 0; i <= list.size() - needleList.size(); i++) {
                                boolean match = true;
                                for (int j = 0; j < needleList.size(); j++) {
                                    if (!ValueOperations.deepEqual(list.get(i + j), needleList.get(j))) {
                                        match = false;
                                        break;
                                    }
                                }
                                if (match) {
                                    results.add((double) i);
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) results.add(null);
                        } else {
                            for (int i = 0; i < list.size(); i++) {
                                if (ValueOperations.deepEqual(list.get(i), needle)) {
                                    results.add((double) i);
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) results.add(null);
                        }
                    } else {
                        results.add(null);
                    }
                }
                return results;
            }

            case "rindex": {
                if (args.isEmpty()) return List.of((Object) null);
                List<Object> needles = eval.evaluate(value, args.get(0), ctx);
                List<Object> results = new ArrayList<>();
                for (Object needle : needles) {
                    if (value instanceof String && needle instanceof String) {
                        int idx = ((String) value).lastIndexOf((String) needle);
                        results.add(idx >= 0 ? (double) idx : null);
                    } else if (value instanceof List) {
                        List<?> list = (List<?>) value;
                        if (needle instanceof List) {
                            List<?> needleList = (List<?>) needle;
                            boolean found = false;
                            for (int i = list.size() - needleList.size(); i >= 0; i--) {
                                boolean match = true;
                                for (int j = 0; j < needleList.size(); j++) {
                                    if (!ValueOperations.deepEqual(list.get(i + j), needleList.get(j))) {
                                        match = false;
                                        break;
                                    }
                                }
                                if (match) {
                                    results.add((double) i);
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) results.add(null);
                        } else {
                            boolean found = false;
                            for (int i = list.size() - 1; i >= 0; i--) {
                                if (ValueOperations.deepEqual(list.get(i), needle)) {
                                    results.add((double) i);
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) results.add(null);
                        }
                    } else {
                        results.add(null);
                    }
                }
                return results;
            }

            case "indices": {
                if (args.isEmpty()) return List.of(List.of());
                List<Object> needles = eval.evaluate(value, args.get(0), ctx);
                List<Object> results = new ArrayList<>();
                for (Object needle : needles) {
                    List<Object> result = new ArrayList<>();
                    if (value instanceof String && needle instanceof String) {
                        String s = (String) value;
                        String n = (String) needle;
                        int idx = s.indexOf(n);
                        while (idx != -1) {
                            result.add((double) idx);
                            idx = s.indexOf(n, idx + 1);
                        }
                    } else if (value instanceof List) {
                        List<?> list = (List<?>) value;
                        if (needle instanceof List) {
                            List<?> needleList = (List<?>) needle;
                            if (needleList.isEmpty()) {
                                for (int i = 0; i <= list.size(); i++) result.add((double) i);
                            } else {
                                for (int i = 0; i <= list.size() - needleList.size(); i++) {
                                    boolean match = true;
                                    for (int j = 0; j < needleList.size(); j++) {
                                        if (!ValueOperations.deepEqual(list.get(i + j), needleList.get(j))) {
                                            match = false;
                                            break;
                                        }
                                    }
                                    if (match) result.add((double) i);
                                }
                            }
                        } else {
                            for (int i = 0; i < list.size(); i++) {
                                if (ValueOperations.deepEqual(list.get(i), needle)) result.add((double) i);
                            }
                        }
                    }
                    results.add(result);
                }
                return results;
            }

            default:
                return null;
        }
    }

    // ============================================================================
    // Control Builtins
    // ============================================================================

    private static List<Object> evalControlBuiltin(Object value, String name, List<AstNode> args,
                                                   EvalContext ctx, Evaluator.EvaluateCallback eval) {
        switch (name) {
            case "first":
                if (!args.isEmpty()) {
                    try {
                        List<Object> results = eval.evaluate(value, args.get(0), ctx);
                        return !results.isEmpty() ? List.of(results.get(0)) : List.of();
                    } catch (Exception e) {
                        return List.of();
                    }
                }
                if (value instanceof List && !((List<?>) value).isEmpty()) return List.of(((List<?>) value).get(0));
                return List.of((Object) null);

            case "last":
                if (!args.isEmpty()) {
                    List<Object> results = eval.evaluate(value, args.get(0), ctx);
                    return !results.isEmpty() ? List.of(results.get(results.size() - 1)) : List.of();
                }
                if (value instanceof List && !((List<?>) value).isEmpty()) {
                    return List.of(((List<?>) value).get(((List<?>) value).size() - 1));
                }
                return List.of((Object) null);

            case "nth": {
                if (args.isEmpty()) return List.of((Object) null);
                List<Object> ns = eval.evaluate(value, args.get(0), ctx);
                if (args.size() > 1) {
                    List<Object> results;
                    try {
                        results = eval.evaluate(value, args.get(1), ctx);
                    } catch (Exception e) {
                        results = List.of();
                    }
                    List<Object> out = new ArrayList<>();
                    for (Object nv : ns) {
                        if (!(nv instanceof Number)) continue;
                        int n = ((Number) nv).intValue();
                        if (n < 0) throw new RuntimeException("nth doesn't support negative indices");
                        if (n < results.size()) out.add(results.get(n));
                    }
                    return out;
                }
                if (value instanceof List) {
                    List<Object> out = new ArrayList<>();
                    for (Object nv : ns) {
                        if (!(nv instanceof Number)) continue;
                        int n = ((Number) nv).intValue();
                        if (n < 0) throw new RuntimeException("nth doesn't support negative indices");
                        List<?> list = (List<?>) value;
                        out.add(n < list.size() ? list.get(n) : null);
                    }
                    return out;
                }
                return List.of((Object) null);
            }

            case "range": {
                if (args.isEmpty()) return List.of();
                int maxRange = Math.max(ctx.limits().maxIterations() * 100, 1_000_000);
                List<Object> startsVals = eval.evaluate(value, args.get(0), ctx);
                if (args.size() == 1) {
                    List<Object> result = new ArrayList<>();
                    for (Object n : startsVals) {
                        if (!(n instanceof Number)) continue;
                        int num = ((Number) n).intValue();
                        for (int i = 0; i < num; i++) {
                            result.add((double) i);
                            if (result.size() >= maxRange) return result;
                        }
                    }
                    return result;
                }
                List<Object> endsVals = eval.evaluate(value, args.get(1), ctx);
                if (args.size() == 2) {
                    List<Object> result = new ArrayList<>();
                    for (Object s : startsVals) {
                        for (Object e : endsVals) {
                            if (!(s instanceof Number) || !(e instanceof Number)) continue;
                            int start = ((Number) s).intValue();
                            int end = ((Number) e).intValue();
                            for (int i = start; i < end; i++) {
                                result.add((double) i);
                                if (result.size() >= maxRange) return result;
                            }
                        }
                    }
                    return result;
                }
                List<Object> stepsVals = eval.evaluate(value, args.get(2), ctx);
                List<Object> result = new ArrayList<>();
                for (Object s : startsVals) {
                    for (Object e : endsVals) {
                        for (Object st : stepsVals) {
                            if (!(s instanceof Number) || !(e instanceof Number) || !(st instanceof Number)) continue;
                            int start = ((Number) s).intValue();
                            int end = ((Number) e).intValue();
                            int step = ((Number) st).intValue();
                            if (step == 0) continue;
                            if (step > 0) {
                                for (int i = start; i < end; i += step) {
                                    result.add((double) i);
                                    if (result.size() >= maxRange) return result;
                                }
                            } else {
                                for (int i = start; i > end; i += step) {
                                    result.add((double) i);
                                    if (result.size() >= maxRange) return result;
                                }
                            }
                        }
                    }
                }
                return result;
            }

            case "limit": {
                if (args.size() < 2) return List.of();
                List<Object> ns = eval.evaluate(value, args.get(0), ctx);
                List<Object> out = new ArrayList<>();
                for (Object nv : ns) {
                    if (!(nv instanceof Number)) continue;
                    int n = ((Number) nv).intValue();
                    if (n < 0) throw new RuntimeException("limit doesn't support negative count");
                    if (n == 0) continue;
                    List<Object> results;
                    try {
                        results = eval.evaluate(value, args.get(1), ctx);
                    } catch (Exception e) {
                        results = List.of();
                    }
                    int count = 0;
                    for (Object result : results) {
                        if (result instanceof LazySequence lazy) {
                            for (Object item : lazy) {
                                if (count >= n) break;
                                out.add(item);
                                count++;
                            }
                        } else {
                            if (count >= n) break;
                            out.add(result);
                            count++;
                        }
                    }
                }
                return out;
            }

            case "isempty": {
                if (args.isEmpty()) return List.of(true);
                try {
                    List<Object> results = eval.evaluate(value, args.get(0), ctx);
                    return List.of(results.isEmpty());
                } catch (Exception e) {
                    return List.of(true);
                }
            }

            case "isvalid": {
                if (args.isEmpty()) return List.of(true);
                try {
                    List<Object> results = eval.evaluate(value, args.get(0), ctx);
                    return List.of(!results.isEmpty());
                } catch (Exception e) {
                    return List.of(false);
                }
            }

            case "skip": {
                if (args.size() < 2) return List.of();
                List<Object> ns = eval.evaluate(value, args.get(0), ctx);
                List<Object> out = new ArrayList<>();
                for (Object nv : ns) {
                    if (!(nv instanceof Number)) continue;
                    int n = ((Number) nv).intValue();
                    if (n < 0) throw new RuntimeException("skip doesn't support negative count");
                    List<Object> results = eval.evaluate(value, args.get(1), ctx);
                    out.addAll(results.subList(Math.min(n, results.size()), results.size()));
                }
                return out;
            }

            case "until": {
                if (args.size() < 2) return List.of(value);
                Object current = value;
                int maxIterations = ctx.limits().maxIterations();
                for (int i = 0; i < maxIterations; i++) {
                    List<Object> conds = eval.evaluate(current, args.get(0), ctx);
                    if (conds.stream().anyMatch(ValueOperations::isTruthy)) return List.of(current);
                    List<Object> next = eval.evaluate(current, args.get(1), ctx);
                    if (next.isEmpty()) return List.of(current);
                    current = next.get(0);
                }
                return List.of(current);
            }

            case "while": {
                if (args.size() < 2) return List.of(value);
                List<Object> results = new ArrayList<>();
                Object current = value;
                int maxIterations = ctx.limits().maxIterations();
                for (int i = 0; i < maxIterations; i++) {
                    List<Object> conds = eval.evaluate(current, args.get(0), ctx);
                    if (!conds.stream().anyMatch(ValueOperations::isTruthy)) break;
                    results.add(current);
                    List<Object> next = eval.evaluate(current, args.get(1), ctx);
                    if (next.isEmpty()) break;
                    current = next.get(0);
                }
                return results;
            }

            case "repeat": {
                if (args.isEmpty()) return List.of(value);
                return List.of(new LazySequence(value, args.get(0), ctx, eval));
            }

            default:
                return null;
        }
    }

    // ============================================================================
    // Path Builtins
    // ============================================================================

    @SuppressWarnings("unchecked")
    private static List<Object> evalPathBuiltin(Object value, String name, List<AstNode> args,
                                                EvalContext ctx, Evaluator.EvaluateCallback eval) {
        switch (name) {
            case "getpath": {
                if (args.isEmpty()) return List.of((Object) null);
                List<Object> paths = eval.evaluate(value, args.get(0), ctx);
                List<Object> results = new ArrayList<>();
                for (Object pathVal : paths) {
                    if (!(pathVal instanceof List)) {
                        results.add(null);
                        continue;
                    }
                    List<?> path = (List<?>) pathVal;
                    Object current = value;
                    for (Object key : path) {
                        if (current == null) {
                            current = null;
                            break;
                        }
                        if (current instanceof List && key instanceof Number) {
                            int idx = ((Number) key).intValue();
                            List<?> list = (List<?>) current;
                            current = idx >= 0 && idx < list.size() ? list.get(idx) : null;
                        } else if (key instanceof String) {
                            Map<String, Object> obj = SafeObject.asQueryRecord(current);
                            if (obj != null && obj.containsKey(key)) {
                                current = obj.get(key);
                            } else {
                                current = null;
                            }
                        } else {
                            current = null;
                        }
                    }
                    results.add(current);
                }
                return results;
            }

            case "setpath": {
                if (args.size() < 2) return List.of((Object) null);
                List<Object> paths = eval.evaluate(value, args.get(0), ctx);
                List<Object> vals = eval.evaluate(value, args.get(1), ctx);
                List<?> path = paths.get(0) instanceof List ? (List<?>) paths.get(0) : List.of();
                Object newVal = vals.isEmpty() ? null : vals.get(0);
                return List.of(PathOperations.setPath(value, (List<Object>) path, newVal));
            }

            case "delpaths": {
                if (args.isEmpty()) return List.of(value);
                List<Object> pathLists = eval.evaluate(value, args.get(0), ctx);
                if (pathLists.isEmpty()) return List.of(value);
                Object pathsVal = pathLists.get(0);
                if (!(pathsVal instanceof List)) return List.of(value);
                Object result = value;
                List<List<Object>> paths = new ArrayList<>();
                for (Object p : (List<?>) pathsVal) {
                    if (p instanceof List) paths.add((List<Object>) p);
                }
                paths.sort((a, b) -> Integer.compare(b.size(), a.size()));
                for (List<Object> path : paths) {
                    result = PathOperations.deletePath(result, path);
                }
                return List.of(result);
            }

            case "path": {
                if (args.isEmpty()) return List.of(List.of());
                List<List<Object>> paths = new ArrayList<>();
                collectPaths(value, args.get(0), ctx, new ArrayList<>(), paths, eval);
                List<Object> result = new ArrayList<>();
                for (List<Object> p : paths) result.add(new ArrayList<>(p));
                return result;
            }

            case "del": {
                if (args.isEmpty()) return List.of(value);
                return List.of(applyDel(value, args.get(0), ctx, eval));
            }

            case "pick": {
                if (args.isEmpty()) return List.of((Object) null);
                List<List<Object>> allPaths = new ArrayList<>();
                for (AstNode arg : args) {
                    collectPaths(value, arg, ctx, new ArrayList<>(), allPaths, eval);
                }
                Object result = null;
                for (List<Object> path : allPaths) {
                    for (Object key : path) {
                        if (key instanceof Number && ((Number) key).intValue() < 0) {
                            throw new RuntimeException("Out of bounds negative array index");
                        }
                    }
                    Object current = value;
                    for (Object key : path) {
                        if (current == null) break;
                        if (current instanceof List && key instanceof Number) {
                            int idx = ((Number) key).intValue();
                            List<?> list = (List<?>) current;
                            current = idx >= 0 && idx < list.size() ? list.get(idx) : null;
                        } else if (key instanceof String) {
                            Map<String, Object> obj = SafeObject.asQueryRecord(current);
                            if (obj != null && obj.containsKey(key)) {
                                current = obj.get(key);
                            } else {
                                current = null;
                            }
                        } else {
                            current = null;
                        }
                    }
                    result = PathOperations.setPath(result, path, current);
                }
                return List.of(result);
            }

            case "paths": {
                List<List<Object>> paths = new ArrayList<>();
                walkPaths(value, new ArrayList<>(), paths);
                if (!args.isEmpty()) {
                    List<Object> filtered = new ArrayList<>();
                    for (List<Object> p : paths) {
                        Object v = value;
                        for (Object key : p) {
                            if (v instanceof List && key instanceof Number) {
                                int idx = ((Number) key).intValue();
                                List<?> list = (List<?>) v;
                                v = idx >= 0 && idx < list.size() ? list.get(idx) : null;
                            } else if (key instanceof String) {
                                Map<String, Object> obj = SafeObject.asQueryRecord(v);
                                if (obj != null && obj.containsKey(key)) {
                                    v = obj.get(key);
                                } else {
                                    v = null;
                                }
                            } else {
                                v = null;
                            }
                        }
                        if (v != null) {
                            List<Object> condResults = eval.evaluate(v, args.get(0), ctx);
                            if (condResults.stream().anyMatch(ValueOperations::isTruthy)) {
                                filtered.add(new ArrayList<>(p));
                            }
                        }
                    }
                    return filtered;
                }
                List<Object> result = new ArrayList<>();
                for (List<Object> p : paths) result.add(new ArrayList<>(p));
                return result;
            }

            case "leaf_paths": {
                List<List<Object>> paths = new ArrayList<>();
                walkLeafPaths(value, new ArrayList<>(), paths);
                List<Object> result = new ArrayList<>();
                for (List<Object> p : paths) result.add(new ArrayList<>(p));
                return result;
            }

            default:
                return null;
        }
    }

    // ============================================================================
    // Navigation Builtins
    // ============================================================================

    private static List<Object> evalNavigationBuiltin(Object value, String name, List<AstNode> args,
                                                      EvalContext ctx, Evaluator.EvaluateCallback eval) {
        switch (name) {
            case "recurse": {
                if (args.isEmpty()) {
                    List<Object> results = new ArrayList<>();
                    walkRecurse(value, results);
                    return results;
                }
                List<Object> results = new ArrayList<>();
                int maxDepth = 10000;
                walkRecurseCond(value, args, ctx, eval, results, 0, maxDepth);
                return results;
            }

            case "recurse_down":
                return evalNavigationBuiltin(value, "recurse", args, ctx, eval);

            case "walk": {
                if (args.isEmpty()) return List.of(value);
                Object result = walkTransform(value, args.get(0), ctx, eval);
                return List.of(result);
            }

            case "transpose": {
                if (!(value instanceof List)) return List.of((Object) null);
                List<?> list = (List<?>) value;
                if (list.isEmpty()) return List.of(List.of());
                int maxLen = 0;
                for (Object row : list) {
                    if (row instanceof List) maxLen = Math.max(maxLen, ((List<?>) row).size());
                }
                List<List<Object>> result = new ArrayList<>();
                for (int i = 0; i < maxLen; i++) {
                    List<Object> col = new ArrayList<>();
                    for (Object row : list) {
                        if (row instanceof List && i < ((List<?>) row).size()) {
                            col.add(((List<?>) row).get(i));
                        } else {
                            col.add(null);
                        }
                    }
                    result.add(col);
                }
                return List.of(result);
            }

            case "combinations": {
                if (!args.isEmpty()) {
                    Object nVal = eval.evaluate(value, args.get(0), ctx).get(0);
                    int n = nVal instanceof Number ? ((Number) nVal).intValue() : 0;
                    if (!(value instanceof List) || n < 0) return List.of();
                    if (n == 0) return List.of(List.of());
                    List<Object> list = (List<Object>) value;
                    List<List<Object>> results = new ArrayList<>();
                    generateCombinations(list, n, new ArrayList<>(), results);
                    List<Object> out = new ArrayList<>();
                    for (List<Object> r : results) out.add(new ArrayList<>(r));
                    return out;
                }
                if (!(value instanceof List)) return List.of();
                List<?> list = (List<?>) value;
                if (list.isEmpty()) return List.of(List.of());
                for (Object arr : list) {
                    if (!(arr instanceof List)) return List.of();
                }
                List<List<Object>> results = new ArrayList<>();
                generateCartesian(list, 0, new ArrayList<>(), results);
                List<Object> out = new ArrayList<>();
                for (List<Object> r : results) out.add(new ArrayList<>(r));
                return out;
            }

            case "parent": {
                if (ctx.root() == null || ctx.currentPath().isEmpty()) return List.of();
                List<Object> path = ctx.currentPath();
                if (path.isEmpty()) return List.of();
                int levels = 1;
                if (!args.isEmpty()) {
                    Object l = eval.evaluate(value, args.get(0), ctx).get(0);
                    if (l instanceof Number) levels = ((Number) l).intValue();
                }
                if (levels >= 0) {
                    if (levels > path.size()) return List.of();
                    List<Object> parentPath = path.subList(0, Math.max(0, path.size() - levels));
                    return List.of(getValueAtPath(ctx.root(), parentPath));
                } else {
                    int targetLen = -levels - 1;
                    if (targetLen >= path.size()) return List.of(value);
                    List<Object> parentPath = path.subList(0, targetLen);
                    return List.of(getValueAtPath(ctx.root(), parentPath));
                }
            }

            case "parents": {
                if (ctx.root() == null || ctx.currentPath().isEmpty()) return List.of(List.of());
                List<Object> path = ctx.currentPath();
                List<Object> parents = new ArrayList<>();
                for (int i = path.size() - 1; i >= 0; i--) {
                    parents.add(getValueAtPath(ctx.root(), path.subList(0, i)));
                }
                return List.of(parents);
            }

            case "root":
                return ctx.root() != null ? List.of(ctx.root()) : List.of();

            default:
                return null;
        }
    }

    // ============================================================================
    // Date Builtins
    // ============================================================================

    private static List<Object> evalDateBuiltin(Object value, String name, List<AstNode> args,
                                                EvalContext ctx, Evaluator.EvaluateCallback eval) {
        switch (name) {
            case "now":
                return List.of(System.currentTimeMillis() / 1000.0);

            case "gmtime": {
                if (!(value instanceof Number)) return List.of((Object) null);
                long ts = (long) (((Number) value).doubleValue() * 1000);
                java.time.Instant instant = java.time.Instant.ofEpochMilli(ts);
                java.time.ZonedDateTime dt = instant.atZone(java.time.ZoneOffset.UTC);
                int yearday = dt.getDayOfYear() - 1;
                return List.of(List.of(
                    (long) dt.getYear(),
                    (long) dt.getMonthValue() - 1,
                    (long) dt.getDayOfMonth(),
                    (long) dt.getHour(),
                    (long) dt.getMinute(),
                    (long) dt.getSecond(),
                    (long) dt.getDayOfWeek().getValue() % 7,
                    (long) yearday
                ));
            }

            case "mktime": {
                if (!(value instanceof List)) throw new RuntimeException("mktime requires parsed datetime inputs");
                List<?> list = (List<?>) value;
                if (list.size() < 2 || !(list.get(0) instanceof Number) || !(list.get(1) instanceof Number)) {
                    throw new RuntimeException("mktime requires parsed datetime inputs");
                }
                int year = ((Number) list.get(0)).intValue();
                int month = ((Number) list.get(1)).intValue();
                int day = list.size() > 2 && list.get(2) instanceof Number ? ((Number) list.get(2)).intValue() : 1;
                int hour = list.size() > 3 && list.get(3) instanceof Number ? ((Number) list.get(3)).intValue() : 0;
                int minute = list.size() > 4 && list.get(4) instanceof Number ? ((Number) list.get(4)).intValue() : 0;
                int second = list.size() > 5 && list.get(5) instanceof Number ? ((Number) list.get(5)).intValue() : 0;
                java.time.Instant instant = java.time.ZonedDateTime.of(
                    year, month + 1, day, hour, minute, second, 0, java.time.ZoneOffset.UTC
                ).toInstant();
                return List.of((double) (instant.toEpochMilli() / 1000));
            }

            case "strftime": {
                if (args.isEmpty()) return List.of((Object) null);
                Object fmtVal = eval.evaluate(value, args.get(0), ctx).get(0);
                if (!(fmtVal instanceof String)) throw new RuntimeException("strftime/1 requires a string format");
                String fmt = (String) fmtVal;
                java.time.ZonedDateTime dt;
                if (value instanceof Number) {
                    dt = java.time.Instant.ofEpochMilli((long) (((Number) value).doubleValue() * 1000))
                        .atZone(java.time.ZoneOffset.UTC);
                } else if (value instanceof List) {
                    List<?> list = (List<?>) value;
                    int year = ((Number) list.get(0)).intValue();
                    int month = ((Number) list.get(1)).intValue();
                    int day = list.size() > 2 && list.get(2) instanceof Number ? ((Number) list.get(2)).intValue() : 1;
                    int hour = list.size() > 3 && list.get(3) instanceof Number ? ((Number) list.get(3)).intValue() : 0;
                    int minute = list.size() > 4 && list.get(4) instanceof Number ? ((Number) list.get(4)).intValue() : 0;
                    int second = list.size() > 5 && list.get(5) instanceof Number ? ((Number) list.get(5)).intValue() : 0;
                    dt = java.time.ZonedDateTime.of(year, month + 1, day, hour, minute, second, 0, java.time.ZoneOffset.UTC);
                } else {
                    throw new RuntimeException("strftime/1 requires parsed datetime inputs");
                }
                String result = fmt
                    .replace("%Y", String.valueOf(dt.getYear()))
                    .replace("%m", String.format("%02d", dt.getMonthValue()))
                    .replace("%d", String.format("%02d", dt.getDayOfMonth()))
                    .replace("%H", String.format("%02d", dt.getHour()))
                    .replace("%M", String.format("%02d", dt.getMinute()))
                    .replace("%S", String.format("%02d", dt.getSecond()))
                    .replace("%A", dt.getDayOfWeek().toString())
                    .replace("%B", dt.getMonth().toString())
                    .replace("%Z", "UTC")
                    .replace("%%", "%");
                return List.of(result);
            }

            case "strptime": {
                if (args.isEmpty()) return List.of((Object) null);
                if (!(value instanceof String)) throw new RuntimeException("strptime/1 requires a string input");
                Object fmtVal = eval.evaluate(value, args.get(0), ctx).get(0);
                if (!(fmtVal instanceof String)) throw new RuntimeException("strptime/1 requires a string format");
                String fmt = (String) fmtVal;
                String s = (String) value;
                if ("%Y-%m-%dT%H:%M:%SZ".equals(fmt)) {
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                        "^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})Z$"
                    );
                    java.util.regex.Matcher m = p.matcher(s);
                    if (m.matches()) {
                        int year = Integer.parseInt(m.group(1));
                        int month = Integer.parseInt(m.group(2)) - 1;
                        int day = Integer.parseInt(m.group(3));
                        int hour = Integer.parseInt(m.group(4));
                        int minute = Integer.parseInt(m.group(5));
                        int second = Integer.parseInt(m.group(6));
                        java.time.Instant instant = java.time.ZonedDateTime.of(
                            year, month + 1, day, hour, minute, second, 0, java.time.ZoneOffset.UTC
                        ).toInstant();
                        java.time.ZonedDateTime dt = instant.atZone(java.time.ZoneOffset.UTC);
                        int yearday = dt.getDayOfYear() - 1;
                        return List.of(List.of(
                            (long) year, (long) month, (long) day,
                            (long) hour, (long) minute, (long) second,
                            (long) (dt.getDayOfWeek().getValue() % 7), (long) yearday
                        ));
                    }
                }
                try {
                    java.time.Instant instant = java.time.Instant.parse(s);
                    java.time.ZonedDateTime dt = instant.atZone(java.time.ZoneOffset.UTC);
                    int yearday = dt.getDayOfYear() - 1;
                    return List.of(List.of(
                        (long) dt.getYear(), (long) (dt.getMonthValue() - 1), (long) dt.getDayOfMonth(),
                        (long) dt.getHour(), (long) dt.getMinute(), (long) dt.getSecond(),
                        (long) (dt.getDayOfWeek().getValue() % 7), (long) yearday
                    ));
                } catch (Exception e) {
                    throw new RuntimeException("Cannot parse date: " + s);
                }
            }

            case "fromdate": {
                if (!(value instanceof String)) throw new RuntimeException("fromdate requires a string input");
                try {
                    java.time.Instant instant = java.time.Instant.parse((String) value);
                    return List.of((double) (instant.toEpochMilli() / 1000));
                } catch (Exception e) {
                    throw new RuntimeException("date \"" + value + "\" does not match format \"%Y-%m-%dT%H:%M:%SZ\"");
                }
            }

            case "todate": {
                if (!(value instanceof Number)) throw new RuntimeException("todate requires a number input");
                long ts = (long) (((Number) value).doubleValue() * 1000);
                java.time.Instant instant = java.time.Instant.ofEpochMilli(ts);
                String s = instant.toString();
                s = s.replaceAll("\\.\\d{3}Z$", "Z");
                return List.of(s);
            }

            default:
                return null;
        }
    }

    // ============================================================================
    // SQL Builtins
    // ============================================================================

    private static List<Object> evalSqlBuiltin(Object value, String name, List<AstNode> args,
                                               EvalContext ctx, Evaluator.EvaluateCallback eval) {
        switch (name) {
            case "IN": {
                if (args.isEmpty()) return List.of(false);
                if (args.size() == 1) {
                    List<Object> streamVals = eval.evaluate(value, args.get(0), ctx);
                    for (Object v : streamVals) {
                        if (ValueOperations.deepEqual(value, v)) return List.of(true);
                    }
                    return List.of(false);
                }
                List<Object> stream1 = eval.evaluate(value, args.get(0), ctx);
                List<Object> stream2 = eval.evaluate(value, args.get(1), ctx);
                Set<String> set2 = new LinkedHashSet<>();
                for (Object v : stream2) set2.add(toJson(v));
                for (Object v : stream1) {
                    if (set2.contains(toJson(v))) return List.of(true);
                }
                return List.of(false);
            }

            case "INDEX": {
                if (args.isEmpty()) return List.of(new LinkedHashMap<String, Object>());
                if (args.size() == 1) {
                    List<Object> streamVals = eval.evaluate(value, args.get(0), ctx);
                    Map<String, Object> result = new LinkedHashMap<>();
                    for (Object v : streamVals) {
                        String key = v == null ? "null" : v.toString();
                        if (SafeObject.isSafeKey(key)) SafeObject.safeSet(result, key, v);
                    }
                    return List.of(result);
                }
                if (args.size() == 2) {
                    List<Object> streamVals = eval.evaluate(value, args.get(0), ctx);
                    Map<String, Object> result = new LinkedHashMap<>();
                    for (Object v : streamVals) {
                        List<Object> keys = eval.evaluate(v, args.get(1), ctx);
                        if (!keys.isEmpty()) {
                            String key = keys.get(0) == null ? "null" : keys.get(0).toString();
                            if (SafeObject.isSafeKey(key)) SafeObject.safeSet(result, key, v);
                        }
                    }
                    return List.of(result);
                }
                List<Object> streamVals = eval.evaluate(value, args.get(0), ctx);
                Map<String, Object> result = new LinkedHashMap<>();
                for (Object v : streamVals) {
                    List<Object> keys = eval.evaluate(v, args.get(1), ctx);
                    List<Object> vals = eval.evaluate(v, args.get(2), ctx);
                    if (!keys.isEmpty() && !vals.isEmpty()) {
                        String key = keys.get(0) == null ? "null" : keys.get(0).toString();
                        if (SafeObject.isSafeKey(key)) SafeObject.safeSet(result, key, vals.get(0));
                    }
                }
                return List.of(result);
            }

            case "JOIN": {
                if (args.size() < 2) return List.of((Object) null);
                Map<String, Object> idxObj = SafeObject.asQueryRecord(eval.evaluate(value, args.get(0), ctx).get(0));
                if (idxObj == null || !(value instanceof List)) return List.of((Object) null);
                List<Object> results = new ArrayList<>();
                for (Object item : (List<?>) value) {
                    List<Object> keys = eval.evaluate(item, args.get(1), ctx);
                    String key = keys.isEmpty() ? "" : (keys.get(0) == null ? "null" : keys.get(0).toString());
                    Object lookup = idxObj.containsKey(key) ? idxObj.get(key) : null;
                    results.add(List.of(item, lookup));
                }
                return List.of(results);
            }

            default:
                return null;
        }
    }

    // ============================================================================
    // Helpers
    // ============================================================================

    private static Object addValues(List<Object> arr) {
        List<Object> filtered = new ArrayList<>();
        for (Object x : arr) if (x != null) filtered.add(x);
        if (filtered.isEmpty()) return null;
        boolean allNumbers = true;
        boolean allStrings = true;
        boolean allArrays = true;
        boolean allObjects = true;
        for (Object x : filtered) {
            if (!(x instanceof Number)) allNumbers = false;
            if (!(x instanceof String)) allStrings = false;
            if (!(x instanceof List)) allArrays = false;
            if (!(x instanceof Map)) allObjects = false;
        }
        if (allNumbers) {
            double sum = 0;
            for (Object x : filtered) sum += ((Number) x).doubleValue();
            return sum;
        }
        if (allStrings) {
            StringBuilder sb = new StringBuilder();
            for (Object x : filtered) sb.append(x);
            return sb.toString();
        }
        if (allArrays) {
            List<Object> result = new ArrayList<>();
            for (Object x : filtered) result.addAll((List<?>) x);
            return result;
        }
        if (allObjects) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Object x : filtered) {
                Map<String, Object> m = SafeObject.asQueryRecord(x);
                if (m != null) result.putAll(m);
            }
            return result;
        }
        return null;
    }

    private static List<Object> flattenList(List<?> list, int depth) {
        List<Object> result = new ArrayList<>();
        for (Object item : list) {
            if (depth > 0 && item instanceof List) {
                result.addAll(flattenList((List<?>) item, depth - 1));
            } else {
                result.add(item);
            }
        }
        return result;
    }

    private static void walkStream(Object value, List<Object> path, List<Object> results) {
        if (value == null || !(value instanceof List || value instanceof Map)) {
            results.add(List.of(new ArrayList<>(path), value));
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.isEmpty()) {
                results.add(List.of(new ArrayList<>(path), List.of()));
            } else {
                for (int i = 0; i < list.size(); i++) {
                    List<Object> newPath = new ArrayList<>(path);
                    newPath.add((double) i);
                    walkStream(list.get(i), newPath, results);
                }
            }
        } else {
            Map<?, ?> map = (Map<?, ?>) value;
            if (map.isEmpty()) {
                results.add(List.of(new ArrayList<>(path), new LinkedHashMap<String, Object>()));
            } else {
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    List<Object> newPath = new ArrayList<>(path);
                    newPath.add(e.getKey().toString());
                    walkStream(e.getValue(), newPath, results);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Object setStreamPath(Object root, List<?> path, Object val) {
        if (path.isEmpty()) return val;
        Object head = path.get(0);
        List<?> rest = path.subList(1, path.size());
        if (root == null) {
            root = head instanceof Number ? new ArrayList<Object>() : new LinkedHashMap<String, Object>();
        }
        if (root instanceof List && head instanceof Number) {
            List<Object> arr = new ArrayList<>((List<Object>) root);
            int idx = ((Number) head).intValue();
            while (arr.size() <= idx) arr.add(null);
            arr.set(idx, setStreamPath(arr.get(idx), rest, val));
            return arr;
        }
        if (root instanceof Map) {
            Map<String, Object> obj = new LinkedHashMap<>((Map<String, Object>) root);
            String key = head.toString();
            Object next = obj.containsKey(key) ? obj.get(key) : null;
            obj.put(key, setStreamPath(next, rest, val));
            return obj;
        }
        return root;
    }

    private static void walkRecurse(Object value, List<Object> results) {
        results.add(value);
        if (value instanceof List) {
            for (Object item : (List<?>) value) walkRecurse(item, results);
        } else if (value instanceof Map) {
            for (Object v : ((Map<?, ?>) value).values()) walkRecurse(v, results);
        }
    }

    private static void walkRecurseCond(Object value, List<AstNode> args, EvalContext ctx,
                                        Evaluator.EvaluateCallback eval, List<Object> results, int depth, int maxDepth) {
        if (depth > maxDepth) return;
        if (args.size() >= 2) {
            List<Object> cond = eval.evaluate(value, args.get(1), ctx);
            if (!cond.stream().anyMatch(ValueOperations::isTruthy)) return;
        }
        results.add(value);
        List<Object> next = eval.evaluate(value, args.get(0), ctx);
        for (Object n : next) {
            if (n != null) walkRecurseCond(n, args, ctx, eval, results, depth + 1, maxDepth);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object walkTransform(Object value, AstNode expr, EvalContext ctx, Evaluator.EvaluateCallback eval) {
        Object transformed;
        if (value instanceof List) {
            List<Object> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                result.add(walkTransform(item, expr, ctx, eval));
            }
            transformed = result;
        } else if (value instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                String k = e.getKey().toString();
                if (SafeObject.isSafeKey(k)) {
                    SafeObject.safeSet(result, k, walkTransform(e.getValue(), expr, ctx, eval));
                }
            }
            transformed = result;
        } else {
            transformed = value;
        }
        List<Object> results = eval.evaluate(transformed, expr, ctx);
        return results.isEmpty() ? null : results.get(0);
    }

    private static void generateCombinations(List<Object> list, int n, List<Object> current, List<List<Object>> results) {
        if (current.size() == n) {
            results.add(new ArrayList<>(current));
            return;
        }
        for (Object item : list) {
            current.add(item);
            generateCombinations(list, n, current, results);
            current.remove(current.size() - 1);
        }
    }

    @SuppressWarnings("unchecked")
    private static void generateCartesian(List<?> lists, int index, List<Object> current, List<List<Object>> results) {
        if (index == lists.size()) {
            results.add(new ArrayList<>(current));
            return;
        }
        for (Object item : (List<?>) lists.get(index)) {
            current.add(item);
            generateCartesian(lists, index + 1, current, results);
            current.remove(current.size() - 1);
        }
    }

    private static Object getValueAtPath(Object root, List<Object> path) {
        Object v = root;
        for (Object key : path) {
            if (v instanceof List && key instanceof Number) {
                int idx = ((Number) key).intValue();
                List<?> list = (List<?>) v;
                v = idx >= 0 && idx < list.size() ? list.get(idx) : null;
            } else if (v instanceof Map && key instanceof String) {
                v = ((Map<?, ?>) v).get(key);
            } else {
                return null;
            }
        }
        return v;
    }

    private static void walkPaths(Object value, List<Object> path, List<List<Object>> paths) {
        if (value instanceof List || value instanceof Map) {
            if (value instanceof List) {
                List<?> list = (List<?>) value;
                for (int i = 0; i < list.size(); i++) {
                    List<Object> newPath = new ArrayList<>(path);
                    newPath.add((double) i);
                    paths.add(newPath);
                    walkPaths(list.get(i), newPath, paths);
                }
            } else {
                Map<?, ?> map = (Map<?, ?>) value;
                for (Object key : map.keySet()) {
                    List<Object> newPath = new ArrayList<>(path);
                    newPath.add(key.toString());
                    paths.add(newPath);
                    walkPaths(map.get(key), newPath, paths);
                }
            }
        }
    }

    private static void walkLeafPaths(Object value, List<Object> path, List<List<Object>> paths) {
        if (value == null || !(value instanceof List || value instanceof Map)) {
            paths.add(new ArrayList<>(path));
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            for (int i = 0; i < list.size(); i++) {
                List<Object> newPath = new ArrayList<>(path);
                newPath.add((double) i);
                walkLeafPaths(list.get(i), newPath, paths);
            }
        } else {
            Map<?, ?> map = (Map<?, ?>) value;
            for (Object key : map.keySet()) {
                List<Object> newPath = new ArrayList<>(path);
                newPath.add(key.toString());
                walkLeafPaths(map.get(key), newPath, paths);
            }
        }
    }

    private static void collectPaths(Object value, AstNode expr, EvalContext ctx,
                                     List<Object> currentPath, List<List<Object>> paths,
                                     Evaluator.EvaluateCallback eval) {
        if (expr instanceof AstNode.CommaNode comma) {
            collectPaths(value, comma.left(), ctx, new ArrayList<>(currentPath), paths, eval);
            collectPaths(value, comma.right(), ctx, new ArrayList<>(currentPath), paths, eval);
            return;
        }
        List<Object> staticPath = extractPathFromAst(expr);
        if (staticPath != null) {
            List<Object> combined = new ArrayList<>(currentPath);
            combined.addAll(staticPath);
            paths.add(combined);
            return;
        }
        if (expr instanceof AstNode.IterateNode) {
            if (value instanceof List) {
                for (int i = 0; i < ((List<?>) value).size(); i++) {
                    List<Object> p = new ArrayList<>(currentPath);
                    p.add((double) i);
                    paths.add(p);
                }
            } else if (value instanceof Map) {
                for (Object key : ((Map<?, ?>) value).keySet()) {
                    List<Object> p = new ArrayList<>(currentPath);
                    p.add(key.toString());
                    paths.add(p);
                }
            }
            return;
        }
        if (expr instanceof AstNode.RecurseNode) {
            walkPaths(value, currentPath, paths);
            return;
        }
        if (expr instanceof AstNode.PipeNode pipe) {
            List<Object> leftPath = extractPathFromAst(pipe.left());
            if (leftPath != null) {
                List<Object> leftResults = eval.evaluate(value, pipe.left(), ctx);
                for (Object lv : leftResults) {
                    List<Object> newPath = new ArrayList<>(currentPath);
                    newPath.addAll(leftPath);
                    collectPaths(lv, pipe.right(), ctx, newPath, paths, eval);
                }
                return;
            }
        }
        List<Object> results = eval.evaluate(value, expr, ctx);
        if (!results.isEmpty()) {
            paths.add(new ArrayList<>(currentPath));
        }
    }

    private static List<Object> extractPathFromAst(AstNode ast) {
        if (ast instanceof AstNode.IdentityNode) return List.of();
        if (ast instanceof AstNode.FieldNode field) {
            List<Object> base = field.base() != null ? extractPathFromAst(field.base()) : List.of();
            if (base == null) return null;
            List<Object> result = new ArrayList<>(base);
            result.add(field.name());
            return result;
        }
        if (ast instanceof AstNode.IndexNode idx && idx.index() instanceof AstNode.LiteralNode lit) {
            List<Object> base = idx.base() != null ? extractPathFromAst(idx.base()) : List.of();
            if (base == null) return null;
            List<Object> result = new ArrayList<>(base);
            if (lit.value() instanceof Number || lit.value() instanceof String) {
                result.add(lit.value());
            } else {
                return null;
            }
            return result;
        }
        if (ast instanceof AstNode.PipeNode pipe) {
            List<Object> leftPath = extractPathFromAst(pipe.left());
            if (leftPath == null) return null;
            return applyPathTransform(leftPath, pipe.right());
        }
        if (ast instanceof AstNode.CallNode call) {
            if ("parent".equals(call.name())) return null;
            if ("root".equals(call.name())) return null;
            if ("first".equals(call.name()) && call.args().isEmpty()) return List.of(0.0);
            if ("last".equals(call.name()) && call.args().isEmpty()) return List.of(-1.0);
        }
        return null;
    }

    private static List<Object> applyPathTransform(List<Object> basePath, AstNode ast) {
        if (ast instanceof AstNode.CallNode call) {
            if ("parent".equals(call.name())) {
                int levels = 1;
                if (!call.args().isEmpty() && call.args().get(0) instanceof AstNode.LiteralNode lit && lit.value() instanceof Number) {
                    levels = ((Number) lit.value()).intValue();
                }
                if (levels >= 0) {
                    return basePath.subList(0, Math.max(0, basePath.size() - levels));
                } else {
                    int targetLen = -levels - 1;
                    return basePath.subList(0, Math.min(targetLen, basePath.size()));
                }
            }
            if ("root".equals(call.name())) return List.of();
        }
        if (ast instanceof AstNode.FieldNode) {
            List<Object> rightPath = extractPathFromAst(ast);
            if (rightPath != null) {
                List<Object> result = new ArrayList<>(basePath);
                result.addAll(rightPath);
                return result;
            }
        }
        if (ast instanceof AstNode.IndexNode idx && idx.index() instanceof AstNode.LiteralNode) {
            List<Object> rightPath = extractPathFromAst(ast);
            if (rightPath != null) {
                List<Object> result = new ArrayList<>(basePath);
                result.addAll(rightPath);
                return result;
            }
        }
        if (ast instanceof AstNode.PipeNode pipe) {
            List<Object> afterLeft = applyPathTransform(basePath, pipe.left());
            if (afterLeft == null) return null;
            return applyPathTransform(afterLeft, pipe.right());
        }
        if (ast instanceof AstNode.IdentityNode) return basePath;
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Object applyDel(Object root, AstNode pathExpr, EvalContext ctx, Evaluator.EvaluateCallback eval) {
        return deleteAt(root, pathExpr, ctx, eval);
    }

    private static Object deleteAt(Object val, AstNode path, EvalContext ctx, Evaluator.EvaluateCallback eval) {
        if (path instanceof AstNode.IdentityNode) return null;

        if (path instanceof AstNode.FieldNode field) {
            if (!SafeObject.isSafeKey(field.name())) return val;
            if (field.base() != null) {
                Object nested = eval.evaluate(val, field.base(), ctx).get(0);
                if (nested == null) return val;
                Object modified = deleteAt(nested, new AstNode.FieldNode(field.name(), null), ctx, eval);
                return setAtPath(val, field.base(), modified, ctx, eval);
            }
            if (val instanceof Map) {
                Map<String, Object> obj = new LinkedHashMap<>((Map<String, Object>) val);
                obj.remove(field.name());
                return obj;
            }
            return val;
        }

        if (path instanceof AstNode.IndexNode idx) {
            if (idx.base() != null) {
                Object nested = eval.evaluate(val, idx.base(), ctx).get(0);
                if (nested == null) return val;
                Object modified = deleteAt(nested, new AstNode.IndexNode(idx.index(), null), ctx, eval);
                return setAtPath(val, idx.base(), modified, ctx, eval);
            }
            List<Object> indices = eval.evaluate(val, idx.index(), ctx);
            Object index = indices.isEmpty() ? null : indices.get(0);
            if (index instanceof Number && val instanceof List) {
                List<Object> arr = new ArrayList<>((List<Object>) val);
                int i = ((Number) index).intValue();
                if (i < 0) i = arr.size() + i;
                if (i >= 0 && i < arr.size()) arr.remove(i);
                return arr;
            }
            if (index instanceof String && val instanceof Map) {
                String strIdx = (String) index;
                if (!SafeObject.isSafeKey(strIdx)) return val;
                Map<String, Object> obj = new LinkedHashMap<>((Map<String, Object>) val);
                obj.remove(strIdx);
                return obj;
            }
            return val;
        }

        if (path instanceof AstNode.IterateNode) {
            if (val instanceof List) return List.of();
            if (val instanceof Map) return new LinkedHashMap<String, Object>();
            return val;
        }

        if (path instanceof AstNode.PipeNode pipe) {
            Object nested = eval.evaluate(val, pipe.left(), ctx).get(0);
            if (nested == null) return val;
            Object modified = deleteAt(nested, pipe.right(), ctx, eval);
            return setAtPath(val, pipe.left(), modified, ctx, eval);
        }

        return val;
    }

    @SuppressWarnings("unchecked")
    private static Object setAtPath(Object obj, AstNode pathNode, Object newVal, EvalContext ctx, Evaluator.EvaluateCallback eval) {
        if (pathNode instanceof AstNode.IdentityNode) return newVal;
        if (pathNode instanceof AstNode.FieldNode field) {
            if (!SafeObject.isSafeKey(field.name())) return obj;
            if (obj instanceof Map) {
                Map<String, Object> result = new LinkedHashMap<>((Map<String, Object>) obj);
                result.put(field.name(), newVal);
                return result;
            }
            return obj;
        }
        if (pathNode instanceof AstNode.IndexNode idx) {
            List<Object> indices = eval.evaluate(obj, idx.index(), ctx);
            Object index = indices.isEmpty() ? null : indices.get(0);
            if (index instanceof Number && obj instanceof List) {
                List<Object> arr = new ArrayList<>((List<Object>) obj);
                int i = ((Number) index).intValue();
                if (i < 0) i = arr.size() + i;
                if (i >= 0 && i < arr.size()) arr.set(i, newVal);
                return arr;
            }
            if (index instanceof String && obj instanceof Map) {
                String strIdx = (String) index;
                if (!SafeObject.isSafeKey(strIdx)) return obj;
                Map<String, Object> result = new LinkedHashMap<>((Map<String, Object>) obj);
                result.put(strIdx, newVal);
                return result;
            }
            return obj;
        }
        if (pathNode instanceof AstNode.PipeNode pipe) {
            Object innerVal = eval.evaluate(obj, pipe.left(), ctx).get(0);
            Object modified = setAtPath(innerVal, pipe.right(), newVal, ctx, eval);
            return setAtPath(obj, pipe.left(), modified, ctx, eval);
        }
        return obj;
    }

    // ============================================================================
    // JSON serialization / parsing helpers
    // ============================================================================

    private static String toJson(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d)) return "null";
            if (Double.isInfinite(d)) return d > 0 ? "1.7976931348623157e+308" : "-1.7976931348623157e+308";
            return value.toString();
        }
        if (value instanceof String) return jsonEscape((String) value);
        if (value instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            List<?> list = (List<?>) value;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        if (value instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            Map<?, ?> map = (Map<?, ?>) value;
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append(jsonEscape(e.getKey().toString())).append(":").append(toJson(e.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        return jsonEscape(value.toString());
    }

    private static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    private static Object parseJson(String json) {
        json = json.trim();
        if (json.equals("null")) return null;
        if (json.equals("true")) return true;
        if (json.equals("false")) return false;
        if (json.startsWith("\"")) {
            StringBuilder sb = new StringBuilder();
            int i = 1;
            while (i < json.length() - 1) {
                char c = json.charAt(i);
                if (c == '\\' && i + 1 < json.length()) {
                    char next = json.charAt(i + 1);
                    switch (next) {
                        case '"': sb.append('"'); i += 2; continue;
                        case '\\': sb.append('\\'); i += 2; continue;
                        case '/': sb.append('/'); i += 2; continue;
                        case 'b': sb.append('\b'); i += 2; continue;
                        case 'f': sb.append('\f'); i += 2; continue;
                        case 'n': sb.append('\n'); i += 2; continue;
                        case 'r': sb.append('\r'); i += 2; continue;
                        case 't': sb.append('\t'); i += 2; continue;
                        case 'u':
                            if (i + 5 < json.length()) {
                                String hex = json.substring(i + 2, i + 6);
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 6;
                                continue;
                            }
                    }
                }
                sb.append(c);
                i++;
            }
            return sb.toString();
        }
        if (json.startsWith("[")) {
            List<Object> result = new ArrayList<>();
            int depth = 0;
            StringBuilder current = new StringBuilder();
            for (int i = 1; i < json.length() - 1; i++) {
                char c = json.charAt(i);
                if (c == '[' || c == '{') depth++;
                else if (c == ']' || c == '}') depth--;
                if (c == ',' && depth == 0) {
                    if (current.length() > 0) result.add(parseJson(current.toString().trim()));
                    current = new StringBuilder();
                } else {
                    current.append(c);
                }
            }
            if (current.length() > 0) result.add(parseJson(current.toString().trim()));
            return result;
        }
        if (json.startsWith("{")) {
            Map<String, Object> result = new LinkedHashMap<>();
            int depth = 0;
            StringBuilder current = new StringBuilder();
            String key = null;
            for (int i = 1; i < json.length() - 1; i++) {
                char c = json.charAt(i);
                if (c == '[' || c == '{') depth++;
                else if (c == ']' || c == '}') depth--;
                if (c == ':' && depth == 0 && key == null) {
                    key = (String) parseJson(current.toString().trim());
                    current = new StringBuilder();
                } else if (c == ',' && depth == 0) {
                    if (key != null && current.length() > 0) {
                        result.put(key, parseJson(current.toString().trim()));
                    }
                    key = null;
                    current = new StringBuilder();
                } else {
                    current.append(c);
                }
            }
            if (key != null && current.length() > 0) {
                result.put(key, parseJson(current.toString().trim()));
            }
            return result;
        }
        try {
            return Double.parseDouble(json);
        } catch (NumberFormatException e) {
            return json;
        }
    }
}
