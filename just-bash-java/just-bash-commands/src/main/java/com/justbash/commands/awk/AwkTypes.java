package com.justbash.commands.awk;

import java.util.List;
import java.util.Map;

public class AwkTypes {

    // ─── Expressions ───────────────────────────────────────────

    public enum ExprType {
        NUMBER, STRING, REGEX, FIELD, VARIABLE, ARRAY_ACCESS,
        BINARY, UNARY, TERNARY, CALL, ASSIGNMENT,
        PRE_INCREMENT, PRE_DECREMENT, POST_INCREMENT, POST_DECREMENT,
        IN, GETLINE, TUPLE
    }

    public static abstract class AwkExpr {
        public abstract ExprType type();
    }

    public static class NumberLiteral extends AwkExpr {
        public final double value;
        NumberLiteral(double value) { this.value = value; }
        @Override public ExprType type() { return ExprType.NUMBER; }
    }

    public static class StringLiteral extends AwkExpr {
        public final String value;
        StringLiteral(String value) { this.value = value; }
        @Override public ExprType type() { return ExprType.STRING; }
    }

    public static class RegexLiteral extends AwkExpr {
        public final String pattern;
        RegexLiteral(String pattern) { this.pattern = pattern; }
        @Override public ExprType type() { return ExprType.REGEX; }
    }

    public static class FieldRef extends AwkExpr {
        public final AwkExpr index;
        FieldRef(AwkExpr index) { this.index = index; }
        @Override public ExprType type() { return ExprType.FIELD; }
    }

    public static class Variable extends AwkExpr {
        public final String name;
        Variable(String name) { this.name = name; }
        @Override public ExprType type() { return ExprType.VARIABLE; }
    }

    public static class ArrayAccess extends AwkExpr {
        public final String array;
        public final AwkExpr key;
        ArrayAccess(String array, AwkExpr key) {
            this.array = array;
            this.key = key;
        }
        @Override public ExprType type() { return ExprType.ARRAY_ACCESS; }
    }

    public static class BinaryOp extends AwkExpr {
        public final String operator;
        public final AwkExpr left;
        public final AwkExpr right;
        BinaryOp(String operator, AwkExpr left, AwkExpr right) {
            this.operator = operator;
            this.left = left;
            this.right = right;
        }
        @Override public ExprType type() { return ExprType.BINARY; }
    }

    public static class UnaryOp extends AwkExpr {
        public final String operator;
        public final AwkExpr operand;
        UnaryOp(String operator, AwkExpr operand) {
            this.operator = operator;
            this.operand = operand;
        }
        @Override public ExprType type() { return ExprType.UNARY; }
    }

    public static class TernaryOp extends AwkExpr {
        public final AwkExpr condition;
        public final AwkExpr consequent;
        public final AwkExpr alternate;
        TernaryOp(AwkExpr condition, AwkExpr consequent, AwkExpr alternate) {
            this.condition = condition;
            this.consequent = consequent;
            this.alternate = alternate;
        }
        @Override public ExprType type() { return ExprType.TERNARY; }
    }

    public static class FunctionCall extends AwkExpr {
        public final String name;
        public final List<AwkExpr> args;
        FunctionCall(String name, List<AwkExpr> args) {
            this.name = name;
            this.args = args;
        }
        @Override public ExprType type() { return ExprType.CALL; }
    }

    public static class Assignment extends AwkExpr {
        public final String operator;
        public final AwkExpr target;
        public final AwkExpr value;
        Assignment(String operator, AwkExpr target, AwkExpr value) {
            this.operator = operator;
            this.target = target;
            this.value = value;
        }
        @Override public ExprType type() { return ExprType.ASSIGNMENT; }
    }

    public static class PreIncrement extends AwkExpr {
        public final AwkExpr operand;
        PreIncrement(AwkExpr operand) { this.operand = operand; }
        @Override public ExprType type() { return ExprType.PRE_INCREMENT; }
    }

    public static class PreDecrement extends AwkExpr {
        public final AwkExpr operand;
        PreDecrement(AwkExpr operand) { this.operand = operand; }
        @Override public ExprType type() { return ExprType.PRE_DECREMENT; }
    }

    public static class PostIncrement extends AwkExpr {
        public final AwkExpr operand;
        PostIncrement(AwkExpr operand) { this.operand = operand; }
        @Override public ExprType type() { return ExprType.POST_INCREMENT; }
    }

    public static class PostDecrement extends AwkExpr {
        public final AwkExpr operand;
        PostDecrement(AwkExpr operand) { this.operand = operand; }
        @Override public ExprType type() { return ExprType.POST_DECREMENT; }
    }

    public static class InExpr extends AwkExpr {
        public final AwkExpr key;
        public final String array;
        InExpr(AwkExpr key, String array) {
            this.key = key;
            this.array = array;
        }
        @Override public ExprType type() { return ExprType.IN; }
    }

    public static class GetlineExpr extends AwkExpr {
        public final String variable;
        public final AwkExpr file;
        public final AwkExpr command;
        GetlineExpr(String variable, AwkExpr file, AwkExpr command) {
            this.variable = variable;
            this.file = file;
            this.command = command;
        }
        @Override public ExprType type() { return ExprType.GETLINE; }
    }

    public static class TupleExpr extends AwkExpr {
        public final List<AwkExpr> elements;
        TupleExpr(List<AwkExpr> elements) { this.elements = elements; }
        @Override public ExprType type() { return ExprType.TUPLE; }
    }

    // ─── Statements ────────────────────────────────────────────

    public enum StmtType {
        EXPR_STMT, PRINT, PRINTF, IF, WHILE, DO_WHILE, FOR, FOR_IN,
        BREAK, CONTINUE, NEXT, NEXT_FILE, EXIT, RETURN, DELETE, BLOCK
    }

    public static abstract class AwkStmt {
        public abstract StmtType type();
    }

    public static class ExpressionStmt extends AwkStmt {
        public final AwkExpr expression;
        ExpressionStmt(AwkExpr expression) { this.expression = expression; }
        @Override public StmtType type() { return StmtType.EXPR_STMT; }
    }

    public static class PrintStmt extends AwkStmt {
        public final List<AwkExpr> args;
        public final String redirect; // ">" or ">>" or null
        public final AwkExpr file;
        PrintStmt(List<AwkExpr> args, String redirect, AwkExpr file) {
            this.args = args;
            this.redirect = redirect;
            this.file = file;
        }
        @Override public StmtType type() { return StmtType.PRINT; }
    }

    public static class PrintfStmt extends AwkStmt {
        public final AwkExpr format;
        public final List<AwkExpr> args;
        public final String redirect;
        public final AwkExpr file;
        PrintfStmt(AwkExpr format, List<AwkExpr> args, String redirect, AwkExpr file) {
            this.format = format;
            this.args = args;
            this.redirect = redirect;
            this.file = file;
        }
        @Override public StmtType type() { return StmtType.PRINTF; }
    }

    public static class IfStmt extends AwkStmt {
        public final AwkExpr condition;
        public final AwkStmt consequent;
        public final AwkStmt alternate;
        IfStmt(AwkExpr condition, AwkStmt consequent, AwkStmt alternate) {
            this.condition = condition;
            this.consequent = consequent;
            this.alternate = alternate;
        }
        @Override public StmtType type() { return StmtType.IF; }
    }

    public static class WhileStmt extends AwkStmt {
        public final AwkExpr condition;
        public final AwkStmt body;
        WhileStmt(AwkExpr condition, AwkStmt body) {
            this.condition = condition;
            this.body = body;
        }
        @Override public StmtType type() { return StmtType.WHILE; }
    }

    public static class DoWhileStmt extends AwkStmt {
        public final AwkStmt body;
        public final AwkExpr condition;
        DoWhileStmt(AwkStmt body, AwkExpr condition) {
            this.body = body;
            this.condition = condition;
        }
        @Override public StmtType type() { return StmtType.DO_WHILE; }
    }

    public static class ForStmt extends AwkStmt {
        public final AwkExpr init;
        public final AwkExpr condition;
        public final AwkExpr update;
        public final AwkStmt body;
        ForStmt(AwkExpr init, AwkExpr condition, AwkExpr update, AwkStmt body) {
            this.init = init;
            this.condition = condition;
            this.update = update;
            this.body = body;
        }
        @Override public StmtType type() { return StmtType.FOR; }
    }

    public static class ForInStmt extends AwkStmt {
        public final String variable;
        public final String array;
        public final AwkStmt body;
        ForInStmt(String variable, String array, AwkStmt body) {
            this.variable = variable;
            this.array = array;
            this.body = body;
        }
        @Override public StmtType type() { return StmtType.FOR_IN; }
    }

    public static class BreakStmt extends AwkStmt {
        @Override public StmtType type() { return StmtType.BREAK; }
    }

    public static class ContinueStmt extends AwkStmt {
        @Override public StmtType type() { return StmtType.CONTINUE; }
    }

    public static class NextStmt extends AwkStmt {
        @Override public StmtType type() { return StmtType.NEXT; }
    }

    public static class NextFileStmt extends AwkStmt {
        @Override public StmtType type() { return StmtType.NEXT_FILE; }
    }

    public static class ExitStmt extends AwkStmt {
        public final AwkExpr code;
        ExitStmt(AwkExpr code) { this.code = code; }
        @Override public StmtType type() { return StmtType.EXIT; }
    }

    public static class ReturnStmt extends AwkStmt {
        public final AwkExpr value;
        ReturnStmt(AwkExpr value) { this.value = value; }
        @Override public StmtType type() { return StmtType.RETURN; }
    }

    public static class DeleteStmt extends AwkStmt {
        public final AwkExpr target;
        DeleteStmt(AwkExpr target) { this.target = target; }
        @Override public StmtType type() { return StmtType.DELETE; }
    }

    public static class BlockStmt extends AwkStmt {
        public final List<AwkStmt> statements;
        BlockStmt(List<AwkStmt> statements) { this.statements = statements; }
        @Override public StmtType type() { return StmtType.BLOCK; }
    }

    // ─── Patterns ──────────────────────────────────────────────

    public enum PatternType {
        BEGIN, END, EXPR_PATTERN, REGEX_PATTERN, RANGE
    }

    public static abstract class AwkPattern {
        public abstract PatternType type();
    }

    public static class BeginPattern extends AwkPattern {
        @Override public PatternType type() { return PatternType.BEGIN; }
    }

    public static class EndPattern extends AwkPattern {
        @Override public PatternType type() { return PatternType.END; }
    }

    public static class ExprPattern extends AwkPattern {
        public final AwkExpr expression;
        ExprPattern(AwkExpr expression) { this.expression = expression; }
        @Override public PatternType type() { return PatternType.EXPR_PATTERN; }
    }

    public static class RegexPattern extends AwkPattern {
        public final String pattern;
        RegexPattern(String pattern) { this.pattern = pattern; }
        @Override public PatternType type() { return PatternType.REGEX_PATTERN; }
    }

    public static class RangePattern extends AwkPattern {
        public final AwkPattern start;
        public final AwkPattern end;
        RangePattern(AwkPattern start, AwkPattern end) {
            this.start = start;
            this.end = end;
        }
        @Override public PatternType type() { return PatternType.RANGE; }
    }

    // ─── Program Structure ─────────────────────────────────────

    public static class AwkRule {
        public final AwkPattern pattern;
        public final BlockStmt action;
        AwkRule(AwkPattern pattern, BlockStmt action) {
            this.pattern = pattern;
            this.action = action;
        }
    }

    public static class AwkFunctionDef {
        public final String name;
        public final List<String> params;
        public final BlockStmt body;
        AwkFunctionDef(String name, List<String> params, BlockStmt body) {
            this.name = name;
            this.params = params;
            this.body = body;
        }
    }

    public static class AwkProgram {
        public final List<AwkFunctionDef> functions;
        public final List<AwkRule> rules;
        AwkProgram(List<AwkFunctionDef> functions, List<AwkRule> rules) {
            this.functions = functions;
            this.rules = rules;
        }
    }

    // ─── Runtime State ─────────────────────────────────────────

    public static class RuntimeContext {
        public String line = "";
        public String FS = " ";
        public String OFS = " ";
        public String ORS = "\n";
        public String SUBSEP = "\034";
        public int NR = 0;
        public int FNR = 0;
        public int NF = 0;
        public String FILENAME = "";
        public String output = "";
        public int exitCode = 0;
        public boolean shouldExit = false;
        public boolean shouldNext = false;
        public boolean shouldNextFile = false;
        public boolean inEndBlock = false;
        public boolean hasReturn = false;
        public Object returnValue = null;
        public int currentRecursionDepth = 0;
        public int maxRecursionDepth = 100;
        public int maxIterations = 10000;
        public int iterationCount = 0;
        public final Map<String, Object> vars = new java.util.HashMap<>();
        public final Map<String, Map<String, Object>> arrays = new java.util.HashMap<>();
        public final Map<String, AwkFunctionDef> functions = new java.util.HashMap<>();
        public final Map<String, String> arrayAliases = new java.util.HashMap<>();
        public String[] fields = new String[0];
        public java.util.List<String> lines = new java.util.ArrayList<>();
        public int lineIndex = -1;
        public final Map<String, Boolean> rangeStates = new java.util.HashMap<>();
    }
}
