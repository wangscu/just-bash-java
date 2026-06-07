package com.justbash.commands.queryengine;

import java.util.*;
import java.util.function.BiFunction;

/**
 * Query expression evaluator.
 *
 * Evaluates a parsed query AST against any value.
 * Used by jq, yq, and other query-based commands.
 */
public final class Evaluator {

    private static final int DEFAULT_MAX_ITERATIONS = 10000;
    private static final int DEFAULT_MAX_DEPTH = 2000;
    private static final int MAX_ARRAY_INDEX = 536870911;

    private static final Map<String, java.util.function.Function<Double, Double>> SIMPLE_MATH = new LinkedHashMap<>();
    static {
        SIMPLE_MATH.put("floor", Math::floor);
        SIMPLE_MATH.put("ceil", Math::ceil);
        SIMPLE_MATH.put("round", x -> (double) Math.round(x));
        SIMPLE_MATH.put("sqrt", Math::sqrt);
        SIMPLE_MATH.put("log", Math::log);
        SIMPLE_MATH.put("log10", Math::log10);
        SIMPLE_MATH.put("log2", x -> Math.log(x) / Math.log(2));
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
        SIMPLE_MATH.put("asinh", Evaluator::asinh);
        SIMPLE_MATH.put("acosh", Evaluator::acosh);
        SIMPLE_MATH.put("atanh", Evaluator::atanh);
        SIMPLE_MATH.put("cbrt", Math::cbrt);
        SIMPLE_MATH.put("expm1", Math::expm1);
        SIMPLE_MATH.put("log1p", Math::log1p);
        SIMPLE_MATH.put("trunc", x -> x >= 0 ? Math.floor(x) : Math.ceil(x));
    }

    private static double asinh(double x) { return Math.log(x + Math.sqrt(x * x + 1)); }
    private static double acosh(double x) { return Math.log(x + Math.sqrt(x * x - 1)); }
    private static double atanh(double x) { return 0.5 * Math.log((1 + x) / (1 - x)); }

    private Evaluator() {}

    private static List<Object> listOf(Object value) {
        return value == null
            ? new ArrayList<Object>(Collections.singletonList(null))
            : List.of(value);
    }

    private static java.util.stream.Stream<Object> streamOfNull() {
        return new ArrayList<Object>(Collections.singletonList(null)).stream();
    }

    /**
     * Functional interface for recursive evaluation callbacks.
     */
    @FunctionalInterface
    public interface EvaluateCallback {
        List<Object> evaluate(Object value, AstNode ast, EvalContext ctx);
    }

    public static List<Object> evaluate(Object value, AstNode ast) {
        return evaluate(value, ast, EvalContext.create());
    }

    public static List<Object> evaluate(Object value, AstNode ast, EvalContext ctx) {
        // Initialize root if not set
        if (ctx.root() == null) {
            ctx = ctx.withRoot(value).withCurrentPath(List.of());
        }

        List<Object> result = switch (ast) {
            case AstNode.IdentityNode ignored -> listOf(value);

            case AstNode.FieldNode field -> evalField(value, field, ctx);
            case AstNode.IndexNode index -> evalIndex(value, index, ctx);
            case AstNode.SliceNode slice -> evalSlice(value, slice, ctx);
            case AstNode.IterateNode iterate -> evalIterate(value, iterate, ctx);
            case AstNode.PipeNode pipe -> evalPipe(value, pipe, ctx);
            case AstNode.CommaNode comma -> evalComma(value, comma, ctx);
            case AstNode.LiteralNode literal -> {
                Object val = literal.value();
                yield val == null ? new ArrayList<Object>(Collections.singletonList(null)) : List.of(val);
            }
            case AstNode.ArrayNode array -> evalArray(value, array, ctx);
            case AstNode.ObjectNode object -> evalObject(value, object, ctx);
            case AstNode.ParenNode paren -> evaluate(value, paren.expr(), ctx);
            case AstNode.BinaryOpNode binOp -> evalBinaryOp(value, binOp, ctx);
            case AstNode.UnaryOpNode unaryOp -> evalUnaryOp(value, unaryOp, ctx);
            case AstNode.CondNode cond -> evalCond(value, cond, ctx);
            case AstNode.TryNode tryNode -> evalTry(value, tryNode, ctx);
            case AstNode.CallNode call -> evalCall(value, call, ctx);
            case AstNode.VarBindNode varBind -> evalVarBind(value, varBind, ctx);
            case AstNode.VarRefNode varRef -> evalVarRef(value, varRef, ctx);
            case AstNode.RecurseNode ignored -> evalRecurse(value);
            case AstNode.OptionalNode opt -> evalOptional(value, opt, ctx);
            case AstNode.StringInterpNode interp -> evalStringInterp(value, interp, ctx);
            case AstNode.UpdateOpNode update -> evalUpdateOp(value, update, ctx);
            case AstNode.ReduceNode reduce -> evalReduce(value, reduce, ctx);
            case AstNode.ForeachNode foreach -> evalForeach(value, foreach, ctx);
            case AstNode.LabelNode label -> evalLabel(value, label, ctx);
            case AstNode.BreakNode breakNode -> evalBreak(breakNode);
            case AstNode.DefNode def -> evalDef(value, def, ctx);
        };

        // Normalize Long to Double for computed results (matches jq float semantics)
        // LiteralNode is excluded so that literal values preserve their parsed type
        if (ast instanceof AstNode.CallNode || ast instanceof AstNode.BinaryOpNode
            || ast instanceof AstNode.UnaryOpNode || ast instanceof AstNode.ObjectNode
            || ast instanceof AstNode.ArrayNode
            || ast instanceof AstNode.UpdateOpNode || ast instanceof AstNode.ReduceNode
            || ast instanceof AstNode.ForeachNode || ast instanceof AstNode.CondNode
            || ast instanceof AstNode.TryNode || ast instanceof AstNode.OptionalNode
            || ast instanceof AstNode.StringInterpNode || ast instanceof AstNode.ParenNode
            || ast instanceof AstNode.DefNode) {
            return result.stream().map(ValueOperations::normalizeNumbers).toList();
        }
        return result;
    }

    // =========================================================================
    // AST Node Handlers
    // =========================================================================

    private static List<Object> evalField(Object value, AstNode.FieldNode field, EvalContext ctx) {
        List<Object> bases = field.base() != null ? evaluate(value, field.base(), ctx) : listOf(value);
        return bases.stream().flatMap(v -> {
            Map<String, Object> obj = SafeObject.asQueryRecord(v);
            if (obj != null) {
                if (!SafeObject.safeHasOwn(obj, field.name())) {
                    return streamOfNull();
                }
                Object result = obj.get(field.name());
                return listOf(result).stream();
            }
            if (v == null) {
                return streamOfNull();
            }
            String typeName = (v instanceof List) ? "array" : v.getClass().getSimpleName().toLowerCase();
            throw new RuntimeException("Cannot index " + typeName + " with string \"" + field.name() + "\"");
        }).toList();
    }

    private static List<Object> evalIndex(Object value, AstNode.IndexNode index, EvalContext ctx) {
        List<Object> bases = index.base() != null ? evaluate(value, index.base(), ctx) : listOf(value);
        return bases.stream().flatMap(v -> {
            List<Object> indices = evaluate(v, index.index(), ctx);
            return indices.stream().flatMap(idx -> {
                if (idx instanceof Number num && v instanceof List<?> list) {
                    double d = num.doubleValue();
                    if (Double.isNaN(d)) return streamOfNull();
                    int truncated = (int) (d >= 0 ? Math.floor(d) : Math.ceil(d));
                    int i = truncated < 0 ? list.size() + truncated : truncated;
                    return (i >= 0 && i < list.size()) ? listOf(list.get(i)).stream() : streamOfNull();
                }
                if (idx instanceof String s) {
                    Map<String, Object> obj = SafeObject.asQueryRecord(v);
                    if (obj == null || !obj.containsKey(s)) {
                        return streamOfNull();
                    }
                    return listOf(obj.get(s)).stream();
                }
                return streamOfNull();
            });
        }).toList();
    }

    private static List<Object> evalSlice(Object value, AstNode.SliceNode slice, EvalContext ctx) {
        List<Object> bases = slice.base() != null ? evaluate(value, slice.base(), ctx) : listOf(value);
        return bases.stream().flatMap(v -> {
            if (v == null) return streamOfNull();
            if (!(v instanceof List) && !(v instanceof String)) {
                throw new RuntimeException("Cannot slice " + (v == null ? "null" : v.getClass().getSimpleName()));
            }
            int len = (v instanceof List<?> list) ? list.size() : ((String) v).length();
            List<Object> starts = slice.start() != null ? evaluate(value, slice.start(), ctx) : List.of(0);
            List<Object> ends = slice.end() != null ? evaluate(value, slice.end(), ctx) : List.of(len);
            return starts.stream().flatMap(s -> ends.stream().map(e -> {
                double sNum = s instanceof Number ? ((Number) s).doubleValue() : 0;
                double eNum = e instanceof Number ? ((Number) e).doubleValue() : len;
                int startRaw = Double.isNaN(sNum) ? 0 : (Math.floor(sNum) == sNum ? (int) sNum : (int) Math.floor(sNum));
                int endRaw = Double.isNaN(eNum) ? len : (Math.ceil(eNum) == eNum ? (int) eNum : (int) Math.ceil(eNum));
                int start = normalizeIndex(startRaw, len);
                int end = normalizeIndex(endRaw, len);
                if (v instanceof List<?> list) {
                    return (Object) new ArrayList<>(list.subList(start, end));
                } else {
                    return (Object) ((String) v).substring(start, end);
                }
            }));
        }).toList();
    }

    private static int normalizeIndex(int idx, int len) {
        if (idx < 0) return Math.max(0, len + idx);
        return Math.min(idx, len);
    }

    private static List<Object> evalIterate(Object value, AstNode.IterateNode iterate, EvalContext ctx) {
        List<Object> bases = iterate.base() != null ? evaluate(value, iterate.base(), ctx) : listOf(value);
        return bases.stream().flatMap(v -> {
            if (v instanceof List<?> list) return list.stream();
            Map<String, Object> obj = SafeObject.asQueryRecord(v);
            if (obj != null) return obj.values().stream();
            return java.util.stream.Stream.empty();
        }).toList();
    }

    private static List<Object> evalPipe(Object value, AstNode.PipeNode pipe, EvalContext ctx) {
        List<Object> leftResults = evaluate(value, pipe.left(), ctx);
        List<Object> leftPath = extractPathFromAst(pipe.left());
        List<Object> pipeResults = new ArrayList<>();
        for (Object v : leftResults) {
            try {
                if (leftPath != null) {
                    List<Object> newPath = new ArrayList<>(ctx.currentPath());
                    newPath.addAll(leftPath);
                    pipeResults.addAll(evaluate(v, pipe.right(), ctx.withCurrentPath(newPath)));
                } else {
                    pipeResults.addAll(evaluate(v, pipe.right(), ctx));
                }
            } catch (BreakException e) {
                throw e.withPrependedResults(pipeResults);
            }
        }
        return pipeResults;
    }

    private static List<Object> evalComma(Object value, AstNode.CommaNode comma, EvalContext ctx) {
        List<Object> leftResults = evaluate(value, comma.left(), ctx);
        List<Object> rightResults = evaluate(value, comma.right(), ctx);
        List<Object> result = new ArrayList<>(leftResults.size() + rightResults.size());
        result.addAll(leftResults);
        result.addAll(rightResults);
        return result;
    }

    private static List<Object> evalArray(Object value, AstNode.ArrayNode array, EvalContext ctx) {
        if (array.elements() == null) return List.of((Object) new ArrayList<>());
        List<Object> elements = evaluate(value, array.elements(), ctx);
        return List.of((Object) new ArrayList<>(elements));
    }

    @SuppressWarnings("unchecked")
    private static List<Object> evalObject(Object value, AstNode.ObjectNode object, EvalContext ctx) {
        List<Map<String, Object>> results = new ArrayList<>();
        results.add(new LinkedHashMap<>());

        for (AstNode.ObjectNode.ObjectEntry entry : object.entries()) {
            List<Object> keys;
            if (entry.key() instanceof AstNode.LiteralNode lit && lit.value() instanceof String s) {
                keys = List.of(s);
            } else {
                keys = evaluate(value, entry.key(), ctx);
            }
            List<Object> values = evaluate(value, entry.value(), ctx);

            List<Map<String, Object>> newResults = new ArrayList<>();
            for (Map<String, Object> obj : results) {
                for (Object k : keys) {
                    if (!(k instanceof String)) {
                        String typeName = k == null ? "null" : (k instanceof List ? "array" : k.getClass().getSimpleName());
                        throw new RuntimeException("Cannot use " + typeName + " (" + k + ") as object key");
                    }
                    String keyStr = (String) k;
                    if (!SafeObject.isSafeKey(keyStr)) {
                        for (Object ignored : values) {
                            newResults.add(new LinkedHashMap<>(obj));
                        }
                        continue;
                    }
                    for (Object v : values) {
                        Map<String, Object> newObj = new LinkedHashMap<>(obj);
                        SafeObject.safeSet(newObj, keyStr, v);
                        newResults.add(newObj);
                    }
                }
            }
            results = newResults;
        }
        return (List<Object>) (List<?>) results;
    }

    private static List<Object> evalUnaryOp(Object value, AstNode.UnaryOpNode unary, EvalContext ctx) {
        List<Object> operands = evaluate(value, unary.operand(), ctx);
        return operands.stream().map(v -> {
            if ("-".equals(unary.op())) {
                if (v instanceof Number n) return (Object) (-n.doubleValue());
                if (v instanceof String s) {
                    String fmt = s.length() > 5 ? "\"" + s.substring(0, 3) + "..." : "\"" + s + "\"";
                    throw new RuntimeException("string (" + fmt + ") cannot be negated");
                }
                return null;
            }
            if ("not".equals(unary.op())) {
                return !ValueOperations.isTruthy(v);
            }
            return null;
        }).toList();
    }

    private static List<Object> evalCond(Object value, AstNode.CondNode cond, EvalContext ctx) {
        List<Object> conds = evaluate(value, cond.cond(), ctx);
        return conds.stream().flatMap(c -> {
            if (ValueOperations.isTruthy(c)) {
                return evaluate(value, cond.thenBranch(), ctx).stream();
            }
            for (AstNode.CondNode.ElifBranch elif : cond.elifs()) {
                List<Object> elifConds = evaluate(value, elif.cond(), ctx);
                if (elifConds.stream().anyMatch(ValueOperations::isTruthy)) {
                    return evaluate(value, elif.thenBranch(), ctx).stream();
                }
            }
            if (cond.elseBranch() != null) {
                return evaluate(value, cond.elseBranch(), ctx).stream();
            }
            return listOf(value).stream();
        }).toList();
    }

    private static List<Object> evalTry(Object value, AstNode.TryNode tryNode, EvalContext ctx) {
        try {
            return evaluate(value, tryNode.body(), ctx);
        } catch (Exception e) {
            if (tryNode.catchBranch() != null) {
                Object errorVal;
                if (e instanceof JqException je) {
                    errorVal = je.getValue();
                } else {
                    errorVal = e.getMessage();
                }
                return evaluate(errorVal, tryNode.catchBranch(), ctx);
            }
            return List.of();
        }
    }

    private static List<Object> evalVarBind(Object value, AstNode.VarBindNode varBind, EvalContext ctx) {
        List<Object> values = evaluate(value, varBind.value(), ctx);
        return values.stream().flatMap(v -> {
            List<DestructurePattern> patternsToTry = new ArrayList<>();
            if (varBind.pattern() != null) {
                patternsToTry.add(varBind.pattern());
            } else if (varBind.name() != null) {
                patternsToTry.add(new DestructurePattern.VarPattern(varBind.name()));
            }
            if (varBind.alternatives() != null) {
                patternsToTry.addAll(varBind.alternatives());
            }

            EvalContext newCtx = null;
            for (DestructurePattern pattern : patternsToTry) {
                newCtx = bindPattern(ctx, pattern, v);
                if (newCtx != null) break;
            }

            if (newCtx == null) {
                return java.util.stream.Stream.empty();
            }
            return evaluate(value, varBind.body(), newCtx).stream();
        }).toList();
    }

    private static EvalContext bindPattern(EvalContext ctx, DestructurePattern pattern, Object value) {
        return switch (pattern) {
            case DestructurePattern.VarPattern var -> ctx.withVar(var.name(), value);
            case DestructurePattern.ArrayPattern arr -> {
                if (!(value instanceof List<?> list)) yield null;
                EvalContext newCtx = ctx;
                for (int i = 0; i < arr.elements().size(); i++) {
                    Object elemValue = i < list.size() ? list.get(i) : null;
                    EvalContext result = bindPattern(newCtx, arr.elements().get(i), elemValue);
                    if (result == null) yield null;
                    newCtx = result;
                }
                yield newCtx;
            }
            case DestructurePattern.ObjectPattern obj -> {
                Map<String, Object> rec = SafeObject.asQueryRecord(value);
                if (rec == null) yield null;
                EvalContext newCtx = ctx;
                for (DestructurePattern.ObjectPattern.ObjectField field : obj.fields()) {
                    String key;
                    if (field.key() instanceof String s) {
                        key = s;
                    } else if (field.key() instanceof AstNode node) {
                        List<Object> keyVals = evaluate(value, node, ctx);
                        if (keyVals.isEmpty()) yield null;
                        key = String.valueOf(keyVals.get(0));
                    } else {
                        key = String.valueOf(field.key());
                    }
                    Object fieldValue = rec.containsKey(key) ? rec.get(key) : null;
                    if (field.keyVar() != null) {
                        newCtx = newCtx.withVar(field.keyVar(), fieldValue);
                    }
                    EvalContext result = bindPattern(newCtx, field.pattern(), fieldValue);
                    if (result == null) yield null;
                    newCtx = result;
                }
                yield newCtx;
            }
        };
    }

    private static List<Object> evalVarRef(Object value, AstNode.VarRefNode varRef, EvalContext ctx) {
        if ("$ENV".equals(varRef.name())) {
            Map<String, Object> envMap = new LinkedHashMap<>();
            if (ctx.env() != null) {
                for (Map.Entry<String, String> e : ctx.env().entrySet()) {
                    envMap.put(e.getKey(), e.getValue());
                }
            }
            return List.of(envMap);
        }
        Object v = ctx.vars().get(varRef.name());
        return v != null ? List.of(v) : new ArrayList<Object>(Collections.singletonList(null));
    }

    private static List<Object> evalRecurse(Object value) {
        List<Object> results = new ArrayList<>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        walkRecurse(value, results, seen);
        return results;
    }

    @SuppressWarnings("unchecked")
    private static void walkRecurse(Object val, List<Object> results, Set<Object> seen) {
        if (val != null && val instanceof Object) {
            if (!(val instanceof String) && !(val instanceof Number) && !(val instanceof Boolean)) {
                if (seen.contains(val)) return;
                seen.add(val);
            }
        }
        results.add(val);
        if (val instanceof List<?> list) {
            for (Object item : list) walkRecurse(item, results, seen);
        } else {
            Map<String, Object> obj = SafeObject.asQueryRecord(val);
            if (obj != null) {
                for (Object child : obj.values()) walkRecurse(child, results, seen);
            }
        }
    }

    private static List<Object> evalOptional(Object value, AstNode.OptionalNode opt, EvalContext ctx) {
        try {
            return evaluate(value, opt.expr(), ctx);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static List<Object> evalStringInterp(Object value, AstNode.StringInterpNode interp, EvalContext ctx) {
        StringBuilder sb = new StringBuilder();
        for (StringInterpPart part : interp.parts()) {
            if (part instanceof StringPart sp) {
                sb.append(sp.value());
            } else if (part instanceof ExprPart ep) {
                List<Object> vals = evaluate(value, ep.expr(), ctx);
                String str = vals.stream()
                    .map(v -> v instanceof String s ? s : jsonStringify(v))
                    .collect(java.util.stream.Collectors.joining(""));
                sb.append(str);
            }
        }
        return List.of(sb.toString());
    }

    private static String jsonStringify(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return "\"" + s + "\"";
        if (value instanceof Boolean b) return b.toString();
        if (value instanceof Number n) return n.toString();
        // For arrays/objects, we need a simple representation
        // This is a simplified version - full JSON serialization would require a library
        if (value instanceof List<?> list) {
            return list.stream().map(Evaluator::jsonStringify).collect(java.util.stream.Collectors.joining(",", "[", "]"));
        }
        Map<String, Object> obj = SafeObject.asQueryRecord(value);
        if (obj != null) {
            return obj.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + jsonStringify(e.getValue()))
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        }
        return value.toString();
    }

    // =========================================================================
    // Update Operations
    // =========================================================================

    private static List<Object> evalUpdateOp(Object value, AstNode.UpdateOpNode update, EvalContext ctx) {
        return List.of(applyUpdate(value, update.path(), update.op(), update.value(), ctx));
    }

    @SuppressWarnings("unchecked")
    private static Object applyUpdate(Object root, AstNode pathExpr, String op, AstNode valueExpr, EvalContext ctx) {
        java.util.function.Function<Object, Object> transformer = current -> {
            if ("|=".equals(op)) {
                List<Object> results = evaluate(current, valueExpr, ctx);
                return results.isEmpty() ? null : results.get(0);
            }
            List<Object> newVals = evaluate(root, valueExpr, ctx);
            Object newVal = newVals.isEmpty() ? null : newVals.get(0);
            return computeNewValue(current, newVal, op);
        };
        return updateRecursive(root, pathExpr, transformer, ctx);
    }

    private static Object computeNewValue(Object current, Object newVal, String op) {
        return switch (op) {
            case "=" -> newVal;
            case "+=" -> {
                if (current instanceof Number a && newVal instanceof Number b) yield a.doubleValue() + b.doubleValue();
                if (current instanceof String a && newVal instanceof String b) yield a + b;
                if (current instanceof List<?> a && newVal instanceof List<?> b) {
                    List<Object> result = new ArrayList<>(a);
                    result.addAll(b);
                    yield result;
                }
                Map<String, Object> aObj = SafeObject.asQueryRecord(current);
                Map<String, Object> bObj = SafeObject.asQueryRecord(newVal);
                if (aObj != null && bObj != null) yield ValueOperations.deepMerge(aObj, bObj);
                yield newVal;
            }
            case "-=" -> {
                if (current instanceof Number a && newVal instanceof Number b) yield a.doubleValue() - b.doubleValue();
                yield current;
            }
            case "*=" -> {
                if (current instanceof Number a && newVal instanceof Number b) yield a.doubleValue() * b.doubleValue();
                yield current;
            }
            case "/=" -> {
                if (current instanceof Number a && newVal instanceof Number b) yield a.doubleValue() / b.doubleValue();
                yield current;
            }
            case "%=" -> {
                if (current instanceof Number a && newVal instanceof Number b) yield a.doubleValue() % b.doubleValue();
                yield current;
            }
            case "//=" -> (current == null || Boolean.FALSE.equals(current)) ? newVal : current;
            default -> newVal;
        };
    }

    @SuppressWarnings("unchecked")
    private static Object updateRecursive(Object val, AstNode path, java.util.function.Function<Object, Object> transform, EvalContext ctx) {
        return switch (path) {
            case AstNode.IdentityNode ignored -> transform.apply(val);
            case AstNode.FieldNode field -> {
                if (!SafeObject.isSafeKey(field.name())) yield val;
                if (field.base() != null) {
                    yield updateRecursive(val, field.base(), baseVal -> {
                        Map<String, Object> obj = SafeObject.asQueryRecord(baseVal);
                        if (obj != null) {
                            Map<String, Object> copy = new LinkedHashMap<>(obj);
                            Object current = copy.containsKey(field.name()) ? copy.get(field.name()) : null;
                            SafeObject.safeSet(copy, field.name(), transform.apply(current));
                            return copy;
                        }
                        return baseVal;
                    }, ctx);
                }
                Map<String, Object> obj = SafeObject.asQueryRecord(val);
                if (obj != null) {
                    Map<String, Object> copy = new LinkedHashMap<>(obj);
                    Object current = copy.containsKey(field.name()) ? copy.get(field.name()) : null;
                    SafeObject.safeSet(copy, field.name(), transform.apply(current));
                    yield copy;
                }
                yield val;
            }
            case AstNode.IndexNode index -> {
                List<Object> indices = evaluate(val, index.index(), ctx);
                Object idx = indices.isEmpty() ? null : indices.get(0);
                if (idx instanceof Number num && Double.isNaN(num.doubleValue())) {
                    throw new RuntimeException("Cannot set array element at NaN index");
                }
                if (idx instanceof Number num && num.doubleValue() != Math.floor(num.doubleValue())) {
                    double d = num.doubleValue();
                    idx = (d >= 0) ? Math.floor(d) : Math.ceil(d);
                }
                if (index.base() != null) {
                    final Object finalIdx = idx;
                    yield updateRecursive(val, index.base(), baseVal -> {
                        if (finalIdx instanceof Number num && baseVal instanceof List<?> list) {
                            List<Object> arr = new ArrayList<>(list);
                            int i = num.intValue() < 0 ? arr.size() + num.intValue() : num.intValue();
                            if (i >= 0) {
                                while (arr.size() <= i) arr.add(null);
                                arr.set(i, transform.apply(arr.get(i)));
                            }
                            return arr;
                        }
                        if (finalIdx instanceof String s && SafeObject.isSafeKey(s) &&
                            SafeObject.asQueryRecord(baseVal) != null) {
                            Map<String, Object> obj = new LinkedHashMap<>(SafeObject.asQueryRecord(baseVal));
                            Object current = obj.containsKey(s) ? obj.get(s) : null;
                            SafeObject.safeSet(obj, s, transform.apply(current));
                            return obj;
                        }
                        return baseVal;
                    }, ctx);
                }
                if (idx instanceof Number num) {
                    if (num.intValue() > MAX_ARRAY_INDEX) throw new RuntimeException("Array index too large");
                    if (num.intValue() < 0 && !(val instanceof List)) throw new RuntimeException("Out of bounds negative array index");
                    if (val instanceof List<?> list) {
                        List<Object> arr = new ArrayList<>(list);
                        int i = num.intValue() < 0 ? arr.size() + num.intValue() : num.intValue();
                        if (i >= 0) {
                            while (arr.size() <= i) arr.add(null);
                            arr.set(i, transform.apply(arr.get(i)));
                        }
                        yield arr;
                    }
                    if (val == null) {
                        List<Object> arr = new ArrayList<>();
                        int i = num.intValue();
                        while (arr.size() <= i) arr.add(null);
                        arr.set(i, transform.apply(null));
                        yield arr;
                    }
                }
                if (idx instanceof String s && SafeObject.isSafeKey(s) &&
                    SafeObject.asQueryRecord(val) != null) {
                    Map<String, Object> obj = new LinkedHashMap<>(SafeObject.asQueryRecord(val));
                    Object current = obj.containsKey(s) ? obj.get(s) : null;
                    SafeObject.safeSet(obj, s, transform.apply(current));
                    yield obj;
                }
                yield val;
            }
            case AstNode.IterateNode iter -> {
                java.util.function.Function<Object, Object> applyToContainer = container -> {
                    if (container instanceof List<?> list) {
                        return list.stream().map(transform).toList();
                    }
                    Map<String, Object> obj = SafeObject.asQueryRecord(container);
                    if (obj != null) {
                        Map<String, Object> result = new LinkedHashMap<>();
                        for (Map.Entry<String, Object> e : obj.entrySet()) {
                            if (SafeObject.isSafeKey(e.getKey())) {
                                SafeObject.safeSet(result, e.getKey(), transform.apply(e.getValue()));
                            }
                        }
                        return result;
                    }
                    return container;
                };
                if (iter.base() != null) {
                    yield updateRecursive(val, iter.base(), applyToContainer, ctx);
                }
                yield applyToContainer.apply(val);
            }
            case AstNode.PipeNode pipe -> {
                Object leftResult = updateRecursive(val, pipe.left(), x -> x, ctx);
                yield updateRecursive(leftResult, pipe.right(), transform, ctx);
            }
            default -> transform.apply(val);
        };
    }

    // =========================================================================
    // Reduce / Foreach / Label / Break / Def
    // =========================================================================

    private static List<Object> evalReduce(Object value, AstNode.ReduceNode reduce, EvalContext ctx) {
        List<Object> items = evaluate(value, reduce.expr(), ctx);
        List<Object> initVals = evaluate(value, reduce.init(), ctx);
        Object accumulator = initVals.isEmpty() ? null : initVals.get(0);
        int maxDepth = ctx.limits().maxDepth();
        for (Object item : items) {
            EvalContext newCtx;
            if (reduce.pattern() != null) {
                newCtx = bindPattern(ctx, reduce.pattern(), item);
                if (newCtx == null) continue;
            } else {
                newCtx = ctx.withVar(reduce.varName(), item);
            }
            List<Object> accResult = evaluate(accumulator, reduce.update(), newCtx);
            accumulator = accResult.isEmpty() ? null : accResult.get(0);
            if (ValueOperations.getValueDepth(accumulator, maxDepth + 1) > maxDepth) {
                return new ArrayList<Object>(Collections.singletonList(null));
            }
        }
        return List.of(accumulator);
    }

    private static List<Object> evalForeach(Object value, AstNode.ForeachNode foreach, EvalContext ctx) {
        List<Object> items = evaluate(value, foreach.expr(), ctx);
        List<Object> initVals = evaluate(value, foreach.init(), ctx);
        Object state = initVals.isEmpty() ? null : initVals.get(0);
        List<Object> results = new ArrayList<>();
        for (Object item : items) {
            try {
                EvalContext newCtx;
                if (foreach.pattern() != null) {
                    newCtx = bindPattern(ctx, foreach.pattern(), item);
                    if (newCtx == null) continue;
                } else {
                    newCtx = ctx.withVar(foreach.varName(), item);
                }
                List<Object> stateResult = evaluate(state, foreach.update(), newCtx);
                state = stateResult.isEmpty() ? null : stateResult.get(0);
                if (foreach.extract() != null) {
                    results.addAll(evaluate(state, foreach.extract(), newCtx));
                } else {
                    results.add(state);
                }
            } catch (BreakException e) {
                throw e.withPrependedResults(results);
            }
        }
        return results;
    }

    private static List<Object> evalLabel(Object value, AstNode.LabelNode label, EvalContext ctx) {
        Set<String> newLabels = new LinkedHashSet<>(ctx.labels());
        newLabels.add(label.name());
        try {
            return evaluate(value, label.body(), ctx.withLabels(newLabels));
        } catch (BreakException e) {
            if (e.getLabel().equals(label.name())) {
                return e.getPartialResults();
            }
            throw e;
        }
    }

    private static List<Object> evalBreak(AstNode.BreakNode breakNode) {
        throw new BreakException(breakNode.name());
    }

    private static List<Object> evalDef(Object value, AstNode.DefNode def, EvalContext ctx) {
        Map<String, EvalContext.UserFunc> newFuncs = new LinkedHashMap<>(ctx.funcs());
        String funcKey = def.name() + "/" + def.params().size();
        EvalContext.UserFunc userFunc = new EvalContext.UserFunc(
            def.params(), def.funcBody(), new LinkedHashMap<>(ctx.funcs())
        );
        newFuncs.put(funcKey, userFunc);
        return evaluate(value, def.body(), ctx.withFuncs(newFuncs));
    }

    // =========================================================================
    // Binary Operations
    // =========================================================================

    private static List<Object> evalBinaryOp(Object value, AstNode.BinaryOpNode binOp, EvalContext ctx) {
        String op = binOp.op();

        if ("and".equals(op)) {
            List<Object> leftVals = evaluate(value, binOp.left(), ctx);
            return leftVals.stream().flatMap(l -> {
                if (!ValueOperations.isTruthy(l)) return List.of((Object) false).stream();
                List<Object> rightVals = evaluate(value, binOp.right(), ctx);
                return rightVals.stream().map(r -> ValueOperations.isTruthy(r));
            }).toList();
        }

        if ("or".equals(op)) {
            List<Object> leftVals = evaluate(value, binOp.left(), ctx);
            return leftVals.stream().flatMap(l -> {
                if (ValueOperations.isTruthy(l)) return List.of((Object) true).stream();
                List<Object> rightVals = evaluate(value, binOp.right(), ctx);
                return rightVals.stream().map(r -> ValueOperations.isTruthy(r));
            }).toList();
        }

        if ("//".equals(op)) {
            List<Object> leftVals = evaluate(value, binOp.left(), ctx);
            List<Object> nonNull = leftVals.stream()
                .filter(v -> v != null && !Boolean.FALSE.equals(v))
                .toList();
            if (!nonNull.isEmpty()) return nonNull;
            return evaluate(value, binOp.right(), ctx);
        }

        List<Object> leftVals = evaluate(value, binOp.left(), ctx);
        List<Object> rightVals = evaluate(value, binOp.right(), ctx);

        List<Object> result = new ArrayList<>();
        for (Object l : leftVals) {
            for (Object r : rightVals) {
                result.add(applyBinaryOp(l, r, op));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Object applyBinaryOp(Object l, Object r, String op) {
        return switch (op) {
            case "+" -> {
                if (l == null) yield r;
                if (r == null) yield l;
                if (l instanceof Number a && r instanceof Number b) yield a.doubleValue() + b.doubleValue();
                if (l instanceof String a && r instanceof String b) yield a + b;
                if (l instanceof List<?> a && r instanceof List<?> b) {
                    List<Object> result = new ArrayList<>(a);
                    result.addAll(b);
                    yield result;
                }
                Map<String, Object> aObj = SafeObject.asQueryRecord(l);
                Map<String, Object> bObj = SafeObject.asQueryRecord(r);
                if (aObj != null && bObj != null) yield ValueOperations.deepMerge(aObj, bObj);
                yield null;
            }
            case "-" -> {
                if (l instanceof Number a && r instanceof Number b) yield a.doubleValue() - b.doubleValue();
                if (l instanceof List<?> a && r instanceof List<?> b) {
                    Set<String> rSet = new LinkedHashSet<>();
                    for (Object x : b) rSet.add(jsonStringify(x));
                    yield a.stream().filter(x -> !rSet.contains(jsonStringify(x))).toList();
                }
                if (l instanceof String && r instanceof String) {
                    String fmtL = ((String) l).length() > 10 ? "\"" + ((String) l).substring(0, 10) + "..." : jsonStringify(l);
                    String fmtR = ((String) r).length() > 10 ? "\"" + ((String) r).substring(0, 10) + "..." : jsonStringify(r);
                    throw new RuntimeException("string (" + fmtL + ") and string (" + fmtR + ") cannot be subtracted");
                }
                yield null;
            }
            case "*" -> {
                if (l instanceof Number a && r instanceof Number b) yield a.doubleValue() * b.doubleValue();
                if (l instanceof String s && r instanceof Number n) yield s.repeat(Math.max(0, n.intValue()));
                Map<String, Object> aObj = SafeObject.asQueryRecord(l);
                Map<String, Object> bObj = SafeObject.asQueryRecord(r);
                if (aObj != null && bObj != null) yield ValueOperations.deepMerge(aObj, bObj);
                yield null;
            }
            case "/" -> {
                if (l instanceof Number a && r instanceof Number b) {
                    if (b.doubleValue() == 0) throw new RuntimeException("number (" + a + ") and number (" + b + ") cannot be divided because the divisor is zero");
                    yield a.doubleValue() / b.doubleValue();
                }
                if (l instanceof String s && r instanceof String sep) yield Arrays.asList((Object[]) s.split(sep));
                yield null;
            }
            case "%" -> {
                if (l instanceof Number a && r instanceof Number b) {
                    double bd = b.doubleValue();
                    if (bd == 0) throw new RuntimeException("number (" + a + ") and number (" + b + ") cannot be divided (remainder) because the divisor is zero");
                    double ad = a.doubleValue();
                    if (!Double.isFinite(ad) && !Double.isNaN(ad)) {
                        if (!Double.isFinite(bd) && !Double.isNaN(bd)) {
                            yield (ad < 0 && bd > 0) ? -1 : 0;
                        }
                        yield 0;
                    }
                    yield ad % bd;
                }
                yield null;
            }
            case "==" -> ValueOperations.deepEqual(l, r);
            case "!=" -> !ValueOperations.deepEqual(l, r);
            case "<" -> ValueOperations.compare(l, r) < 0;
            case "<=" -> ValueOperations.compare(l, r) <= 0;
            case ">" -> ValueOperations.compare(l, r) > 0;
            case ">=" -> ValueOperations.compare(l, r) >= 0;
            default -> null;
        };
    }

    // =========================================================================
    // Builtin Functions
    // =========================================================================

    private static List<Object> evalCall(Object value, AstNode.CallNode call, EvalContext ctx) {
        String name = call.name();
        List<AstNode> args = call.args();

        // User-defined functions shadow builtins
        String funcKey = name + "/" + args.size();
        EvalContext.UserFunc userFunc = ctx.funcs().get(funcKey);
        if (userFunc != null) {
            Map<String, EvalContext.UserFunc> baseFuncs = userFunc.closure() != null ?
                new LinkedHashMap<>(userFunc.closure()) : new LinkedHashMap<>(ctx.funcs());
            Map<String, EvalContext.UserFunc> newFuncs = new LinkedHashMap<>(baseFuncs);
            newFuncs.put(funcKey, userFunc);
            EvalContext newCtx = ctx.withFuncs(newFuncs);
            for (int i = 0; i < userFunc.params().size(); i++) {
                String paramName = userFunc.params().get(i);
                if (i < args.size()) {
                    List<Object> argVals = evaluate(value, args.get(i), ctx);
                    Object argVal = argVals.isEmpty() ? null : argVals.get(0);
                    newCtx = newCtx.withVar(paramName, argVal);
                }
            }
            return evaluate(value, userFunc.body(), newCtx);
        }

        // Simple math functions
        java.util.function.Function<Double, Double> simpleMath = SIMPLE_MATH.get(name);
        if (simpleMath != null) {
            if (value instanceof Number n) return List.of(simpleMath.apply(n.doubleValue()));
            return new ArrayList<Object>(Collections.singletonList(null));
        }

        // Try builtins
        List<Object> builtinResult = Builtins.evalBuiltin(value, name, args, ctx, Evaluator::evaluate);
        if (builtinResult != null) return builtinResult;

        // Builtins list
        if ("builtins".equals(name)) {
            return List.of((Object) new ArrayList<>(BUILTINS_LIST));
        }

        // error
        if ("error".equals(name)) {
            Object msg = args.isEmpty() ? value : evaluate(value, args.get(0), ctx).get(0);
            throw new JqException(msg);
        }

        // env
        if ("env".equals(name)) {
            Map<String, Object> envMap = new LinkedHashMap<>();
            if (ctx.env() != null) {
                for (Map.Entry<String, String> e : ctx.env().entrySet()) {
                    envMap.put(e.getKey(), e.getValue());
                }
            }
            return List.of(envMap);
        }

        // debug
        if ("debug".equals(name)) {
            return listOf(value);
        }

        // input_line_number
        if ("input_line_number".equals(name)) {
            return List.of(1L);
        }

        // Bare identifier fallback: check if it matches a variable/parameter
        if (args.isEmpty()) {
            Object v = ctx.vars().get(name);
            if (v != null || ctx.vars().containsKey(name)) {
                return listOf(v);
            }
        }

        throw new RuntimeException("Unknown function: " + name);
    }

    private static final List<String> BUILTINS_LIST = List.of(
        "add/0", "all/0", "all/1", "all/2", "any/0", "any/1", "any/2",
        "arrays/0", "ascii/0", "ascii_downcase/0", "ascii_upcase/0",
        "booleans/0", "bsearch/1", "builtins/0", "combinations/0", "combinations/1",
        "contains/1", "debug/0", "del/1", "delpaths/1", "empty/0", "env/0",
        "error/0", "error/1", "explode/0", "first/0", "first/1", "flatten/0", "flatten/1",
        "floor/0", "from_entries/0", "fromdate/0", "fromjson/0", "getpath/1",
        "gmtime/0", "group_by/1", "gsub/2", "gsub/3", "has/1", "implode/0",
        "IN/1", "IN/2", "INDEX/1", "INDEX/2", "index/1", "indices/1",
        "infinite/0", "inside/1", "isempty/1", "isnan/0", "isnormal/0",
        "isvalid/1", "iterables/0", "join/1", "keys/0", "keys_unsorted/0",
        "last/0", "last/1", "length/0", "limit/2", "ltrimstr/1", "map/1",
        "map_values/1", "match/1", "match/2", "max/0", "max_by/1", "min/0",
        "min_by/1", "mktime/0", "modulemeta/1", "nan/0", "not/0", "nth/1",
        "nth/2", "null/0", "nulls/0", "numbers/0", "objects/0", "path/1",
        "paths/0", "paths/1", "pick/1", "range/1", "range/2", "range/3",
        "recurse/0", "recurse/1", "recurse_down/0", "repeat/1", "reverse/0",
        "rindex/1", "rtrimstr/1", "scalars/0", "scan/1", "scan/2", "select/1",
        "setpath/2", "skip/2", "sort/0", "sort_by/1", "split/1", "splits/1",
        "splits/2", "sqrt/0", "startswith/1", "strftime/1", "strings/0",
        "strptime/1", "sub/2", "sub/3", "test/1", "test/2", "to_entries/0",
        "toboolean/0", "todate/0", "tojson/0", "tostream/0", "fromstream/1",
        "truncate_stream/1", "tonumber/0", "tostring/0", "transpose/0",
        "trim/0", "ltrim/0", "rtrim/0", "type/0", "unique/0", "unique_by/1",
        "until/2", "utf8bytelength/0", "values/0", "walk/1", "while/2", "with_entries/1"
    );

    // =========================================================================
    // Path Extraction
    // =========================================================================

    private static List<Object> extractPathFromAst(AstNode ast) {
        if (ast instanceof AstNode.IdentityNode) return List.of();
        if (ast instanceof AstNode.FieldNode field) {
            List<Object> basePath = field.base() != null ? extractPathFromAst(field.base()) : List.of();
            if (basePath == null) return null;
            List<Object> result = new ArrayList<>(basePath);
            result.add(field.name());
            return result;
        }
        if (ast instanceof AstNode.IndexNode idx && idx.index() instanceof AstNode.LiteralNode lit) {
            List<Object> basePath = idx.base() != null ? extractPathFromAst(idx.base()) : List.of();
            if (basePath == null) return null;
            List<Object> result = new ArrayList<>(basePath);
            result.add(lit.value());
            return result;
        }
        if (ast instanceof AstNode.PipeNode pipe) {
            List<Object> leftPath = extractPathFromAst(pipe.left());
            if (leftPath == null) return null;
            return applyPathTransform(leftPath, pipe.right());
        }
        if (ast instanceof AstNode.CallNode call) {
            if ("first".equals(call.name()) && call.args().isEmpty()) return List.of(0);
            if ("last".equals(call.name()) && call.args().isEmpty()) return List.of(-1);
        }
        return null;
    }

    private static List<Object> applyPathTransform(List<Object> basePath, AstNode ast) {
        if (ast instanceof AstNode.CallNode call) {
            if ("parent".equals(call.name())) {
                int levels = 1;
                if (!call.args().isEmpty() && call.args().get(0) instanceof AstNode.LiteralNode lit && lit.value() instanceof Number n) {
                    levels = n.intValue();
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
        List<Object> rightPath = extractPathFromAst(ast);
        if (rightPath != null) {
            List<Object> result = new ArrayList<>(basePath);
            result.addAll(rightPath);
            return result;
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
    private static Object getValueAtPath(Object root, List<Object> path) {
        Object v = root;
        for (Object key : path) {
            if (v instanceof List<?> list && key instanceof Number num) {
                int idx = num.intValue();
                v = (idx >= 0 && idx < list.size()) ? list.get(idx) : null;
            } else if (v instanceof Map<?, ?> map && key instanceof String s) {
                Map<String, Object> obj = (Map<String, Object>) map;
                v = obj.containsKey(s) ? obj.get(s) : null;
            } else {
                return null;
            }
        }
        return v;
    }
}
