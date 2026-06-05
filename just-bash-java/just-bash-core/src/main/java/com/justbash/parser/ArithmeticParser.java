package com.justbash.parser;

import com.justbash.ast.expression.*;
import com.justbash.interpreter.errors.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Recursive descent parser for bash arithmetic expressions.
 *
 * Grammar (simplified for MVP):
 *   expr       ::= assignment
 *   assignment ::= ternary (('=' | '+=' | '-=' | '*=' | '/=' | '%=') ternary)?
 *   ternary    ::= logicalOr ('?' ternary ':' ternary)?
 *   logicalOr  ::= logicalAnd ('||' logicalAnd)*
 *   logicalAnd ::= equality ('&&' equality)*
 *   equality   ::= comparison (('==' | '!=') comparison)*
 *   comparison ::= shift (('<=' | '>=' | '<' | '>') shift)*
 *   shift      ::= additive (('<<' | '>>') additive)*
 *   additive   ::= multiplicative (('+' | '-') multiplicative)*
 *   multiplicative ::= power (('*' | '/' | '%') power)*
 *   power      ::= unary ('**' power)?
 *   unary      ::= ('++' | '--' | '+' | '-' | '!') unary | postfix
 *   postfix    ::= primary ('++' | '--')?
 *   primary    ::= NUMBER | VARIABLE | SPECIAL_VAR | '(' expr ')'
 */
public class ArithmeticParser {

    public static ArithExpr parse(String input, int line) {
        ArithmeticParser parser = new ArithmeticParser(input, line);
        return parser.parseExpr();
    }

    private final String input;
    private final int line;
    private int pos = 0;

    private ArithmeticParser(String input, int line) {
        this.input = input;
        this.line = line;
    }

    private ArithExpr parseExpr() {
        return parseAssignment();
    }

    private ArithExpr parseAssignment() {
        ArithExpr left = parseTernary();
        if (match("=") || match("+=") || match("-=") || match("*=") || match("/=") || match("%=")) {
            String op = previousOp();
            ArithExpr right = parseTernary();
            if (left instanceof ArithVariableNode var) {
                return new ArithAssignmentNode(line, op, var.name(), right);
            } else if (left instanceof ArithArrayElementNode arr) {
                // For MVP, treat array element assignment as variable assignment
                return new ArithAssignmentNode(line, op, arr.toString(), right);
            }
            // Invalid assignment target — return as-is for MVP
        }
        return left;
    }

    private ArithExpr parseTernary() {
        ArithExpr condition = parseLogicalOr();
        if (match("?")) {
            ArithExpr consequent = parseTernary();
            expect(":");
            ArithExpr alternate = parseTernary();
            return new ArithTernaryNode(line, condition, consequent, alternate);
        }
        return condition;
    }

    private ArithExpr parseLogicalOr() {
        ArithExpr left = parseLogicalAnd();
        while (match("||")) {
            ArithExpr right = parseLogicalAnd();
            left = new ArithBinaryNode(line, "||", left, right);
        }
        return left;
    }

    private ArithExpr parseLogicalAnd() {
        ArithExpr left = parseEquality();
        while (match("&&")) {
            ArithExpr right = parseEquality();
            left = new ArithBinaryNode(line, "&&", left, right);
        }
        return left;
    }

    private ArithExpr parseEquality() {
        ArithExpr left = parseComparison();
        while (true) {
            if (match("==")) {
                ArithExpr right = parseComparison();
                left = new ArithBinaryNode(line, "==", left, right);
            } else if (match("!=")) {
                ArithExpr right = parseComparison();
                left = new ArithBinaryNode(line, "!=", left, right);
            } else {
                break;
            }
        }
        return left;
    }

    private ArithExpr parseComparison() {
        ArithExpr left = parseShift();
        while (true) {
            if (match("<=") || match(">=") || match("<") || match(">")) {
                String op = previousOp();
                ArithExpr right = parseShift();
                left = new ArithBinaryNode(line, op, left, right);
            } else {
                break;
            }
        }
        return left;
    }

    private ArithExpr parseShift() {
        ArithExpr left = parseAdditive();
        while (true) {
            if (match("<<") || match(">>")) {
                String op = previousOp();
                ArithExpr right = parseAdditive();
                left = new ArithBinaryNode(line, op, left, right);
            } else {
                break;
            }
        }
        return left;
    }

    private ArithExpr parseAdditive() {
        ArithExpr left = parseMultiplicative();
        while (true) {
            if (match("+") || match("-")) {
                String op = previousOp();
                ArithExpr right = parseMultiplicative();
                left = new ArithBinaryNode(line, op, left, right);
            } else {
                break;
            }
        }
        return left;
    }

    private ArithExpr parseMultiplicative() {
        ArithExpr left = parsePower();
        while (true) {
            if (match("*") || match("/") || match("%")) {
                String op = previousOp();
                ArithExpr right = parsePower();
                left = new ArithBinaryNode(line, op, left, right);
            } else {
                break;
            }
        }
        return left;
    }

    private ArithExpr parsePower() {
        ArithExpr left = parseUnary();
        if (match("**")) {
            ArithExpr right = parsePower(); // right-associative
            return new ArithBinaryNode(line, "**", left, right);
        }
        return left;
    }

    private ArithExpr parseUnary() {
        if (match("++") || match("--")) {
            String op = previousOp();
            ArithExpr operand = parseUnary();
            return new ArithUnaryNode(line, op, operand, true);
        }
        if (match("+") || match("-") || match("!")) {
            String op = previousOp();
            ArithExpr operand = parseUnary();
            return new ArithUnaryNode(line, op, operand, true);
        }
        return parsePostfix();
    }

    private ArithExpr parsePostfix() {
        ArithExpr primary = parsePrimary();
        if (match("++") || match("--")) {
            String op = previousOp();
            return new ArithUnaryNode(line, op, primary, false);
        }
        return primary;
    }

    private ArithExpr parsePrimary() {
        skipWhitespace();
        if (match("(")) {
            ArithExpr expr = parseExpr();
            expect(")");
            return new ArithGroupNode(line, expr);
        }

        String token = readToken();
        if (token.isEmpty()) {
            return new ArithNumberNode(line, 0);
        }

        // Number
        if (token.matches("-?\\d+")) {
            return new ArithNumberNode(line, Integer.parseInt(token));
        }

        // Special variable
        if (token.matches("[?#$@*!-]")) {
            return new ArithSpecialVarNode(line, token);
        }

        // Variable or array element
        if (token.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            if (match("[")) {
                ArithExpr index = parseExpr();
                expect("]");
                return new ArithArrayElementNode(line, token, index);
            }
            return new ArithVariableNode(line, token);
        }

        // Fallback: treat as number 0
        return new ArithNumberNode(line, 0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tokenizer helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String readToken() {
        skipWhitespace();
        if (isAtEnd()) return "";

        char c = peek();
        if (c == '(' || c == ')' || c == '?' || c == ':' ||
            c == '+' || c == '-' || c == '*' || c == '/' || c == '%' ||
            c == '<' || c == '>' || c == '=' || c == '!' ||
            c == '&' || c == '|' || c == '^' || c == '~') {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        while (!isAtEnd()) {
            c = peek();
            if (Character.isWhitespace(c) ||
                c == '(' || c == ')' || c == '?' || c == ':' ||
                c == '+' || c == '-' || c == '*' || c == '/' || c == '%' ||
                c == '<' || c == '>' || c == '=' || c == '!' ||
                c == '&' || c == '|' || c == '^' || c == '~' || c == '[' || c == ']') {
                break;
            }
            sb.append(advance());
        }
        return sb.toString();
    }

    private boolean match(String expected) {
        skipWhitespace();
        if (input.startsWith(expected, pos)) {
            pos += expected.length();
            lastOp = expected;
            return true;
        }
        return false;
    }

    private String lastOp = "";
    private String previousOp() { return lastOp; }

    private void expect(String expected) {
        skipWhitespace();
        if (!input.startsWith(expected, pos)) {
            throw new ParseException("Expected " + expected + " in arithmetic expression",
                line, pos);
        }
        pos += expected.length();
    }

    private void skipWhitespace() {
        while (!isAtEnd() && Character.isWhitespace(peek())) {
            pos++;
        }
    }

    private char peek() {
        return isAtEnd() ? '\0' : input.charAt(pos);
    }

    private char advance() {
        return input.charAt(pos++);
    }

    private boolean isAtEnd() {
        return pos >= input.length();
    }
}
