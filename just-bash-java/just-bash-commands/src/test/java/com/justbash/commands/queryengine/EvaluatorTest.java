package com.justbash.commands.queryengine;

import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class EvaluatorTest {

    private static List<Object> eval(String expr, Object value) {
        AstNode ast = Parser.parse(expr);
        return Evaluator.evaluate(value, ast);
    }

    private static Object evalOne(String expr, Object value) {
        List<Object> results = eval(expr, value);
        assertFalse(results.isEmpty(), "Expected at least one result for: " + expr);
        return results.get(0);
    }

    private static Map<String, Object> obj(Object... kvs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kvs.length; i += 2) {
            Object val = kvs[i + 1];
            if (val instanceof Long l) val = l.doubleValue();
            map.put((String) kvs[i], val);
        }
        return map;
    }

    private static List<Object> arr(Object... items) {
        List<Object> result = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Long l) {
                result.add(l.doubleValue());
            } else if (item instanceof List<?> list) {
                List<Object> nested = new ArrayList<>();
                for (Object x : list) {
                    if (x instanceof Long lx) nested.add(lx.doubleValue());
                    else nested.add(x);
                }
                result.add(nested);
            } else {
                result.add(item);
            }
        }
        return result;
    }

    @Test
    public void testIdentity() {
        assertEquals("hello", evalOne(".", "hello"));
        assertEquals(42L, evalOne(".", 42L));
    }

    @Test
    public void testFieldAccess() {
        Map<String, Object> data = obj("name", "Alice", "age", 30L);
        assertEquals("Alice", evalOne(".name", data));
        assertEquals(30.0, evalOne(".age", data));
    }

    @Test
    public void testNestedFieldAccess() {
        Map<String, Object> inner = obj("x", 1L);
        Map<String, Object> data = obj("a", inner);
        assertEquals(1.0, evalOne(".a.x", data));
    }

    @Test
    public void testIndex() {
        List<Object> data = arr(10L, 20L, 30L);
        assertEquals(10.0, evalOne(".[0]", data));
        assertEquals(20.0, evalOne(".[1]", data));
        assertEquals(30.0, evalOne(".[-1]", data));
    }

    @Test
    public void testSlice() {
        List<Object> data = arr(1L, 2L, 3L, 4L, 5L);
        assertEquals(arr(2L, 3L), evalOne(".[1:3]", data));
        assertEquals(arr(1L, 2L, 3L), evalOne(".[:3]", data));
        assertEquals(arr(3L, 4L, 5L), evalOne(".[2:]", data));
    }

    @Test
    public void testIterate() {
        List<Object> data = arr(1L, 2L, 3L);
        assertEquals(arr(1L, 2L, 3L), eval(".[]", data));
    }

    @Test
    public void testPipe() {
        Map<String, Object> data = obj("a", obj("b", 42L));
        assertEquals(42.0, evalOne(".a | .b", data));
    }

    @Test
    public void testComma() {
        Map<String, Object> data = obj("x", 1L, "y", 2L);
        assertEquals(arr(1L, 2L), eval(".x, .y", data));
    }

    @Test
    public void testLiteral() {
        assertEquals(true, evalOne("true", null));
        assertEquals(false, evalOne("false", null));
        assertNull(evalOne("null", null));
    }

    @Test
    public void testArrayConstruction() {
        assertEquals(arr(1L, 2L, 3L), evalOne("[1, 2, 3]", null));
    }

    @Test
    public void testObjectConstruction() {
        Object result = evalOne("{a: 1, b: 2}", null);
        assertTrue(result instanceof Map);
        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals(1.0, map.get("a"));
        assertEquals(2.0, map.get("b"));
    }

    @Test
    public void testBinaryOp() {
        assertEquals(5.0, evalOne("2 + 3", null));
        assertEquals(-1.0, evalOne("2 - 3", null));
        assertEquals(6.0, evalOne("2 * 3", null));
        assertEquals(2.0, evalOne("6 / 3", null));
        assertEquals(true, evalOne("2 == 2", null));
        assertEquals(false, evalOne("2 == 3", null));
        assertEquals(true, evalOne("2 < 3", null));
        assertEquals(true, evalOne("2 <= 3", null));
    }

    @Test
    public void testAlternative() {
        assertEquals(1.0, evalOne("1 // 2", null));
        assertEquals(2.0, evalOne("null // 2", null));
        assertEquals(2.0, evalOne("false // 2", null));
    }

    @Test
    public void testAndOr() {
        assertEquals(true, evalOne("true and true", null));
        assertEquals(false, evalOne("true and false", null));
        assertEquals(true, evalOne("true or false", null));
        assertEquals(false, evalOne("false or false", null));
    }

    @Test
    public void testUnaryOp() {
        assertEquals(-5.0, evalOne("-5", null));
        assertEquals(false, evalOne("not", true));
        assertEquals(true, evalOne("not", false));
    }

    @Test
    public void testCond() {
        assertEquals("yes", evalOne("if true then \"yes\" else \"no\" end", null));
        assertEquals("no", evalOne("if false then \"yes\" else \"no\" end", null));
    }

    @Test
    public void testTryCatch() {
        assertEquals("caught", evalOne("try .foo catch \"caught\"", 42L));
    }

    @Test
    public void testVarBind() {
        assertEquals(5L, evalOne("5 as $x | $x", null));
    }

    @Test
    public void testVarRef() {
        assertNull(evalOne("$undefined", null));
    }

    @Test
    public void testRecurse() {
        Map<String, Object> data = obj("a", obj("b", 1L));
        List<Object> results = eval("..", data);
        assertEquals(3, results.size());
    }

    @Test
    public void testOptional() {
        assertEquals(List.of(), eval(".foo?", 42L));
    }

    @Test
    public void testUpdateOp() {
        Map<String, Object> data = obj("a", 1L);
        Object result = evalOne(".a = 5", data);
        assertTrue(result instanceof Map);
        assertEquals(5.0, ((Map<?, ?>) result).get("a"));
    }

    @Test
    public void testReduce() {
        List<Object> data = arr(1L, 2L, 3L);
        assertEquals(6.0, evalOne("reduce .[] as $x (0; . + $x)", data));
    }

    @Test
    public void testForeach() {
        List<Object> data = arr(1L, 2L, 3L);
        assertEquals(arr(1.0, 3.0, 6.0), eval("foreach .[] as $x (0; . + $x)", data));
    }

    @Test
    public void testLabelBreak() {
        assertEquals(List.of(), eval("label $out | break $out", null));
    }

    @Test
    public void testDef() {
        assertEquals(5.0, evalOne("def add1: . + 1; 4 | add1", null));
    }

    @Test
    public void testBuiltinType() {
        assertEquals("number", evalOne("type", 42L));
        assertEquals("string", evalOne("type", "hello"));
        assertEquals("array", evalOne("type", arr()));
        assertEquals("object", evalOne("type", obj()));
        assertEquals("boolean", evalOne("type", true));
        assertEquals("null", evalOne("type", null));
    }

    @Test
    public void testBuiltinLength() {
        assertEquals(3.0, evalOne("length", "abc"));
        assertEquals(3.0, evalOne("length", arr(1L, 2L, 3L)));
        assertEquals(2.0, evalOne("length", obj("a", 1L, "b", 2L)));
    }

    @Test
    public void testBuiltinKeys() {
        Map<String, Object> data = obj("b", 1L, "a", 2L);
        assertEquals(arr("a", "b"), evalOne("keys", data));
    }

    @Test
    public void testBuiltinSort() {
        List<Object> data = arr(3L, 1L, 2L);
        assertEquals(arr(1L, 2L, 3L), evalOne("sort", data));
    }

    @Test
    public void testBuiltinMap() {
        List<Object> data = arr(1L, 2L, 3L);
        assertEquals(arr(2.0, 3.0, 4.0), evalOne("map(. + 1)", data));
    }

    @Test
    public void testBuiltinSelect() {
        List<Object> data = arr(1L, 2L, 3L);
        assertEquals(arr(2L, 3L), eval(".[] | select(. > 1)", data));
    }

    @Test
    public void testBuiltinAdd() {
        List<Object> data = arr(1L, 2L, 3L);
        assertEquals(6.0, evalOne("add", data));
    }

    @Test
    public void testBuiltinReverse() {
        List<Object> data = arr(1L, 2L, 3L);
        assertEquals(arr(3L, 2L, 1L), evalOne("reverse", data));
    }

    @Test
    public void testBuiltinContains() {
        assertEquals(true, evalOne("contains(1)", arr(1L, 2L)));
        assertEquals(false, evalOne("contains(3)", arr(1L, 2L)));
    }

    @Test
    public void testBuiltinHas() {
        Map<String, Object> data = obj("a", 1L);
        assertEquals(true, evalOne("has(\"a\")", data));
        assertEquals(false, evalOne("has(\"b\")", data));
    }

    @Test
    public void testBuiltinIn() {
        List<Object> data = arr(1L, 2L);
        assertEquals(true, evalOne("1 | in([1,2])", data));
    }

    @Test
    public void testBuiltinToEntries() {
        Map<String, Object> data = obj("a", 1L);
        Object result = evalOne("to_entries", data);
        assertTrue(result instanceof List);
        List<?> list = (List<?>) result;
        assertEquals(1, list.size());
    }

    @Test
    public void testBuiltinFromEntries() {
        List<Object> data = arr(obj("key", "a", "value", 1L));
        Object result = evalOne("from_entries", data);
        assertTrue(result instanceof Map);
        assertEquals(1.0, ((Map<?, ?>) result).get("a"));
    }

    @Test
    public void testBuiltinUnique() {
        List<Object> data = arr(1L, 2L, 1L, 3L);
        assertEquals(arr(1L, 2L, 3L), evalOne("unique", data));
    }

    @Test
    public void testBuiltinGroupBy() {
        List<Object> data = arr(obj("a", 1L), obj("a", 2L), obj("a", 1L));
        Object result = evalOne("group_by(.a)", data);
        assertTrue(result instanceof List);
    }

    @Test
    public void testBuiltinMaxMin() {
        List<Object> data = arr(1L, 5L, 3L);
        assertEquals(5.0, evalOne("max", data));
        assertEquals(1.0, evalOne("min", data));
    }

    @Test
    public void testBuiltinAnyAll() {
        List<Object> data = arr(true, false, true);
        assertEquals(true, evalOne("any", data));
        assertEquals(false, evalOne("all", data));
    }

    @Test
    public void testBuiltinFirstLast() {
        List<Object> data = arr(1L, 2L, 3L);
        assertEquals(1.0, evalOne("first", data));
        assertEquals(3.0, evalOne("last", data));
    }

    @Test
    public void testBuiltinRange() {
        assertEquals(arr(0L, 1L, 2L), eval("range(3)", null));
        assertEquals(arr(1L, 2L), eval("range(1;3)", null));
    }

    @Test
    public void testBuiltinLimit() {
        List<Object> data = arr(1L, 2L, 3L, 4L);
        assertEquals(arr(1L, 2L), eval("limit(2; .[])", data));
    }

    @Test
    public void testBuiltinEmpty() {
        assertEquals(List.of(), eval("empty", null));
    }

    @Test
    public void testBuiltinNot() {
        assertEquals(true, evalOne("not", false));
        assertEquals(true, evalOne("not", null));
        assertEquals(false, evalOne("not", true));
    }

    @Test
    public void testBuiltinNumbersStrings() {
        assertEquals(arr(42.0), eval("numbers", 42.0));
        assertEquals(arr("hello"), eval("strings", "hello"));
        assertEquals(List.of(), eval("numbers", "hello"));
    }

    @Test
    public void testBuiltinValues() {
        assertEquals(List.of(), eval("values", null));
        assertEquals(arr(42L), eval("values", 42L));
    }

    @Test
    public void testBuiltinSqrt() {
        assertEquals(4.0, evalOne("sqrt", 16.0));
    }

    @Test
    public void testBuiltinFloorCeilRound() {
        assertEquals(3.0, evalOne("floor", 3.7));
        assertEquals(4.0, evalOne("ceil", 3.2));
        assertEquals(3.0, evalOne("round", 3.4));
    }

    @Test
    public void testBuiltinAbs() {
        assertEquals(5.0, evalOne("abs", -5.0));
        assertEquals(5.0, evalOne("fabs", -5.0));
    }

    @Test
    public void testBuiltinExplodeImplode() {
        assertEquals(arr(97L, 98L, 99L), evalOne("explode", "abc"));
        assertEquals("abc", evalOne("implode", arr(97L, 98L, 99L)));
    }

    @Test
    public void testBuiltinAsciiDowncaseUpcase() {
        assertEquals("abc", evalOne("ascii_downcase", "ABC"));
        assertEquals("ABC", evalOne("ascii_upcase", "abc"));
    }

    @Test
    public void testBuiltinSplit() {
        assertEquals(arr("a", "b", "c"), evalOne("split(\",\")", "a,b,c"));
    }

    @Test
    public void testBuiltinJoin() {
        assertEquals("a,b,c", evalOne("join(\",\")", arr("a", "b", "c")));
    }

    @Test
    public void testBuiltinStartswithEndswith() {
        assertEquals(true, evalOne("startswith(\"ab\")", "abc"));
        assertEquals(false, evalOne("startswith(\"bc\")", "abc"));
        assertEquals(true, evalOne("endswith(\"bc\")", "abc"));
    }

    @Test
    public void testBuiltinTrim() {
        assertEquals("abc", evalOne("trim", "  abc  "));
        assertEquals("abc  ", evalOne("ltrim", "  abc  "));
        assertEquals("  abc", evalOne("rtrim", "  abc  "));
    }

    @Test
    public void testBuiltinLtrimstrRtrimstr() {
        assertEquals("c", evalOne("ltrimstr(\"ab\")", "abc"));
        assertEquals("a", evalOne("rtrimstr(\"bc\")", "abc"));
    }

    @Test
    public void testBuiltinTojsonFromjson() {
        assertEquals("\"hello\"", evalOne("tojson", "hello"));
        assertEquals("hello", evalOne("fromjson", "\"hello\""));
    }

    @Test
    public void testBuiltinTostring() {
        assertEquals("42", evalOne("tostring", 42L));
    }

    @Test
    public void testBuiltinTonumber() {
        assertEquals(42.0, evalOne("tonumber", "42"));
    }

    @Test
    public void testBuiltinToboolean() {
        assertEquals(true, evalOne("toboolean", true));
        assertEquals(false, evalOne("toboolean", false));
        assertEquals(true, evalOne("toboolean", "true"));
    }

    @Test
    public void testBuiltinFlatten() {
        List<Object> data = arr(arr(1L, 2L), arr(3L, 4L));
        assertEquals(arr(1L, 2L, 3L, 4L), evalOne("flatten", data));
    }

    @Test
    public void testBuiltinTranspose() {
        List<Object> data = arr(arr(1L, 2L), arr(3L, 4L));
        Object result = evalOne("transpose", data);
        assertTrue(result instanceof List);
    }

    @Test
    public void testBuiltinInside() {
        assertEquals(true, evalOne("inside([1,2,3])", 2L));
    }

    @Test
    public void testBuiltinMapValues() {
        Map<String, Object> data = obj("a", 1L, "b", 2L);
        Object result = evalOne("map_values(. + 1)", data);
        assertTrue(result instanceof Map);
        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals(2.0, map.get("a"));
        assertEquals(3.0, map.get("b"));
    }

    @Test
    public void testBuiltinSortBy() {
        List<Object> data = arr(obj("x", 3L), obj("x", 1L), obj("x", 2L));
        Object result = evalOne("sort_by(.x)", data);
        assertTrue(result instanceof List);
    }

    @Test
    public void testBuiltinUniqueBy() {
        List<Object> data = arr(obj("x", 1L), obj("x", 2L), obj("x", 1L));
        Object result = evalOne("unique_by(.x)", data);
        assertTrue(result instanceof List);
        assertEquals(2, ((List<?>) result).size());
    }

    @Test
    public void testBuiltinMaxByMinBy() {
        List<Object> data = arr(obj("x", 1L), obj("x", 3L), obj("x", 2L));
        assertEquals(obj("x", 3L), evalOne("max_by(.x)", data));
        assertEquals(obj("x", 1L), evalOne("min_by(.x)", data));
    }

    @Test
    public void testBuiltinBsearch() {
        List<Object> data = arr(1L, 2L, 3L);
        assertEquals(1.0, evalOne("bsearch(2)", data));
    }

    @Test
    public void testBuiltinIndexRindexIndices() {
        assertEquals(1.0, evalOne("index(\"b\")", "abc"));
        assertEquals(2.0, evalOne("rindex(\"b\")", "abbc"));
        assertEquals(arr(1L, 2L), evalOne("indices(\"b\")", "abbc"));
    }

    @Test
    public void testBuiltinUntil() {
        assertEquals(10.0, evalOne("until(. >= 10; . + 1)", 0L));
    }

    @Test
    public void testBuiltinWhile() {
        List<Object> result = eval("while(. < 5; . + 1)", 0L);
        assertEquals(arr(0.0, 1.0, 2.0, 3.0, 4.0), result);
    }

    @Test
    public void testBuiltinRepeat() {
        List<Object> result = eval("repeat(. + 1) | limit(3; .)", 0L);
        assertEquals(arr(0.0, 1.0, 2.0), result);
    }

    @Test
    public void testBuiltinIsvalidIsempty() {
        assertEquals(true, evalOne("isvalid(.)", 42L));
        assertEquals(false, evalOne("isempty(.)", 42L));
        assertEquals(true, evalOne("isempty(empty)", 42L));
    }

    @Test
    public void testBuiltinNth() {
        List<Object> data = arr(10L, 20L, 30L);
        assertEquals(20.0, evalOne("nth(1)", data));
    }

    @Test
    public void testBuiltinSkip() {
        List<Object> data = arr(1L, 2L, 3L, 4L);
        assertEquals(arr(3L, 4L), eval("skip(2; .[])", data));
    }

    @Test
    public void testBuiltinWithEntries() {
        Map<String, Object> data = obj("a", 1L, "b", 2L);
        Object result = evalOne("with_entries(.value = .value + 1)", data);
        assertTrue(result instanceof Map);
    }

    @Test
    public void testBuiltinGetpathSetpath() {
        Map<String, Object> data = obj("a", obj("b", 1L));
        assertEquals(1.0, evalOne("getpath([\"a\", \"b\"])", data));
        Object result = evalOne("setpath([\"a\", \"c\"]; 2)", data);
        assertTrue(result instanceof Map);
    }

    @Test
    public void testBuiltinPaths() {
        Map<String, Object> data = obj("a", obj("b", 1L));
        Object result = evalOne("paths", data);
        assertTrue(result instanceof List);
    }

    @Test
    public void testBuiltinDel() {
        Map<String, Object> data = obj("a", 1L, "b", 2L);
        Object result = evalOne("del(.a)", data);
        assertTrue(result instanceof Map);
        Map<?, ?> map = (Map<?, ?>) result;
        assertFalse(map.containsKey("a"));
        assertEquals(2.0, map.get("b"));
    }

    @Test
    public void testBuiltinPick() {
        Map<String, Object> data = obj("a", 1L, "b", 2L, "c", 3L);
        Object result = evalOne("pick(.a, .b)", data);
        assertTrue(result instanceof Map);
        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals(1.0, map.get("a"));
        assertEquals(2.0, map.get("b"));
        assertFalse(map.containsKey("c"));
    }

    @Test
    public void testBuiltinRecurse() {
        Map<String, Object> data = obj("a", obj("b", 1L));
        List<Object> results = eval("recurse", data);
        assertTrue(results.size() > 1);
    }

    @Test
    public void testBuiltinWalk() {
        Map<String, Object> data = obj("a", 1L);
        Object result = evalOne("walk(if type == \"number\" then . + 1 else . end)", data);
        assertTrue(result instanceof Map);
        assertEquals(2.0, ((Map<?, ?>) result).get("a"));
    }

    @Test
    public void testBuiltinCombinations() {
        List<Object> data = arr(arr(1L, 2L), arr(3L, 4L));
        Object result = evalOne("combinations", data);
        assertTrue(result instanceof List);
    }

    @Test
    public void testBuiltinEnv() {
        EvalContext ctx = EvalContext.create(Map.of("FOO", "bar"), new EvalContext.Limits());
        AstNode ast = Parser.parse("env");
        Object result = Evaluator.evaluate(null, ast, ctx).get(0);
        assertTrue(result instanceof Map);
        assertEquals("bar", ((Map<?, ?>) result).get("FOO"));
    }

    @Test
    public void testBuiltinError() {
        assertThrows(JqException.class, () -> evalOne("error(\"oops\")", null));
    }

    @Test
    public void testBuiltinDebug() {
        assertEquals(42.0, evalOne("debug", 42L));
    }

    @Test
    public void testBuiltinInputLineNumber() {
        assertEquals(1.0, evalOne("input_line_number", null));
    }

    @Test
    public void testBuiltinNow() {
        Object result = evalOne("now", null);
        assertTrue(result instanceof Number);
        assertTrue(((Number) result).doubleValue() > 0);
    }

    @Test
    public void testBuiltinGmtimeMktime() {
        Object now = evalOne("now", null);
        Object gmtime = evalOne("gmtime", now);
        assertTrue(gmtime instanceof List);
        Object mktime = evalOne("mktime", gmtime);
        assertTrue(mktime instanceof Number);
    }

    @Test
    public void testBuiltinStrftimeStrptime() {
        Object gmtime = evalOne("gmtime", evalOne("now", null));
        Object strf = evalOne("strftime(\"%Y-%m-%d\")", gmtime);
        assertTrue(strf instanceof String);
    }

    @Test
    public void testBuiltinFromdateTodate() {
        String iso = "2024-01-15T10:30:00Z";
        Object ts = evalOne("fromdate", iso);
        assertTrue(ts instanceof Number);
        Object back = evalOne("todate", ts);
        assertTrue(back instanceof String);
    }

    @Test
    public void testBuiltinIN() {
        assertEquals(true, evalOne("IN(1,2,3)", 2L));
        assertEquals(false, evalOne("IN(1,2,3)", 4L));
    }

    @Test
    public void testBuiltinINDEX() {
        List<Object> data = arr(1L, 2L, 3L);
        Object result = evalOne("INDEX(.[])", data);
        assertTrue(result instanceof Map);
    }

    @Test
    public void testBuiltinJOIN() {
        Map<String, Object> idx = obj("a", 1L, "b", 2L);
        List<Object> data = arr(obj("key", "a"), obj("key", "b"));
        Object result = evalOne("JOIN(" + jsonStringify(idx) + "; .key)", data);
        assertTrue(result instanceof List);
    }

    @Test
    public void testBuiltinTostreamFromstream() {
        Map<String, Object> data = obj("a", 1L);
        List<Object> stream = eval("tostream", data);
        assertTrue(stream.size() > 0);
    }

    @Test
    public void testBuiltinTruncateStream() {
        // truncate_stream takes depth from input and stream as arg
        // Test with a simple stream: [["a","b"], 1] with depth 1 should give [["b"], 1]
        List<Object> stream = arr(arr("a", "b"), 1L);
        Object result = evalOne("truncate_stream(" + jsonStringify(stream) + ")", 1L);
        assertTrue(result instanceof List);
    }

    @Test
    public void testBuiltinAtbase64() {
        assertEquals("aGVsbG8=", evalOne("@base64", "hello"));
        assertEquals("hello", evalOne("@base64d", "aGVsbG8="));
    }

    @Test
    public void testBuiltinAturi() {
        assertEquals("hello%20world", evalOne("@uri", "hello world"));
    }

    @Test
    public void testBuiltinAtcsv() {
        assertEquals("a,b,c", evalOne("@csv", arr("a", "b", "c")));
    }

    @Test
    public void testBuiltinAttsv() {
        assertEquals("a\tb\tc", evalOne("@tsv", arr("a", "b", "c")));
    }

    @Test
    public void testBuiltinAtjson() {
        assertEquals("\"hello\"", evalOne("@json", "hello"));
    }

    @Test
    public void testBuiltinAtsh() {
        assertEquals("'hello'", evalOne("@sh", "hello"));
    }

    @Test
    public void testBuiltinDelpaths() {
        Map<String, Object> data = obj("a", 1L, "b", 2L);
        List<Object> paths = arr(arr("a"));
        Object result = evalOne("delpaths(" + jsonStringify(paths) + ")", data);
        assertTrue(result instanceof Map);
        assertFalse(((Map<?, ?>) result).containsKey("a"));
    }

    @Test
    public void testBuiltinLeafPaths() {
        Map<String, Object> data = obj("a", obj("b", 1L));
        Object result = evalOne("leaf_paths", data);
        assertTrue(result instanceof List);
    }

    @Test
    public void testBuiltinPath() {
        Map<String, Object> data = obj("a", 1L);
        Object result = evalOne("path(.a)", data);
        assertTrue(result instanceof List);
    }

    @Test
    public void testBuiltinParentRoot() {
        Map<String, Object> data = obj("a", obj("b", 1L));
        EvalContext ctx = EvalContext.create();
        ctx = ctx.withRoot(data).withCurrentPath(List.of("a", "b"));
        AstNode ast = Parser.parse("parent");
        Object result = Evaluator.evaluate(data, ast, ctx).get(0);
        assertTrue(result instanceof Map);
    }

    @Test
    public void testBuiltinParents() {
        Map<String, Object> data = obj("a", obj("b", 1L));
        EvalContext ctx = EvalContext.create();
        ctx = ctx.withRoot(data).withCurrentPath(List.of("a", "b"));
        AstNode ast = Parser.parse("parents");
        Object result = Evaluator.evaluate(data, ast, ctx).get(0);
        assertTrue(result instanceof List);
    }

    @Test
    public void testBuiltinInfiniteNan() {
        assertTrue(evalOne("infinite", null) instanceof Number);
        assertTrue(evalOne("nan", null) instanceof Number);
        assertEquals(true, evalOne("isnan", Double.NaN));
        assertEquals(true, evalOne("isinfinite", Double.POSITIVE_INFINITY));
        assertEquals(true, evalOne("isfinite", 42.0));
        assertEquals(true, evalOne("isnormal", 42.0));
    }

    @Test
    public void testBuiltinScalarsArraysObjects() {
        assertEquals(arr(42L), eval("scalars", 42L));
        assertEquals(arr(arr(1L)), eval("arrays", arr(1L)));
        assertEquals(arr(obj("a", 1L)), eval("objects", obj("a", 1L)));
    }

    @Test
    public void testBuiltinNullsBooleans() {
        assertEquals(arr((Object) null), eval("nulls", null));
        assertEquals(arr(true), eval("booleans", true));
    }

    @Test
    public void testBuiltinIterablesValues() {
        assertEquals(arr(arr(1L)), eval("iterables", arr(1L)));
        assertEquals(arr(42L), eval("values", 42L));
    }

    @Test
    public void testBuiltinUtf8bytelength() {
        assertEquals(5.0, evalOne("utf8bytelength", "hello"));
    }

    @Test
    public void testBuiltinReverseString() {
        assertEquals("cba", evalOne("reverse", "abc"));
    }

    @Test
    public void testBuiltinAscii() {
        assertEquals(97.0, evalOne("ascii", "abc"));
    }

    @Test
    public void testBuiltinPow() {
        assertEquals(8.0, evalOne("pow(2;3)", null));
    }

    @Test
    public void testBuiltinAtan2() {
        assertTrue(Math.abs(0.785 - ((Number) evalOne("atan2(1;1)", null)).doubleValue()) < 0.01);
    }

    @Test
    public void testBuiltinHypot() {
        assertEquals(5.0, evalOne("hypot(3)", 4.0));
    }

    @Test
    public void testBuiltinFma() {
        assertEquals(11.0, evalOne("fma(2;3)", 4.0));
    }

    @Test
    public void testBuiltinCopysign() {
        assertEquals(-4.0, evalOne("copysign(-1)", 4.0));
    }

    @Test
    public void testBuiltinFdim() {
        assertEquals(1.0, evalOne("fdim(3)", 4.0));
    }

    @Test
    public void testBuiltinFmaxFmin() {
        assertEquals(4.0, evalOne("fmax(3)", 4.0));
        assertEquals(3.0, evalOne("fmin(3)", 4.0));
    }

    @Test
    public void testBuiltinLdexp() {
        assertEquals(16.0, evalOne("ldexp(2)", 4.0));
    }

    @Test
    public void testBuiltinNearbyint() {
        assertEquals(4.0, evalOne("nearbyint", 3.7));
    }

    @Test
    public void testBuiltinLogb() {
        assertTrue(evalOne("logb", 8.0) instanceof Number);
    }

    @Test
    public void testBuiltinSignificand() {
        assertTrue(evalOne("significand", 8.0) instanceof Number);
    }

    @Test
    public void testBuiltinFrexp() {
        Object result = evalOne("frexp", 8.0);
        assertTrue(result instanceof List);
    }

    @Test
    public void testBuiltinModf() {
        Object result = evalOne("modf", 3.5);
        assertTrue(result instanceof List);
    }

    @Test
    public void testBuiltinExp10Exp2() {
        assertEquals(100.0, evalOne("exp10", 2.0));
        assertEquals(8.0, evalOne("exp2", 3.0));
    }

    @Test
    public void testBuiltinBuiltins() {
        Object result = evalOne("builtins", null);
        assertTrue(result instanceof List);
        assertTrue(((List<?>) result).size() > 50);
    }

    @Test
    public void testUserDefinedFunction() {
        assertEquals(8.0, evalOne("def add(a;b): a + b; add(3;5)", null));
    }

    @Test
    public void testArrayPatternBinding() {
        List<Object> data = arr(1L, 2L);
        assertEquals(3.0, evalOne(". as [$a, $b] | $a + $b", data));
    }

    @Test
    public void testObjectPatternBinding() {
        Map<String, Object> data = obj("x", 1L, "y", 2L);
        assertEquals(3.0, evalOne(". as {x: $a, y: $b} | $a + $b", data));
    }

    @Test
    public void testStringInterpolation() {
        Map<String, Object> data = obj("name", "world");
        assertEquals("hello world", evalOne("\"hello \\( .name )\"", data));
    }

    @Test
    public void testUpdatePipeOp() {
        Map<String, Object> data = obj("a", 1L);
        Object result = evalOne(".a |= . + 1", data);
        assertEquals(2.0, ((Map<?, ?>) result).get("a"));
    }

    @Test
    public void testUpdateAddOp() {
        Map<String, Object> data = obj("a", 1L);
        Object result = evalOne(".a += 1", data);
        assertEquals(2.0, ((Map<?, ?>) result).get("a"));
    }

    @Test
    public void testUpdateAltOp() {
        Map<String, Object> data = obj("a", null);
        Object result = evalOne(".a //= 5", data);
        assertEquals(5.0, ((Map<?, ?>) result).get("a"));
    }

    @Test
    public void testParen() {
        assertEquals(5.0, evalOne("(2 + 3)", null));
    }

    @Test
    public void testNullFieldReturnsNull() {
        assertNull(evalOne(".foo", null));
    }

    @Test
    public void testMissingFieldReturnsNull() {
        Map<String, Object> data = obj("a", 1L);
        assertNull(evalOne(".b", data));
    }

    @Test
    public void testOutOfBoundsIndexReturnsNull() {
        List<Object> data = arr(1L, 2L);
        assertNull(evalOne(".[5]", data));
    }

    @Test
    public void testElif() {
        assertEquals("second", evalOne("if false then \"first\" elif true then \"second\" else \"third\" end", null));
    }

    @Test
    public void testNoElseReturnsInput() {
        assertEquals(42.0, evalOne("if false then 1 end", 42L));
    }

    @Test
    public void testTryWithoutCatchReturnsEmpty() {
        assertEquals(List.of(), eval("try .foo", 42L));
    }

    @Test
    public void testOptionalOnField() {
        Map<String, Object> data = obj("a", 1L);
        assertEquals(1.0, evalOne(".a?", data));
    }

    @Test
    public void testFieldOnNonObjectThrows() {
        assertThrows(RuntimeException.class, () -> evalOne(".foo", 42L));
    }

    @Test
    public void testDivisionByZeroThrows() {
        assertThrows(RuntimeException.class, () -> evalOne("1 / 0", null));
    }

    @Test
    public void testModuloByZeroThrows() {
        assertThrows(RuntimeException.class, () -> evalOne("1 % 0", null));
    }

    @Test
    public void testUnknownFunctionThrows() {
        assertThrows(RuntimeException.class, () -> evalOne("nonexistent", null));
    }

    @Test
    public void testBreakOutsideLabelThrows() {
        assertThrows(BreakException.class, () -> evalOne("break $out", null));
    }

    @Test
    public void testEnvVar() {
        EvalContext ctx = EvalContext.create(Map.of("FOO", "bar"), new EvalContext.Limits());
        AstNode ast = Parser.parse("$ENV.FOO");
        assertEquals("bar", Evaluator.evaluate(null, ast, ctx).get(0));
    }

    @Test
    public void testNullPlusNull() {
        assertNull(evalOne("null + null", null));
    }

    @Test
    public void testNullPlusNumber() {
        assertEquals(5.0, evalOne("null + 5", null));
    }

    @Test
    public void testNumberPlusNull() {
        assertEquals(5.0, evalOne("5 + null", null));
    }

    @Test
    public void testStringConcat() {
        assertEquals("hello world", evalOne("\"hello\" + \" world\"", null));
    }

    @Test
    public void testArrayConcat() {
        assertEquals(arr(1L, 2L, 3L, 4L), evalOne("[1,2] + [3,4]", null));
    }

    @Test
    public void testObjectMerge() {
        Map<String, Object> a = obj("x", 1L);
        Map<String, Object> b = obj("y", 2L);
        Object result = evalOne(".a + .b", obj("a", a, "b", b));
        assertTrue(result instanceof Map);
        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals(1.0, map.get("x"));
        assertEquals(2.0, map.get("y"));
    }

    @Test
    public void testArraySubtract() {
        assertEquals(arr(1L, 3L), evalOne("[1,2,3] - [2]", null));
    }

    @Test
    public void testStringRepeat() {
        assertEquals("aaa", evalOne("\"a\" * 3", null));
    }

    @Test
    public void testObjectMultiply() {
        Map<String, Object> a = obj("x", 1L);
        Map<String, Object> b = obj("y", 2L);
        Object result = evalOne(".a * .b", obj("a", a, "b", b));
        assertTrue(result instanceof Map);
    }

    @Test
    public void testStringSplit() {
        assertEquals(arr("a", "b", "c"), evalOne("\"a,b,c\" / \",\"", null));
    }

    @Test
    public void testNotEquals() {
        assertEquals(true, evalOne("1 != 2", null));
        assertEquals(false, evalOne("1 != 1", null));
    }

    @Test
    public void testGreaterThan() {
        assertEquals(true, evalOne("2 > 1", null));
        assertEquals(false, evalOne("1 > 2", null));
    }

    @Test
    public void testGreaterThanOrEqual() {
        assertEquals(true, evalOne("2 >= 2", null));
        assertEquals(false, evalOne("1 >= 2", null));
    }

    @Test
    public void testLessThanOrEqual() {
        assertEquals(true, evalOne("1 <= 2", null));
        assertEquals(false, evalOne("2 <= 1", null));
    }

    @Test
    public void testDeepEquals() {
        assertEquals(true, evalOne("[1,2] == [1,2]", null));
        assertEquals(false, evalOne("[1,2] == [1,3]", null));
    }

    @Test
    public void testEvalContextCreate() {
        EvalContext ctx = EvalContext.create();
        assertNotNull(ctx);
        assertNotNull(ctx.vars());
        assertNotNull(ctx.limits());
        assertNotNull(ctx.env());
        assertNotNull(ctx.funcs());
        assertNotNull(ctx.labels());
    }

    @Test
    public void testEvalContextWithVar() {
        EvalContext ctx = EvalContext.create();
        EvalContext newCtx = ctx.withVar("$x", 42L);
        assertEquals(42L, newCtx.vars().get("$x"));
    }

    @Test
    public void testEvalContextWithFuncs() {
        EvalContext ctx = EvalContext.create();
        Map<String, EvalContext.UserFunc> funcs = new LinkedHashMap<>();
        funcs.put("foo/0", new EvalContext.UserFunc(List.of(), new AstNode.LiteralNode(42L), new LinkedHashMap<>()));
        EvalContext newCtx = ctx.withFuncs(funcs);
        assertNotNull(newCtx.funcs().get("foo/0"));
    }

    @Test
    public void testEvalContextWithLabels() {
        EvalContext ctx = EvalContext.create();
        EvalContext newCtx = ctx.withLabels(Set.of("$out"));
        assertTrue(newCtx.labels().contains("$out"));
    }

    @Test
    public void testEvalContextWithCurrentPath() {
        EvalContext ctx = EvalContext.create();
        EvalContext newCtx = ctx.withCurrentPath(List.of("a", "b"));
        assertEquals(List.of("a", "b"), newCtx.currentPath());
    }

    @Test
    public void testBreakException() {
        BreakException ex = new BreakException("$out");
        assertEquals("$out", ex.getLabel());
        assertEquals(List.of(), ex.getPartialResults());

        BreakException ex2 = ex.withPrependedResults(List.of(1L, 2L));
        assertEquals(List.of(1L, 2L), ex2.getPartialResults());
    }

    @Test
    public void testJqException() {
        JqException ex = new JqException("error message");
        assertEquals("error message", ex.getValue());
        assertEquals("error message", ex.getMessage());
    }

    @Test
    public void testJqExceptionWithObject() {
        JqException ex = new JqException(42L);
        assertEquals(42L, ex.getValue());
    }

    @Test
    public void testLimitsDefaults() {
        EvalContext.Limits limits = new EvalContext.Limits();
        assertEquals(10000, limits.maxIterations());
        assertEquals(2000, limits.maxDepth());
    }

    @Test
    public void testLimitsCustom() {
        EvalContext.Limits limits = new EvalContext.Limits(500, 100);
        assertEquals(500, limits.maxIterations());
        assertEquals(100, limits.maxDepth());
    }

    @Test
    public void testUserFuncRecord() {
        EvalContext.UserFunc func = new EvalContext.UserFunc(
            List.of("a"),
            new AstNode.LiteralNode(42L),
            new LinkedHashMap<>()
        );
        assertEquals(List.of("a"), func.params());
        assertNotNull(func.body());
    }

    // Helper
    private static String jsonStringify(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "\"" + value + "\"";
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(jsonStringify(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(e.getKey()).append("\":").append(jsonStringify(e.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        return value.toString();
    }
}
