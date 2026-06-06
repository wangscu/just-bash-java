package com.justbash.commands.awk;

import java.util.ArrayList;
import java.util.List;

public class AwkParser {

    private String input;
    private int pos;
    private int length;

    public AwkTypes.AwkProgram parse(String input) {
        this.input = input;
        this.pos = 0;
        this.length = input.length();

        List<AwkTypes.AwkFunctionDef> functions = new ArrayList<>();
        List<AwkTypes.AwkRule> rules = new ArrayList<>();

        skipWhitespaceAndNewlines();
        while (pos < length) {
            skipWhitespaceAndNewlines();
            if (pos >= length) break;

            if (peek() == '#') {
                skipComment();
                continue;
            }

            // Check for function definition
            if (matchKeyword("function") || matchKeyword("func")) {
                functions.add(parseFunctionDef());
                continue;
            }

            // Parse rule
            AwkTypes.AwkRule rule = parseRule();
            if (rule != null) {
                rules.add(rule);
            }

            skipWhitespaceAndNewlines();
        }

        return new AwkTypes.AwkProgram(functions, rules);
    }

    private AwkTypes.AwkRule parseRule() {
        skipWhitespaceAndNewlines();
        if (pos >= length) return null;

        AwkTypes.AwkPattern pattern = null;

        // Check for BEGIN/END
        if (matchKeyword("BEGIN")) {
            pattern = new AwkTypes.BeginPattern();
        } else if (matchKeyword("END")) {
            pattern = new AwkTypes.EndPattern();
        } else {
            // Try to parse pattern
            pattern = parsePattern();
        }

        skipWhitespace();

        // Parse action block
        AwkTypes.BlockStmt action;
        if (peek() == '{') {
            action = parseBlock();
        } else {
            // Default action: { print }
            List<AwkTypes.AwkStmt> stmts = new ArrayList<>();
            stmts.add(new AwkTypes.PrintStmt(new ArrayList<>(), null, null));
            action = new AwkTypes.BlockStmt(stmts);
        }

        return new AwkTypes.AwkRule(pattern, action);
    }

    private AwkTypes.AwkPattern parsePattern() {
        skipWhitespace();
        if (pos >= length) return null;

        // Check for range pattern start
        AwkTypes.AwkPattern first = parseSinglePattern();
        if (first == null) return null;

        skipWhitespace();
        if (match(",")) {
            AwkTypes.AwkPattern second = parseSinglePattern();
            return new AwkTypes.RangePattern(first, second);
        }

        return first;
    }

    private AwkTypes.AwkPattern parseSinglePattern() {
        skipWhitespace();
        if (pos >= length) return null;

        // Regex pattern /.../
        if (peek() == '/') {
            return parseRegexPattern();
        }

        // Expression pattern
        AwkTypes.AwkExpr expr = parseExpression();
        if (expr == null) return null;

        return new AwkTypes.ExprPattern(expr);
    }

    private AwkTypes.RegexPattern parseRegexPattern() {
        consume(); // consume '/'
        StringBuilder pattern = new StringBuilder();
        while (pos < length && peek() != '/') {
            if (peek() == '\\' && pos + 1 < length) {
                pattern.append(consume());
                pattern.append(consume());
            } else {
                pattern.append(consume());
            }
        }
        if (peek() == '/') consume();
        return new AwkTypes.RegexPattern(pattern.toString());
    }

    private AwkTypes.AwkFunctionDef parseFunctionDef() {
        skipWhitespace();
        String name = parseIdentifier();
        expect('(');
        List<String> params = new ArrayList<>();
        skipWhitespace();
        if (peek() != ')') {
            params.add(parseIdentifier());
            while (match(",")) {
                params.add(parseIdentifier());
            }
        }
        expect(')');
        AwkTypes.BlockStmt body = parseBlock();
        return new AwkTypes.AwkFunctionDef(name, params, body);
    }

    private AwkTypes.BlockStmt parseBlock() {
        expect('{');
        List<AwkTypes.AwkStmt> statements = new ArrayList<>();
        while (peek() != '}') {
            skipWhitespaceAndNewlines();
            if (peek() == '}') break;
            AwkTypes.AwkStmt stmt = parseStatement();
            if (stmt == null && peek() != '}') {
                throw new RuntimeException("parseStatement() returned null at position " + pos +
                    ", remaining input: '" + input.substring(pos) + "'");
            }
            if (stmt != null) {
                statements.add(stmt);
            }
            skipWhitespaceAndNewlines();
        }
        expect('}');
        return new AwkTypes.BlockStmt(statements);
    }

    private AwkTypes.AwkStmt parseStatement() {
        skipWhitespaceAndNewlines();
        if (pos >= length) return null;

        char c = peek();
        int startPos = pos;

        if (c == '{') {
            return parseBlock();
        }

        if (matchKeyword("if")) {
            return parseIf();
        }
        if (matchKeyword("while")) {
            return parseWhile();
        }
        if (matchKeyword("do")) {
            return parseDoWhile();
        }
        if (matchKeyword("for")) {
            return parseFor();
        }
        if (matchKeyword("break")) {
            return new AwkTypes.BreakStmt();
        }
        if (matchKeyword("continue")) {
            return new AwkTypes.ContinueStmt();
        }
        if (matchKeyword("next")) {
            return new AwkTypes.NextStmt();
        }
        if (matchKeyword("nextfile")) {
            return new AwkTypes.NextFileStmt();
        }
        if (matchKeyword("exit")) {
            AwkTypes.AwkExpr code = null;
            skipWhitespace();
            if (pos < length && peek() != ';' && peek() != '\n' && peek() != '}') {
                code = parseExpression();
            }
            return new AwkTypes.ExitStmt(code);
        }
        if (matchKeyword("return")) {
            AwkTypes.AwkExpr value = null;
            skipWhitespace();
            if (pos < length && peek() != ';' && peek() != '\n' && peek() != '}') {
                value = parseExpression();
            }
            return new AwkTypes.ReturnStmt(value);
        }
        if (matchKeyword("delete")) {
            AwkTypes.AwkExpr target = parseExpression();
            return new AwkTypes.DeleteStmt(target);
        }
        if (matchKeyword("print")) {
            return parsePrint();
        }
        if (matchKeyword("printf")) {
            return parsePrintf();
        }

        // Expression statement
        AwkTypes.AwkExpr expr = parseExpression();
        if (expr != null) {
            return new AwkTypes.ExpressionStmt(expr);
        }

        return null;
    }

    private AwkTypes.AwkStmt parseIf() {
        expect('(');
        AwkTypes.AwkExpr condition = parseExpression();
        expect(')');
        AwkTypes.AwkStmt consequent = parseStatement();
        AwkTypes.AwkStmt alternate = null;
        skipWhitespace();
        if (matchKeyword("else")) {
            alternate = parseStatement();
        }
        return new AwkTypes.IfStmt(condition, consequent, alternate);
    }

    private AwkTypes.AwkStmt parseWhile() {
        expect('(');
        AwkTypes.AwkExpr condition = parseExpression();
        expect(')');
        AwkTypes.AwkStmt body = parseStatement();
        return new AwkTypes.WhileStmt(condition, body);
    }

    private AwkTypes.AwkStmt parseDoWhile() {
        AwkTypes.AwkStmt body = parseStatement();
        expectKeyword("while");
        expect('(');
        AwkTypes.AwkExpr condition = parseExpression();
        expect(')');
        return new AwkTypes.DoWhileStmt(body, condition);
    }

    private AwkTypes.AwkStmt parseFor() {
        expect('(');
        skipWhitespace();

        // Check for for-in
        if (pos < length && Character.isLetter(peek())) {
            int savePos = pos;
            String name = parseIdentifier();
            skipWhitespace();
            if (matchKeyword("in")) {
                String array = parseIdentifier();
                expect(')');
                AwkTypes.AwkStmt body = parseStatement();
                return new AwkTypes.ForInStmt(name, array, body);
            }
            pos = savePos;
        }

        AwkTypes.AwkExpr init = null;
        if (peek() != ';') {
            init = parseExpression();
        }
        expect(';');
        AwkTypes.AwkExpr condition = null;
        if (peek() != ';') {
            condition = parseExpression();
        }
        expect(';');
        AwkTypes.AwkExpr update = null;
        if (peek() != ')') {
            update = parseExpression();
        }
        expect(')');
        AwkTypes.AwkStmt body = parseStatement();
        return new AwkTypes.ForStmt(init, condition, update, body);
    }

    private AwkTypes.AwkStmt parsePrint() {
        List<AwkTypes.AwkExpr> args = new ArrayList<>();
        skipWhitespace();
        if (pos < length && peek() != ';' && peek() != '\n' && peek() != '}' && peek() != '>') {
            args.add(parseExpression());
            while (match(",")) {
                args.add(parseExpression());
            }
        }
        String redirect = null;
        AwkTypes.AwkExpr file = null;
        skipWhitespace();
        if (match(">>")) {
            redirect = ">>";
            file = parseExpression();
        } else if (match(">")) {
            redirect = ">";
            file = parseExpression();
        }
        return new AwkTypes.PrintStmt(args, redirect, file);
    }

    private AwkTypes.AwkStmt parsePrintf() {
        AwkTypes.AwkExpr format = parseExpression();
        List<AwkTypes.AwkExpr> args = new ArrayList<>();
        while (match(",")) {
            args.add(parseExpression());
        }
        String redirect = null;
        AwkTypes.AwkExpr file = null;
        skipWhitespace();
        if (match(">>")) {
            redirect = ">>";
            file = parseExpression();
        } else if (match(">")) {
            redirect = ">";
            file = parseExpression();
        }
        return new AwkTypes.PrintfStmt(format, args, redirect, file);
    }

    // ─── Expression Parsing ────────────────────────────────────

    private AwkTypes.AwkExpr parseExpression() {
        return parseAssignment();
    }

    private AwkTypes.AwkExpr parseAssignment() {
        AwkTypes.AwkExpr left = parseConditional();
        skipWhitespace();
        String[] ops = {"+=", "-=", "*=", "/=", "%=", "^=", "="};
        for (String op : ops) {
            if (match(op)) {
                AwkTypes.AwkExpr right = parseAssignment();
                return new AwkTypes.Assignment(op, left, right);
            }
        }
        return left;
    }

    private AwkTypes.AwkExpr parseConditional() {
        AwkTypes.AwkExpr condition = parseOr();
        skipWhitespace();
        if (match("?")) {
            AwkTypes.AwkExpr consequent = parseExpression();
            expect(':');
            AwkTypes.AwkExpr alternate = parseConditional();
            return new AwkTypes.TernaryOp(condition, consequent, alternate);
        }
        return condition;
    }

    private AwkTypes.AwkExpr parseOr() {
        AwkTypes.AwkExpr left = parseAnd();
        while (true) {
            skipWhitespace();
            if (match("||")) {
                AwkTypes.AwkExpr right = parseAnd();
                left = new AwkTypes.BinaryOp("||", left, right);
            } else {
                break;
            }
        }
        return left;
    }

    private AwkTypes.AwkExpr parseAnd() {
        AwkTypes.AwkExpr left = parseIn();
        while (true) {
            skipWhitespace();
            if (match("&&")) {
                AwkTypes.AwkExpr right = parseIn();
                left = new AwkTypes.BinaryOp("&&", left, right);
            } else {
                break;
            }
        }
        return left;
    }

    private AwkTypes.AwkExpr parseIn() {
        AwkTypes.AwkExpr left = parseMatch();
        skipWhitespace();
        if (matchKeyword("in")) {
            String array = ((AwkTypes.Variable) left).name;
            return new AwkTypes.InExpr(left, array);
        }
        return left;
    }

    private AwkTypes.AwkExpr parseMatch() {
        AwkTypes.AwkExpr left = parseComparison();
        while (true) {
            skipWhitespace();
            if (match("!~")) {
                AwkTypes.AwkExpr right = parseComparison();
                left = new AwkTypes.BinaryOp("!~", left, right);
            } else if (match("~")) {
                AwkTypes.AwkExpr right = parseComparison();
                left = new AwkTypes.BinaryOp("~", left, right);
            } else {
                break;
            }
        }
        return left;
    }

    private AwkTypes.AwkExpr parseComparison() {
        AwkTypes.AwkExpr left = parseConcat();
        while (true) {
            skipWhitespace();
            if (match("<=")) {
                AwkTypes.AwkExpr right = parseConcat();
                left = new AwkTypes.BinaryOp("<=", left, right);
            } else if (match(">=")) {
                AwkTypes.AwkExpr right = parseConcat();
                left = new AwkTypes.BinaryOp(">=", left, right);
            } else if (match("==")) {
                AwkTypes.AwkExpr right = parseConcat();
                left = new AwkTypes.BinaryOp("==", left, right);
            } else if (match("!=")) {
                AwkTypes.AwkExpr right = parseConcat();
                left = new AwkTypes.BinaryOp("!=", left, right);
            } else if (match("<")) {
                AwkTypes.AwkExpr right = parseConcat();
                left = new AwkTypes.BinaryOp("<", left, right);
            } else if (match(">")) {
                AwkTypes.AwkExpr right = parseConcat();
                left = new AwkTypes.BinaryOp(">", left, right);
            } else {
                break;
            }
        }
        return left;
    }

    private AwkTypes.AwkExpr parseConcat() {
        AwkTypes.AwkExpr left = parseAdditive();
        while (true) {
            skipWhitespace();
            int savePos = pos;
            // Concatenation: two expressions next to each other
            if (pos < length && isConcatStart(peek())) {
                AwkTypes.AwkExpr right = parseAdditive();
                if (right != null) {
                    left = new AwkTypes.BinaryOp(" ", left, right);
                    continue;
                }
            }
            pos = savePos;
            break;
        }
        return left;
    }

    private boolean isConcatStart(char c) {
        return c == '$' || c == '(' || c == '"' || c == '/' ||
               Character.isLetter(c) || Character.isDigit(c) || c == '+' || c == '-' || c == '!';
    }

    private AwkTypes.AwkExpr parseAdditive() {
        AwkTypes.AwkExpr left = parseMultiplicative();
        while (true) {
            skipWhitespace();
            if (match("+")) {
                if (pos < length && peek() == '=') {
                    pos--; // backtrack: this + is part of +=
                    break;
                }
                AwkTypes.AwkExpr right = parseMultiplicative();
                left = new AwkTypes.BinaryOp("+", left, right);
            } else if (match("-")) {
                if (pos < length && peek() == '=') {
                    pos--; // backtrack: this - is part of -=
                    break;
                }
                AwkTypes.AwkExpr right = parseMultiplicative();
                left = new AwkTypes.BinaryOp("-", left, right);
            } else {
                break;
            }
        }
        return left;
    }

    private AwkTypes.AwkExpr parseMultiplicative() {
        AwkTypes.AwkExpr left = parsePower();
        while (true) {
            skipWhitespace();
            if (match("*")) {
                if (pos < length && peek() == '=') {
                    pos--; // backtrack: this * is part of *=
                    break;
                }
                AwkTypes.AwkExpr right = parsePower();
                left = new AwkTypes.BinaryOp("*", left, right);
            } else if (match("/")) {
                if (pos < length && peek() == '=') {
                    pos--; // backtrack: this / is part of /=
                    break;
                }
                AwkTypes.AwkExpr right = parsePower();
                left = new AwkTypes.BinaryOp("/", left, right);
            } else if (match("%")) {
                if (pos < length && peek() == '=') {
                    pos--; // backtrack: this % is part of %=
                    break;
                }
                AwkTypes.AwkExpr right = parsePower();
                left = new AwkTypes.BinaryOp("%", left, right);
            } else {
                break;
            }
        }
        return left;
    }

    private AwkTypes.AwkExpr parsePower() {
        AwkTypes.AwkExpr left = parseUnary();
        skipWhitespace();
        if (match("^")) {
            if (pos < length && peek() == '=') {
                pos--; // backtrack: this ^ is part of ^=
                return left;
            }
            AwkTypes.AwkExpr right = parsePower();
            left = new AwkTypes.BinaryOp("^", left, right);
        }
        return left;
    }

    private AwkTypes.AwkExpr parseUnary() {
        skipWhitespace();
        if (match("++")) {
            return new AwkTypes.PreIncrement(parsePostfix());
        }
        if (match("--")) {
            return new AwkTypes.PreDecrement(parsePostfix());
        }
        if (match("!")) {
            return new AwkTypes.UnaryOp("!", parseUnary());
        }
        if (match("+")) {
            if (pos < length && peek() == '=') {
                pos--; // backtrack: this + is part of +=
                return parsePostfix();
            }
            return new AwkTypes.UnaryOp("+", parseUnary());
        }
        if (match("-")) {
            if (pos < length && peek() == '=') {
                pos--; // backtrack: this - is part of -=
                return parsePostfix();
            }
            return new AwkTypes.UnaryOp("-", parseUnary());
        }
        return parsePostfix();
    }

    private AwkTypes.AwkExpr parsePostfix() {
        AwkTypes.AwkExpr expr = parsePrimary();
        skipWhitespace();
        if (match("++")) {
            return new AwkTypes.PostIncrement(expr);
        }
        if (match("--")) {
            return new AwkTypes.PostDecrement(expr);
        }
        return expr;
    }

    private AwkTypes.AwkExpr parsePrimary() {
        skipWhitespace();
        if (pos >= length) return null;

        char c = peek();

        // Number literal
        if (Character.isDigit(c) || (c == '.' && pos + 1 < length && Character.isDigit(input.charAt(pos + 1)))) {
            return parseNumber();
        }

        // String literal
        if (c == '"') {
            return parseString();
        }

        // Regex literal (when used as primary)
        if (c == '/') {
            return parseRegexLiteral();
        }

        // Parenthesized expression
        if (c == '(') {
            consume();
            AwkTypes.AwkExpr expr = parseExpression();
            expect(')');
            return expr;
        }

        // Field reference
        if (c == '$') {
            consume();
            AwkTypes.AwkExpr index = parsePrimary();
            return new AwkTypes.FieldRef(index);
        }

        // getline
        if (matchKeyword("getline")) {
            String var = null;
            AwkTypes.AwkExpr file = null;
            AwkTypes.AwkExpr command = null;
            skipWhitespace();
            if (pos < length && peek() != ';' && peek() != '\n' && peek() != '}' && peek() != ')') {
                if (Character.isLetter(peek())) {
                    int save = pos;
                    String name = parseIdentifier();
                    skipWhitespace();
                    if (match("<")) {
                        // getline var < file
                        file = parseExpression();
                        var = name;
                    } else {
                        pos = save;
                        AwkTypes.AwkExpr maybeVar = parsePrimary();
                        if (maybeVar instanceof AwkTypes.Variable) {
                            var = ((AwkTypes.Variable) maybeVar).name;
                        }
                    }
                } else if (match("<")) {
                    file = parseExpression();
                }
            }
            return new AwkTypes.GetlineExpr(var, file, command);
        }

        // Identifier / function call / variable / array access
        if (Character.isLetter(c) || c == '_') {
            int savePos = pos;
            String name = parseIdentifier();
            if (isKeyword(name)) {
                pos = savePos;
                return null;
            }
            skipWhitespace();
            if (peek() == '(') {
                // Function call
                consume();
                List<AwkTypes.AwkExpr> args = new ArrayList<>();
                skipWhitespace();
                if (peek() != ')') {
                    args.add(parseExpression());
                    while (match(",")) {
                        args.add(parseExpression());
                    }
                }
                expect(')');
                return new AwkTypes.FunctionCall(name, args);
            }
            // Array access
            if (peek() == '[') {
                consume();
                AwkTypes.AwkExpr key = parseExpression();
                expect(']');
                return new AwkTypes.ArrayAccess(name, key);
            }
            return new AwkTypes.Variable(name);
        }

        return null;
    }

    private AwkTypes.AwkExpr parseNumber() {
        int start = pos;
        boolean hasDot = false;
        while (pos < length && (Character.isDigit(peek()) || peek() == '.')) {
            if (peek() == '.') {
                if (hasDot) break;
                hasDot = true;
            }
            consume();
        }
        String numStr = input.substring(start, pos);
        try {
            return new AwkTypes.NumberLiteral(Double.parseDouble(numStr));
        } catch (NumberFormatException e) {
            return new AwkTypes.NumberLiteral(0);
        }
    }

    private AwkTypes.AwkExpr parseString() {
        consume(); // "
        StringBuilder sb = new StringBuilder();
        while (pos < length && peek() != '"') {
            if (peek() == '\\' && pos + 1 < length) {
                consume();
                char escaped = consume();
                switch (escaped) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'a': sb.append('\007'); break;
                    case 'v': sb.append('\013'); break;
                    case '\\': sb.append('\\'); break;
                    case '"': sb.append('"'); break;
                    case '/': sb.append('/'); break;
                    default: sb.append(escaped); break;
                }
            } else {
                sb.append(consume());
            }
        }
        if (peek() == '"') consume();
        return new AwkTypes.StringLiteral(sb.toString());
    }

    private AwkTypes.AwkExpr parseRegexLiteral() {
        // Only parse as regex if it doesn't look like division
        int savePos = pos;
        consume(); // /
        StringBuilder pattern = new StringBuilder();
        while (pos < length && peek() != '/') {
            if (peek() == '\\' && pos + 1 < length) {
                pattern.append(consume());
                pattern.append(consume());
            } else {
                pattern.append(consume());
            }
        }
        if (peek() == '/') {
            consume();
            return new AwkTypes.RegexLiteral(pattern.toString());
        }
        pos = savePos;
        return null;
    }

    // ─── Helpers ───────────────────────────────────────────────

    private char peek() {
        return pos < length ? input.charAt(pos) : '\0';
    }

    private char consume() {
        return pos < length ? input.charAt(pos++) : '\0';
    }

    private void expect(char expected) {
        if (peek() == expected) {
            consume();
        }
    }

    private void expectKeyword(String keyword) {
        matchKeyword(keyword);
    }

    private boolean match(String s) {
        if (input.startsWith(s, pos)) {
            pos += s.length();
            return true;
        }
        return false;
    }

    private boolean matchKeyword(String keyword) {
        int savePos = pos;
        skipWhitespace();
        if (input.startsWith(keyword, pos)) {
            int after = pos + keyword.length();
            if (after >= length || !Character.isLetterOrDigit(input.charAt(after))) {
                pos = after;
                return true;
            }
        }
        pos = savePos;
        return false;
    }

    private String parseIdentifier() {
        StringBuilder sb = new StringBuilder();
        while (pos < length && (Character.isLetterOrDigit(peek()) || peek() == '_')) {
            sb.append(consume());
        }
        return sb.toString();
    }

    private void skipWhitespace() {
        while (pos < length && (peek() == ' ' || peek() == '\t' || peek() == '\r')) {
            pos++;
        }
    }

    private void skipWhitespaceAndNewlines() {
        while (pos < length) {
            char c = peek();
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                pos++;
            } else if (c == ';') {
                pos++;
            } else {
                break;
            }
        }
    }

    private void skipComment() {
        while (pos < length && peek() != '\n') {
            pos++;
        }
    }

    private boolean isKeyword(String name) {
        return name.equals("if") || name.equals("while") || name.equals("do") ||
               name.equals("for") || name.equals("break") || name.equals("continue") ||
               name.equals("next") || name.equals("nextfile") || name.equals("exit") ||
               name.equals("return") || name.equals("delete") || name.equals("print") ||
               name.equals("printf") || name.equals("function") || name.equals("func") ||
               name.equals("BEGIN") || name.equals("END");
    }
}
