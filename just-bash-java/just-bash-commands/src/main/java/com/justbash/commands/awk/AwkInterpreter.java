package com.justbash.commands.awk;

import com.justbash.CommandContext;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AwkInterpreter {

    private final AwkTypes.RuntimeContext ctx;
    private final CommandContext commandCtx;
    private AwkTypes.AwkProgram program;
    private final Map<String, Boolean> rangeStates = new HashMap<>();

    public AwkInterpreter(AwkTypes.RuntimeContext ctx, CommandContext commandCtx) {
        this.ctx = ctx;
        this.commandCtx = commandCtx;
    }

    public void execute(AwkTypes.AwkProgram program) {
        this.program = program;
        this.ctx.output = "";
        for (AwkTypes.AwkFunctionDef func : program.functions) {
            ctx.functions.put(func.name, func);
        }
    }

    public void executeBegin() {
        if (program == null) return;
        for (AwkTypes.AwkRule rule : program.rules) {
            if (rule.pattern != null && rule.pattern.type() == AwkTypes.PatternType.BEGIN) {
                executeStatements(rule.action.statements);
                if (ctx.shouldExit) break;
            }
        }
    }

    public void executeLine(String line) {
        if (program == null || ctx.shouldExit) return;
        setCurrentLine(line);
        ctx.NR++;
        ctx.FNR++;
        ctx.shouldNext = false;

        for (int i = 0; i < program.rules.size(); i++) {
            if (ctx.shouldExit || ctx.shouldNext || ctx.shouldNextFile) break;
            AwkTypes.AwkRule rule = program.rules.get(i);
            if (rule.pattern == null) {
                executeStatements(rule.action.statements);
            } else if (rule.pattern.type() != AwkTypes.PatternType.BEGIN &&
                       rule.pattern.type() != AwkTypes.PatternType.END) {
                if (matchesRule(rule, i)) {
                    executeStatements(rule.action.statements);
                }
            }
        }
    }

    public void executeEnd() {
        if (program == null || ctx.inEndBlock) return;
        ctx.inEndBlock = true;
        ctx.shouldExit = false;
        for (AwkTypes.AwkRule rule : program.rules) {
            if (rule.pattern != null && rule.pattern.type() == AwkTypes.PatternType.END) {
                executeStatements(rule.action.statements);
                if (ctx.shouldExit) break;
            }
        }
        ctx.inEndBlock = false;
    }

    public String getOutput() {
        return ctx.output;
    }

    public int getExitCode() {
        return ctx.exitCode;
    }

    // ─── Rule Matching ─────────────────────────────────────────

    private boolean matchesRule(AwkTypes.AwkRule rule, int index) {
        AwkTypes.AwkPattern pattern = rule.pattern;
        if (pattern == null) return true;

        switch (pattern.type()) {
            case BEGIN: case END: return false;
            case REGEX_PATTERN:
                return matchRegex(((AwkTypes.RegexPattern) pattern).pattern, ctx.line);
            case EXPR_PATTERN:
                return isTruthy(evalExpr(((AwkTypes.ExprPattern) pattern).expression));
            case RANGE:
                return matchesRange((AwkTypes.RangePattern) pattern, index);
            default: return false;
        }
    }

    private boolean matchesRange(AwkTypes.RangePattern range, int index) {
        String key = "range_" + index;
        boolean active = rangeStates.getOrDefault(key, false);
        boolean startMatches = matchPattern(range.start);
        boolean endMatches = matchPattern(range.end);

        if (!active) {
            if (startMatches) {
                rangeStates.put(key, true);
                if (endMatches) {
                    rangeStates.put(key, false);
                }
                return true;
            }
            return false;
        } else {
            if (endMatches) {
                rangeStates.put(key, false);
            }
            return true;
        }
    }

    private boolean matchPattern(AwkTypes.AwkPattern pattern) {
        switch (pattern.type()) {
            case REGEX_PATTERN:
                return matchRegex(((AwkTypes.RegexPattern) pattern).pattern, ctx.line);
            case EXPR_PATTERN:
                return isTruthy(evalExpr(((AwkTypes.ExprPattern) pattern).expression));
            default: return false;
        }
    }

    private boolean matchRegex(String pattern, String text) {
        try {
            return Pattern.compile(pattern).matcher(text).find();
        } catch (Exception e) {
            return false;
        }
    }

    // ─── Statement Execution ───────────────────────────────────

    private void executeStatements(List<AwkTypes.AwkStmt> statements) {
        for (AwkTypes.AwkStmt stmt : statements) {
            if (ctx.shouldExit || ctx.shouldNext || ctx.shouldNextFile || ctx.hasReturn) break;
            executeStatement(stmt);
        }
    }

    private void executeStatement(AwkTypes.AwkStmt stmt) {
        ctx.iterationCount++;
        if (ctx.iterationCount > ctx.maxIterations) {
            ctx.shouldExit = true;
            return;
        }

        switch (stmt.type()) {
            case EXPR_STMT:
                evalExpr(((AwkTypes.ExpressionStmt) stmt).expression);
                break;
            case PRINT:
                executePrint((AwkTypes.PrintStmt) stmt);
                break;
            case PRINTF:
                executePrintf((AwkTypes.PrintfStmt) stmt);
                break;
            case IF:
                executeIf((AwkTypes.IfStmt) stmt);
                break;
            case WHILE:
                executeWhile((AwkTypes.WhileStmt) stmt);
                break;
            case DO_WHILE:
                executeDoWhile((AwkTypes.DoWhileStmt) stmt);
                break;
            case FOR:
                executeFor((AwkTypes.ForStmt) stmt);
                break;
            case FOR_IN:
                executeForIn((AwkTypes.ForInStmt) stmt);
                break;
            case BREAK:
                // Break handled by loop context - not supported here
                break;
            case CONTINUE:
                // Continue handled by loop context - not supported here
                break;
            case NEXT:
                ctx.shouldNext = true;
                break;
            case NEXT_FILE:
                ctx.shouldNextFile = true;
                break;
            case EXIT:
                executeExit((AwkTypes.ExitStmt) stmt);
                break;
            case RETURN:
                executeReturn((AwkTypes.ReturnStmt) stmt);
                break;
            case DELETE:
                executeDelete((AwkTypes.DeleteStmt) stmt);
                break;
            case BLOCK:
                executeStatements(((AwkTypes.BlockStmt) stmt).statements);
                break;
        }
    }

    private void executePrint(AwkTypes.PrintStmt stmt) {
        StringBuilder sb = new StringBuilder();
        if (stmt.args.isEmpty()) {
            sb.append(ctx.line);
        } else {
            for (int i = 0; i < stmt.args.size(); i++) {
                if (i > 0) sb.append(ctx.OFS);
                sb.append(toAwkString(evalExpr(stmt.args.get(i))));
            }
        }
        sb.append(ctx.ORS);
        String output = sb.toString();
        if (stmt.redirect != null && stmt.file != null) {
            String filename = toAwkString(evalExpr(stmt.file));
            String path = filename.startsWith("/") ? filename : commandCtx.cwd() + "/" + filename;
            try {
                if (stmt.redirect.equals(">>")) {
                    String existing = "";
                    try {
                        existing = commandCtx.fs().readFile(path).join();
                    } catch (Exception ignored) {}
                    commandCtx.fs().writeFile(path, new com.justbash.fs.IFileSystem.StringContent(existing + output)).join();
                } else {
                    commandCtx.fs().writeFile(path, new com.justbash.fs.IFileSystem.StringContent(output)).join();
                }
            } catch (Exception e) {
                // Ignore write errors
            }
        } else {
            ctx.output += output;
        }
    }

    private void executePrintf(AwkTypes.PrintfStmt stmt) {
        String format = toAwkString(evalExpr(stmt.format));
        List<Object> args = new ArrayList<>();
        for (AwkTypes.AwkExpr arg : stmt.args) {
            args.add(evalExpr(arg));
        }
        String output = sprintf(format, args);
        if (stmt.redirect != null && stmt.file != null) {
            String filename = toAwkString(evalExpr(stmt.file));
            String path = filename.startsWith("/") ? filename : commandCtx.cwd() + "/" + filename;
            try {
                if (stmt.redirect.equals(">>")) {
                    String existing = "";
                    try {
                        existing = commandCtx.fs().readFile(path).join();
                    } catch (Exception ignored) {}
                    commandCtx.fs().writeFile(path, new com.justbash.fs.IFileSystem.StringContent(existing + output)).join();
                } else {
                    commandCtx.fs().writeFile(path, new com.justbash.fs.IFileSystem.StringContent(output)).join();
                }
            } catch (Exception e) {
                // Ignore write errors
            }
        } else {
            ctx.output += output;
        }
    }

    private void executeIf(AwkTypes.IfStmt stmt) {
        if (isTruthy(evalExpr(stmt.condition))) {
            executeStatement(stmt.consequent);
        } else if (stmt.alternate != null) {
            executeStatement(stmt.alternate);
        }
    }

    private void executeWhile(AwkTypes.WhileStmt stmt) {
        while (isTruthy(evalExpr(stmt.condition)) && !ctx.shouldExit && !ctx.shouldNext && !ctx.shouldNextFile) {
            executeStatement(stmt.body);
        }
    }

    private void executeDoWhile(AwkTypes.DoWhileStmt stmt) {
        do {
            executeStatement(stmt.body);
        } while (isTruthy(evalExpr(stmt.condition)) && !ctx.shouldExit && !ctx.shouldNext && !ctx.shouldNextFile);
    }

    private void executeFor(AwkTypes.ForStmt stmt) {
        if (stmt.init != null) evalExpr(stmt.init);
        while ((stmt.condition == null || isTruthy(evalExpr(stmt.condition))) && !ctx.shouldExit && !ctx.shouldNext && !ctx.shouldNextFile) {
            executeStatement(stmt.body);
            if (stmt.update != null) evalExpr(stmt.update);
        }
    }

    private void executeForIn(AwkTypes.ForInStmt stmt) {
        Map<String, Object> array = ctx.arrays.get(stmt.array);
        if (array != null) {
            for (String key : array.keySet()) {
                if (ctx.shouldExit || ctx.shouldNext || ctx.shouldNextFile) break;
                ctx.vars.put(stmt.variable, key);
                executeStatement(stmt.body);
            }
        }
    }

    private void executeExit(AwkTypes.ExitStmt stmt) {
        ctx.shouldExit = true;
        if (stmt.code != null) {
            Object val = evalExpr(stmt.code);
            ctx.exitCode = (int) toNumber(val);
        }
    }

    private void executeReturn(AwkTypes.ReturnStmt stmt) {
        ctx.hasReturn = true;
        if (stmt.value != null) {
            ctx.returnValue = evalExpr(stmt.value);
        } else {
            ctx.returnValue = "";
        }
    }

    private void executeDelete(AwkTypes.DeleteStmt stmt) {
        AwkTypes.AwkExpr target = stmt.target;
        if (target instanceof AwkTypes.ArrayAccess) {
            AwkTypes.ArrayAccess aa = (AwkTypes.ArrayAccess) target;
            String key = toAwkString(evalExpr(aa.key));
            Map<String, Object> array = ctx.arrays.get(aa.array);
            if (array != null) array.remove(key);
        } else if (target instanceof AwkTypes.Variable) {
            ctx.vars.remove(((AwkTypes.Variable) target).name);
        }
    }

    // ─── Expression Evaluation ─────────────────────────────────

    private Object evalExpr(AwkTypes.AwkExpr expr) {
        if (expr == null) return "";

        switch (expr.type()) {
            case NUMBER:
                return ((AwkTypes.NumberLiteral) expr).value;
            case STRING:
                return ((AwkTypes.StringLiteral) expr).value;
            case REGEX:
                return matchRegex(((AwkTypes.RegexLiteral) expr).pattern, ctx.line) ? 1.0 : 0.0;
            case FIELD:
                return evalFieldRef((AwkTypes.FieldRef) expr);
            case VARIABLE:
                return getVariable(((AwkTypes.Variable) expr).name);
            case ARRAY_ACCESS:
                return evalArrayAccess((AwkTypes.ArrayAccess) expr);
            case BINARY:
                return evalBinaryOp((AwkTypes.BinaryOp) expr);
            case UNARY:
                return evalUnaryOp((AwkTypes.UnaryOp) expr);
            case TERNARY:
                return evalTernary((AwkTypes.TernaryOp) expr);
            case CALL:
                return evalFunctionCall((AwkTypes.FunctionCall) expr);
            case ASSIGNMENT:
                return evalAssignment((AwkTypes.Assignment) expr);
            case PRE_INCREMENT:
                return evalPreInc((AwkTypes.PreIncrement) expr);
            case PRE_DECREMENT:
                return evalPreDec((AwkTypes.PreDecrement) expr);
            case POST_INCREMENT:
                return evalPostInc((AwkTypes.PostIncrement) expr);
            case POST_DECREMENT:
                return evalPostDec((AwkTypes.PostDecrement) expr);
            case IN:
                return evalInExpr((AwkTypes.InExpr) expr);
            case GETLINE:
                return evalGetline((AwkTypes.GetlineExpr) expr);
            case TUPLE:
                Object result = "";
                for (AwkTypes.AwkExpr e : ((AwkTypes.TupleExpr) expr).elements) {
                    result = evalExpr(e);
                }
                return result;
            default:
                return "";
        }
    }

    private Object evalFieldRef(AwkTypes.FieldRef expr) {
        int index = (int) toNumber(evalExpr(expr.index));
        return getField(index);
    }

    private Object evalArrayAccess(AwkTypes.ArrayAccess expr) {
        String key = toAwkString(evalExpr(expr.key));
        String arrayName = expr.array;
        String aliased = ctx.arrayAliases.get(arrayName);
        if (aliased != null) arrayName = aliased;
        Map<String, Object> array = ctx.arrays.get(arrayName);
        if (array == null) return "";
        Object val = array.get(key);
        return val != null ? val : "";
    }

    private Object evalBinaryOp(AwkTypes.BinaryOp expr) {
        String op = expr.operator;

        if (op.equals("||")) {
            return isTruthy(evalExpr(expr.left)) || isTruthy(evalExpr(expr.right)) ? 1.0 : 0.0;
        }
        if (op.equals("&&")) {
            return isTruthy(evalExpr(expr.left)) && isTruthy(evalExpr(expr.right)) ? 1.0 : 0.0;
        }
        if (op.equals("~")) {
            Object left = evalExpr(expr.left);
            String pattern;
            if (expr.right instanceof AwkTypes.RegexLiteral) {
                pattern = ((AwkTypes.RegexLiteral) expr.right).pattern;
            } else {
                pattern = toAwkString(evalExpr(expr.right));
            }
            try {
                return Pattern.compile(pattern).matcher(toAwkString(left)).find() ? 1.0 : 0.0;
            } catch (Exception e) {
                return 0.0;
            }
        }
        if (op.equals("!~")) {
            Object left = evalExpr(expr.left);
            String pattern;
            if (expr.right instanceof AwkTypes.RegexLiteral) {
                pattern = ((AwkTypes.RegexLiteral) expr.right).pattern;
            } else {
                pattern = toAwkString(evalExpr(expr.right));
            }
            try {
                return Pattern.compile(pattern).matcher(toAwkString(left)).find() ? 0.0 : 1.0;
            } catch (Exception e) {
                return 1.0;
            }
        }
        if (op.equals(" ")) {
            return toAwkString(evalExpr(expr.left)) + toAwkString(evalExpr(expr.right));
        }

        Object left = evalExpr(expr.left);
        Object right = evalExpr(expr.right);

        if (isComparisonOp(op)) {
            return evalComparison(left, right, op);
        }

        double l = toNumber(left);
        double r = toNumber(right);
        switch (op) {
            case "+": return l + r;
            case "-": return l - r;
            case "*": return l * r;
            case "/": return r != 0 ? l / r : 0.0;
            case "%": return r != 0 ? l % r : 0.0;
            case "^": return Math.pow(l, r);
            default: return 0.0;
        }
    }

    private boolean isComparisonOp(String op) {
        return op.equals("<") || op.equals("<=") || op.equals(">") || op.equals(">=") || op.equals("==") || op.equals("!=");
    }

    private double evalComparison(Object left, Object right, String op) {
        boolean leftNum = looksLikeNumber(left);
        boolean rightNum = looksLikeNumber(right);
        if (leftNum && rightNum) {
            double l = toNumber(left);
            double r = toNumber(right);
            switch (op) {
                case "<": return l < r ? 1.0 : 0.0;
                case "<=": return l <= r ? 1.0 : 0.0;
                case ">": return l > r ? 1.0 : 0.0;
                case ">=": return l >= r ? 1.0 : 0.0;
                case "==": return l == r ? 1.0 : 0.0;
                case "!=": return l != r ? 1.0 : 0.0;
            }
        }
        String l = toAwkString(left);
        String r = toAwkString(right);
        int cmp = l.compareTo(r);
        switch (op) {
            case "<": return cmp < 0 ? 1.0 : 0.0;
            case "<=": return cmp <= 0 ? 1.0 : 0.0;
            case ">": return cmp > 0 ? 1.0 : 0.0;
            case ">=": return cmp >= 0 ? 1.0 : 0.0;
            case "==": return l.equals(r) ? 1.0 : 0.0;
            case "!=": return !l.equals(r) ? 1.0 : 0.0;
        }
        return 0.0;
    }

    private Object evalUnaryOp(AwkTypes.UnaryOp expr) {
        Object val = evalExpr(expr.operand);
        switch (expr.operator) {
            case "!": return isTruthy(val) ? 0.0 : 1.0;
            case "-": return -toNumber(val);
            case "+": return +toNumber(val);
            default: return val;
        }
    }

    private Object evalTernary(AwkTypes.TernaryOp expr) {
        return isTruthy(evalExpr(expr.condition))
            ? evalExpr(expr.consequent)
            : evalExpr(expr.alternate);
    }

    private Object evalAssignment(AwkTypes.Assignment expr) {
        Object value = evalExpr(expr.value);
        String op = expr.operator;
        AwkTypes.AwkExpr target = expr.target;

        Object finalValue = value;
        if (!op.equals("=")) {
            Object current = getTargetValue(target);
            double currentNum = toNumber(current);
            double valueNum = toNumber(value);
            switch (op) {
                case "+=": finalValue = currentNum + valueNum; break;
                case "-=": finalValue = currentNum - valueNum; break;
                case "*=": finalValue = currentNum * valueNum; break;
                case "/=": finalValue = valueNum != 0 ? currentNum / valueNum : 0.0; break;
                case "%=": finalValue = valueNum != 0 ? currentNum % valueNum : 0.0; break;
                case "^=": finalValue = Math.pow(currentNum, valueNum); break;
            }
        }

        setTargetValue(target, finalValue);
        return finalValue;
    }

    private Object getTargetValue(AwkTypes.AwkExpr target) {
        if (target instanceof AwkTypes.FieldRef) {
            int index = (int) toNumber(evalExpr(((AwkTypes.FieldRef) target).index));
            return getField(index);
        } else if (target instanceof AwkTypes.Variable) {
            return getVariable(((AwkTypes.Variable) target).name);
        } else if (target instanceof AwkTypes.ArrayAccess) {
            AwkTypes.ArrayAccess aa = (AwkTypes.ArrayAccess) target;
            String key = toAwkString(evalExpr(aa.key));
            Map<String, Object> array = ctx.arrays.get(aa.array);
            if (array == null) return "";
            Object val = array.get(key);
            return val != null ? val : "";
        }
        return "";
    }

    private void setTargetValue(AwkTypes.AwkExpr target, Object value) {
        if (target instanceof AwkTypes.FieldRef) {
            int index = (int) toNumber(evalExpr(((AwkTypes.FieldRef) target).index));
            setField(index, toAwkString(value));
        } else if (target instanceof AwkTypes.Variable) {
            ctx.vars.put(((AwkTypes.Variable) target).name, value);
        } else if (target instanceof AwkTypes.ArrayAccess) {
            AwkTypes.ArrayAccess aa = (AwkTypes.ArrayAccess) target;
            String key = toAwkString(evalExpr(aa.key));
            String arrayName = aa.array;
            String aliased = ctx.arrayAliases.get(arrayName);
            if (aliased != null) arrayName = aliased;
            ctx.arrays.computeIfAbsent(arrayName, k -> new HashMap<>()).put(key, value);
        }
    }

    private Object evalPreInc(AwkTypes.PreIncrement expr) {
        return applyIncDec(expr.operand, 1, true);
    }

    private Object evalPreDec(AwkTypes.PreDecrement expr) {
        return applyIncDec(expr.operand, -1, true);
    }

    private Object evalPostInc(AwkTypes.PostIncrement expr) {
        return applyIncDec(expr.operand, 1, false);
    }

    private Object evalPostDec(AwkTypes.PostDecrement expr) {
        return applyIncDec(expr.operand, -1, false);
    }

    private Object applyIncDec(AwkTypes.AwkExpr operand, double delta, boolean returnNew) {
        Object current = getTargetValue(operand);
        double oldVal = toNumber(current);
        double newVal = oldVal + delta;
        setTargetValue(operand, newVal);
        return returnNew ? newVal : oldVal;
    }

    private Object evalInExpr(AwkTypes.InExpr expr) {
        String key = toAwkString(evalExpr(expr.key));
        Map<String, Object> array = ctx.arrays.get(expr.array);
        return array != null && array.containsKey(key) ? 1.0 : 0.0;
    }

    private Object evalGetline(AwkTypes.GetlineExpr expr) {
        // Simple getline: read next line from current input
        if (ctx.lineIndex + 1 < ctx.lines.size()) {
            ctx.lineIndex++;
            String line = ctx.lines.get(ctx.lineIndex);
            ctx.NR++;
            if (expr.variable != null) {
                ctx.vars.put(expr.variable, line);
            } else {
                setCurrentLine(line);
            }
            return 1.0;
        }
        return 0.0;
    }

    // ─── Built-in Functions ────────────────────────────────────

    private Object evalFunctionCall(AwkTypes.FunctionCall expr) {
        String name = expr.name;
        List<Object> args = new ArrayList<>();
        for (AwkTypes.AwkExpr arg : expr.args) {
            args.add(evalExpr(arg));
        }

        switch (name) {
            case "length":
                if (args.isEmpty()) return (double) ctx.line.length();
                return (double) toAwkString(args.get(0)).length();
            case "substr":
                if (args.size() >= 2) {
                    String str = toAwkString(args.get(0));
                    int start = (int) toNumber(args.get(1));
                    if (start < 1) start = 1;
                    int len = args.size() >= 3 ? (int) toNumber(args.get(2)) : str.length();
                    if (start > str.length()) return "";
                    int end = Math.min(start - 1 + len, str.length());
                    return str.substring(start - 1, end);
                }
                return "";
            case "index":
                if (args.size() >= 2) {
                    String str = toAwkString(args.get(0));
                    String sub = toAwkString(args.get(1));
                    int idx = str.indexOf(sub);
                    return idx >= 0 ? (double) (idx + 1) : 0.0;
                }
                return 0.0;
            case "split":
                if (args.size() >= 2) {
                    String str = toAwkString(args.get(0));
                    String arrayName = ((AwkTypes.Variable) expr.args.get(1)).name;
                    String sep = args.size() >= 3 ? toAwkString(args.get(2)) : ctx.FS;
                    Map<String, Object> array = ctx.arrays.computeIfAbsent(arrayName, k -> new HashMap<>());
                    array.clear();
                    String[] parts;
                    if (sep.equals(" ")) {
                        parts = str.trim().split("\\s+");
                    } else {
                        parts = str.split(Pattern.quote(sep), -1);
                    }
                    for (int i = 0; i < parts.length; i++) {
                        array.put(String.valueOf(i + 1), parts[i]);
                    }
                    return (double) parts.length;
                }
                return 0.0;
            case "gsub":
            case "sub":
                if (args.size() >= 2) {
                    String pattern;
                    if (expr.args.get(0) instanceof AwkTypes.RegexLiteral) {
                        pattern = ((AwkTypes.RegexLiteral) expr.args.get(0)).pattern;
                    } else {
                        pattern = toAwkString(args.get(0));
                    }
                    String replacement = toAwkString(args.get(1));
                    String target = args.size() >= 3 ? toAwkString(args.get(2)) : ctx.line;
                    boolean global = name.equals("gsub");
                    try {
                        String result = global
                            ? target.replaceAll(pattern, Matcher.quoteReplacement(replacement))
                            : target.replaceFirst(pattern, Matcher.quoteReplacement(replacement));
                        if (args.size() >= 3 && expr.args.get(2) instanceof AwkTypes.FieldRef) {
                            setTargetValue(expr.args.get(2), result);
                        } else if (args.size() < 3) {
                            setCurrentLine(result);
                        }
                        return target.equals(result) ? 0.0 : 1.0;
                    } catch (Exception e) {
                        return 0.0;
                    }
                }
                return 0.0;
            case "match":
                if (args.size() >= 2) {
                    String str = toAwkString(args.get(0));
                    String pattern;
                    if (expr.args.get(1) instanceof AwkTypes.RegexLiteral) {
                        pattern = ((AwkTypes.RegexLiteral) expr.args.get(1)).pattern;
                    } else {
                        pattern = toAwkString(args.get(1));
                    }
                    try {
                        Matcher m = Pattern.compile(pattern).matcher(str);
                        if (m.find()) {
                            ctx.vars.put("RLENGTH", (double) m.group().length());
                            ctx.vars.put("RSTART", (double) (m.start() + 1));
                            return (double) (m.start() + 1);
                        }
                        ctx.vars.put("RLENGTH", -1.0);
                        ctx.vars.put("RSTART", 0.0);
                        return 0.0;
                    } catch (Exception e) {
                        return 0.0;
                    }
                }
                return 0.0;
            case "sprintf":
                return sprintf(toAwkString(args.get(0)), args.subList(1, args.size()));
            case "int":
                return args.isEmpty() ? 0.0 : Math.floor(toNumber(args.get(0)));
            case "sqrt":
                return args.isEmpty() ? 0.0 : Math.sqrt(toNumber(args.get(0)));
            case "exp":
                return args.isEmpty() ? 0.0 : Math.exp(toNumber(args.get(0)));
            case "log":
                return args.isEmpty() ? 0.0 : Math.log(toNumber(args.get(0)));
            case "sin":
                return args.isEmpty() ? 0.0 : Math.sin(toNumber(args.get(0)));
            case "cos":
                return args.isEmpty() ? 0.0 : Math.cos(toNumber(args.get(0)));
            case "rand":
                return Math.random();
            case "srand":
                return 1.0;
            case "toupper":
                return args.isEmpty() ? "" : toAwkString(args.get(0)).toUpperCase();
            case "tolower":
                return args.isEmpty() ? "" : toAwkString(args.get(0)).toLowerCase();
            case "system":
                return 0.0; // Not supported
            default:
                // User-defined function
                AwkTypes.AwkFunctionDef func = ctx.functions.get(name);
                if (func != null) {
                    return callUserFunction(func, expr.args);
                }
                return "";
        }
    }

    private Object callUserFunction(AwkTypes.AwkFunctionDef func, List<AwkTypes.AwkExpr> argExprs) {
        ctx.currentRecursionDepth++;
        if (ctx.currentRecursionDepth > ctx.maxRecursionDepth) {
            ctx.currentRecursionDepth--;
            return "";
        }

        Map<String, Object> saved = new HashMap<>();
        for (String param : func.params) {
            saved.put(param, ctx.vars.get(param));
        }

        List<String> createdAliases = new ArrayList<>();
        for (int i = 0; i < func.params.size(); i++) {
            String param = func.params.get(i);
            if (i < argExprs.size()) {
                AwkTypes.AwkExpr arg = argExprs.get(i);
                if (arg instanceof AwkTypes.Variable) {
                    ctx.arrayAliases.put(param, ((AwkTypes.Variable) arg).name);
                    createdAliases.add(param);
                }
                ctx.vars.put(param, evalExpr(arg));
            } else {
                ctx.vars.put(param, "");
            }
        }

        ctx.hasReturn = false;
        ctx.returnValue = null;
        executeStatements(func.body.statements);
        Object result = ctx.returnValue != null ? ctx.returnValue : "";

        for (String param : func.params) {
            if (saved.containsKey(param)) {
                ctx.vars.put(param, saved.get(param));
            } else {
                ctx.vars.remove(param);
            }
        }
        for (String alias : createdAliases) {
            ctx.arrayAliases.remove(alias);
        }

        ctx.currentRecursionDepth--;
        return result;
    }

    // ─── sprintf implementation ────────────────────────────────

    private String sprintf(String format, List<Object> args) {
        StringBuilder result = new StringBuilder();
        int argIndex = 0;
        for (int i = 0; i < format.length(); i++) {
            char c = format.charAt(i);
            if (c == '%' && i + 1 < format.length()) {
                if (format.charAt(i + 1) == '%') {
                    result.append('%');
                    i++;
                    continue;
                }
                // Parse format specifier
                int start = i;
                i++;
                // Skip flags
                while (i < format.length() && "-+ #0".indexOf(format.charAt(i)) >= 0) i++;
                // Skip width
                while (i < format.length() && Character.isDigit(format.charAt(i))) i++;
                // Skip precision
                if (i < format.length() && format.charAt(i) == '.') {
                    i++;
                    while (i < format.length() && Character.isDigit(format.charAt(i))) i++;
                }
                if (i < format.length()) {
                    char specifier = format.charAt(i);
                    Object arg = argIndex < args.size() ? args.get(argIndex++) : "";
                    switch (specifier) {
                        case 'd': case 'i':
                            result.append((int) toNumber(arg));
                            break;
                        case 'f':
                            result.append(toNumber(arg));
                            break;
                        case 'e':
                            result.append(String.format(java.util.Locale.US, "%e", toNumber(arg)));
                            break;
                        case 'E':
                            result.append(String.format(java.util.Locale.US, "%E", toNumber(arg)));
                            break;
                        case 'g':
                            result.append(String.format(java.util.Locale.US, "%g", toNumber(arg)));
                            break;
                        case 'G':
                            result.append(String.format(java.util.Locale.US, "%G", toNumber(arg)));
                            break;
                        case 'o':
                            result.append(Integer.toOctalString((int) toNumber(arg)));
                            break;
                        case 'x':
                            result.append(Integer.toHexString((int) toNumber(arg)));
                            break;
                        case 'X':
                            result.append(Integer.toHexString((int) toNumber(arg)).toUpperCase());
                            break;
                        case 'c':
                            result.append((char) ((int) toNumber(arg) & 0xFF));
                            break;
                        case 's':
                            result.append(toAwkString(arg));
                            break;
                        default:
                            result.append(format.substring(start, i + 1));
                            break;
                    }
                }
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    // ─── Field Management ──────────────────────────────────────

    private void setCurrentLine(String line) {
        ctx.line = line;
        splitFields();
    }

    private void splitFields() {
        String line = ctx.line;
        if (line.isEmpty()) {
            ctx.fields = new String[0];
            ctx.NF = 0;
            return;
        }
        String[] parts;
        if (ctx.FS.equals(" ")) {
            parts = line.trim().split("\\s+");
        } else if (ctx.FS.equals("")) {
            parts = line.split("");
        } else {
            parts = line.split(Pattern.quote(ctx.FS), -1);
        }
        ctx.fields = parts;
        ctx.NF = parts.length;
    }

    private Object getField(int index) {
        if (index == 0) return ctx.line;
        if (index < 1 || index > ctx.fields.length) return "";
        return ctx.fields[index - 1];
    }

    private void setField(int index, String value) {
        if (index < 1) return;
        if (index > ctx.fields.length) {
            String[] newFields = new String[index];
            System.arraycopy(ctx.fields, 0, newFields, 0, ctx.fields.length);
            for (int i = ctx.fields.length; i < index - 1; i++) {
                newFields[i] = "";
            }
            newFields[index - 1] = value;
            ctx.fields = newFields;
        } else {
            ctx.fields[index - 1] = value;
        }
        ctx.NF = ctx.fields.length;
        ctx.line = String.join(ctx.OFS, ctx.fields);
    }

    // ─── Variable Access ───────────────────────────────────────

    private Object getVariable(String name) {
        switch (name) {
            case "NR": return (double) ctx.NR;
            case "FNR": return (double) ctx.FNR;
            case "NF": return (double) ctx.NF;
            case "FS": return ctx.FS;
            case "OFS": return ctx.OFS;
            case "ORS": return ctx.ORS;
            case "SUBSEP": return ctx.SUBSEP;
            case "FILENAME": return ctx.FILENAME;
            case "RLENGTH": return ctx.vars.getOrDefault("RLENGTH", -1.0);
            case "RSTART": return ctx.vars.getOrDefault("RSTART", 0.0);
            case "ARGC": return ctx.vars.getOrDefault("ARGC", 0.0);
            default:
                if (name.equals("length")) {
                    Object val = ctx.vars.get(name);
                    if (val != null) return val;
                    return (double) ctx.line.length();
                }
                Object val = ctx.vars.get(name);
                return val != null ? val : "";
        }
    }

    // ─── Type Coercion ─────────────────────────────────────────

    private static boolean isTruthy(Object val) {
        if (val == null) return false;
        if (val instanceof Double) return (Double) val != 0.0;
        if (val instanceof String) return !((String) val).isEmpty();
        return true;
    }

    private static double toNumber(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Double) return (Double) val;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try {
            String s = val.toString().trim();
            if (s.isEmpty()) return 0.0;
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static String toAwkString(Object val) {
        if (val == null) return "";
        if (val instanceof Double) {
            double d = (Double) val;
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.valueOf((long) d);
            }
            return String.valueOf(d);
        }
        return val.toString();
    }

    private static boolean looksLikeNumber(Object val) {
        if (val instanceof Number) return true;
        try {
            String s = val.toString().trim();
            if (s.isEmpty()) return false;
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
