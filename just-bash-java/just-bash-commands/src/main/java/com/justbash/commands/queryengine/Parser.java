package com.justbash.commands.queryengine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Parser {

    private final List<Token> tokens;
    private int pos = 0;

    private static final Map<TokenType, String> UPDATE_OP_MAP = Map.of(
        TokenType.ASSIGN, "=",
        TokenType.UPDATE_ADD, "+=",
        TokenType.UPDATE_SUB, "-=",
        TokenType.UPDATE_MUL, "*=",
        TokenType.UPDATE_DIV, "/=",
        TokenType.UPDATE_MOD, "%=",
        TokenType.UPDATE_ALT, "//=",
        TokenType.UPDATE_PIPE, "|="
    );

    private static final Map<TokenType, String> COMPARISON_OP_MAP = Map.of(
        TokenType.EQ, "==",
        TokenType.NE, "!=",
        TokenType.LT, "<",
        TokenType.LE, "<=",
        TokenType.GT, ">",
        TokenType.GE, ">="
    );

    private static final Map<TokenType, String> ADD_SUB_OP_MAP = Map.of(
        TokenType.PLUS, "+",
        TokenType.MINUS, "-"
    );

    private static final Map<TokenType, String> MUL_DIV_OP_MAP = Map.of(
        TokenType.STAR, "*",
        TokenType.SLASH, "/",
        TokenType.PERCENT, "%"
    );

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public static AstNode parse(String input) {
        List<Token> tokens = new Tokenizer().tokenize(input);
        Parser parser = new Parser(tokens);
        AstNode result = parser.parseExpression();
        if (!parser.isAtEnd()) {
            throw new ParseException("Unexpected token at position " + parser.current().pos());
        }
        return result;
    }

    private Token peek(int offset) {
        int idx = pos + offset;
        if (idx < tokens.size()) {
            return tokens.get(idx);
        }
        return new Token(TokenType.EOF, -1);
    }

    private Token peek() {
        return peek(0);
    }

    private Token advance() {
        return tokens.get(pos++);
    }

    private boolean check(TokenType type) {
        return peek().type() == type;
    }

    private Token match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                return advance();
            }
        }
        return null;
    }

    private Token expect(TokenType type, String msg) {
        if (!check(type)) {
            throw new ParseException(msg + " at position " + peek().pos() + ", got " + peek().type());
        }
        return advance();
    }

    private boolean isFieldNameAfterDot(int dotOffset) {
        Token dot = peek(dotOffset);
        Token next = peek(dotOffset + 1);
        if (next.type() == TokenType.STRING) return true;
        if (next.type() == TokenType.IDENT || Tokenizer.getKeywordTokenTypes().contains(next.type())) {
            return next.pos() == dot.pos() + 1;
        }
        return false;
    }

    private boolean isFieldNameAfterDot() {
        return isFieldNameAfterDot(0);
    }

    private boolean isIdentLike() {
        TokenType t = peek().type();
        return t == TokenType.IDENT || Tokenizer.getKeywordTokenTypes().contains(t);
    }

    private String consumeFieldNameAfterDot(Token dotToken) {
        Token next = peek();
        if (next.type() == TokenType.STRING) {
            return (String) advance().value();
        }
        if ((next.type() == TokenType.IDENT || Tokenizer.getKeywordTokenTypes().contains(next.type()))
            && next.pos() == dotToken.pos() + 1) {
            return (String) advance().value();
        }
        return null;
    }

    public AstNode parseExpression() {
        return parseExpr();
    }

    public boolean isAtEnd() {
        return check(TokenType.EOF);
    }

    public Token current() {
        return peek();
    }

    private AstNode parseExpr() {
        return parsePipe();
    }

    private DestructurePattern parsePattern() {
        if (match(TokenType.LBRACKET) != null) {
            List<DestructurePattern> elements = new ArrayList<>();
            if (!check(TokenType.RBRACKET)) {
                elements.add(parsePattern());
                while (match(TokenType.COMMA) != null) {
                    if (check(TokenType.RBRACKET)) break;
                    elements.add(parsePattern());
                }
            }
            expect(TokenType.RBRACKET, "Expected ']' after array pattern");
            return new DestructurePattern.ArrayPattern(elements);
        }

        if (match(TokenType.LBRACE) != null) {
            List<DestructurePattern.ObjectPattern.ObjectField> fields = new ArrayList<>();
            if (!check(TokenType.RBRACE)) {
                fields.add(parsePatternField());
                while (match(TokenType.COMMA) != null) {
                    if (check(TokenType.RBRACE)) break;
                    fields.add(parsePatternField());
                }
            }
            expect(TokenType.RBRACE, "Expected '}' after object pattern");
            return new DestructurePattern.ObjectPattern(fields);
        }

        Token tok = expect(TokenType.IDENT, "Expected variable name in pattern");
        String name = (String) tok.value();
        if (!name.startsWith("$")) {
            throw new ParseException("Variable name must start with $ at position " + tok.pos());
        }
        return new DestructurePattern.VarPattern(name);
    }

    private DestructurePattern.ObjectPattern.ObjectField parsePatternField() {
        if (match(TokenType.LPAREN) != null) {
            AstNode keyExpr = parseExpr();
            expect(TokenType.RPAREN, "Expected ')' after computed key");
            expect(TokenType.COLON, "Expected ':' after computed key");
            DestructurePattern pattern = parsePattern();
            return new DestructurePattern.ObjectPattern.ObjectField(keyExpr, pattern, null);
        }

        Token tok = peek();
        if (tok.type() == TokenType.IDENT || Tokenizer.getKeywordTokenTypes().contains(tok.type())) {
            String name = (String) tok.value();
            if (name.startsWith("$")) {
                advance();
                if (match(TokenType.COLON) != null) {
                    DestructurePattern pattern = parsePattern();
                    return new DestructurePattern.ObjectPattern.ObjectField(name.substring(1), pattern, name);
                }
                return new DestructurePattern.ObjectPattern.ObjectField(name.substring(1), new DestructurePattern.VarPattern(name), null);
            }
            advance();
            if (match(TokenType.COLON) != null) {
                DestructurePattern pattern = parsePattern();
                return new DestructurePattern.ObjectPattern.ObjectField(name, pattern, null);
            }
            return new DestructurePattern.ObjectPattern.ObjectField(name, new DestructurePattern.VarPattern("$" + name), null);
        }

        throw new ParseException("Expected field name in object pattern at position " + tok.pos());
    }

    private AstNode parsePipe() {
        AstNode left = parseComma();
        while (match(TokenType.PIPE) != null) {
            AstNode right = parseComma();
            left = new AstNode.PipeNode(left, right);
        }
        return left;
    }

    private AstNode parseComma() {
        AstNode left = parseVarBind();
        while (match(TokenType.COMMA) != null) {
            AstNode right = parseVarBind();
            left = new AstNode.CommaNode(left, right);
        }
        return left;
    }

    private AstNode parseVarBind() {
        AstNode expr = parseUpdate();
        if (match(TokenType.AS) != null) {
            DestructurePattern pattern = parsePattern();

            List<DestructurePattern> alternatives = new ArrayList<>();
            while (check(TokenType.QUESTION) && peekAhead(1) != null && peekAhead(1).type() == TokenType.ALT) {
                advance(); // consume QUESTION
                advance(); // consume ALT
                alternatives.add(parsePattern());
            }

            expect(TokenType.PIPE, "Expected '|' after variable binding");
            AstNode body = parseExpr();

            if (pattern instanceof DestructurePattern.VarPattern && alternatives.isEmpty()) {
                return new AstNode.VarBindNode(((DestructurePattern.VarPattern) pattern).name(), expr, body, null, null);
            }

            String name = pattern instanceof DestructurePattern.VarPattern ? ((DestructurePattern.VarPattern) pattern).name() : "";
            return new AstNode.VarBindNode(name, expr, body,
                pattern instanceof DestructurePattern.VarPattern ? null : pattern,
                alternatives.isEmpty() ? null : alternatives);
        }
        return expr;
    }

    private Token peekAhead(int n) {
        int idx = pos + n;
        if (idx < tokens.size()) {
            return tokens.get(idx);
        }
        return null;
    }

    private AstNode parseUpdate() {
        AstNode left = parseAlt();
        Token tok = match(TokenType.ASSIGN, TokenType.UPDATE_ADD, TokenType.UPDATE_SUB,
            TokenType.UPDATE_MUL, TokenType.UPDATE_DIV, TokenType.UPDATE_MOD,
            TokenType.UPDATE_ALT, TokenType.UPDATE_PIPE);
        if (tok != null) {
            AstNode value = parseVarBind();
            String op = UPDATE_OP_MAP.get(tok.type());
            if (op != null) {
                return new AstNode.UpdateOpNode(op, left, value);
            }
        }
        return left;
    }

    private AstNode parseAlt() {
        AstNode left = parseOr();
        while (match(TokenType.ALT) != null) {
            AstNode right = parseOr();
            left = new AstNode.BinaryOpNode("//", left, right);
        }
        return left;
    }

    private AstNode parseOr() {
        AstNode left = parseAnd();
        while (match(TokenType.OR) != null) {
            AstNode right = parseAnd();
            left = new AstNode.BinaryOpNode("or", left, right);
        }
        return left;
    }

    private AstNode parseAnd() {
        AstNode left = parseNot();
        while (match(TokenType.AND) != null) {
            AstNode right = parseNot();
            left = new AstNode.BinaryOpNode("and", left, right);
        }
        return left;
    }

    private AstNode parseNot() {
        return parseComparison();
    }

    private AstNode parseComparison() {
        AstNode left = parseAddSub();
        Token tok = match(TokenType.EQ, TokenType.NE, TokenType.LT, TokenType.LE, TokenType.GT, TokenType.GE);
        if (tok != null) {
            String op = COMPARISON_OP_MAP.get(tok.type());
            if (op != null) {
                AstNode right = parseAddSub();
                left = new AstNode.BinaryOpNode(op, left, right);
            }
        }
        return left;
    }

    private AstNode parseAddSub() {
        AstNode left = parseMulDiv();
        while (true) {
            if (match(TokenType.PLUS) != null) {
                AstNode right = parseMulDiv();
                left = new AstNode.BinaryOpNode("+", left, right);
            } else if (match(TokenType.MINUS) != null) {
                AstNode right = parseMulDiv();
                left = new AstNode.BinaryOpNode("-", left, right);
            } else {
                break;
            }
        }
        return left;
    }

    private AstNode parseMulDiv() {
        AstNode left = parseUnary();
        while (true) {
            if (match(TokenType.STAR) != null) {
                AstNode right = parseUnary();
                left = new AstNode.BinaryOpNode("*", left, right);
            } else if (match(TokenType.SLASH) != null) {
                AstNode right = parseUnary();
                left = new AstNode.BinaryOpNode("/", left, right);
            } else if (match(TokenType.PERCENT) != null) {
                AstNode right = parseUnary();
                left = new AstNode.BinaryOpNode("%", left, right);
            } else {
                break;
            }
        }
        return left;
    }

    private AstNode parseUnary() {
        if (match(TokenType.MINUS) != null) {
            AstNode operand = parseUnary();
            return new AstNode.UnaryOpNode("-", operand);
        }
        return parsePostfix();
    }

    private AstNode parsePostfix() {
        AstNode expr = parsePrimary();

        while (true) {
            if (match(TokenType.QUESTION) != null) {
                expr = new AstNode.OptionalNode(expr);
            } else if (check(TokenType.DOT) && isFieldNameAfterDot()) {
                advance(); // consume DOT
                Token token = advance();
                String name = (String) token.value();
                expr = new AstNode.FieldNode(name, expr);
            } else if (check(TokenType.LBRACKET)) {
                advance();
                if (match(TokenType.RBRACKET) != null) {
                    expr = new AstNode.IterateNode(expr);
                } else if (check(TokenType.COLON)) {
                    advance();
                    AstNode end = check(TokenType.RBRACKET) ? null : parseExpr();
                    expect(TokenType.RBRACKET, "Expected ']'");
                    expr = new AstNode.SliceNode(null, end, expr);
                } else {
                    AstNode indexExpr = parseExpr();
                    if (match(TokenType.COLON) != null) {
                        AstNode end = check(TokenType.RBRACKET) ? null : parseExpr();
                        expect(TokenType.RBRACKET, "Expected ']'");
                        expr = new AstNode.SliceNode(indexExpr, end, expr);
                    } else {
                        expect(TokenType.RBRACKET, "Expected ']'");
                        expr = new AstNode.IndexNode(indexExpr, expr);
                    }
                }
            } else {
                break;
            }
        }

        return expr;
    }

    private AstNode parsePrimary() {
        if (match(TokenType.DOTDOT) != null) {
            return new AstNode.RecurseNode();
        }

        if (check(TokenType.DOT)) {
            Token dotToken = advance();
            if (check(TokenType.LBRACKET)) {
                advance();
                if (match(TokenType.RBRACKET) != null) {
                    return new AstNode.IterateNode(null);
                }
                if (check(TokenType.COLON)) {
                    advance();
                    AstNode end = check(TokenType.RBRACKET) ? null : parseExpr();
                    expect(TokenType.RBRACKET, "Expected ']'");
                    return new AstNode.SliceNode(null, end, null);
                }
                AstNode indexExpr = parseExpr();
                if (match(TokenType.COLON) != null) {
                    AstNode end = check(TokenType.RBRACKET) ? null : parseExpr();
                    expect(TokenType.RBRACKET, "Expected ']'");
                    return new AstNode.SliceNode(indexExpr, end, null);
                }
                expect(TokenType.RBRACKET, "Expected ']'");
                return new AstNode.IndexNode(indexExpr, null);
            }
            String fieldName = consumeFieldNameAfterDot(dotToken);
            if (fieldName != null) {
                return new AstNode.FieldNode(fieldName, null);
            }
            return new AstNode.IdentityNode();
        }

        if (match(TokenType.TRUE) != null) {
            return new AstNode.LiteralNode(true);
        }
        if (match(TokenType.FALSE) != null) {
            return new AstNode.LiteralNode(false);
        }
        if (match(TokenType.NULL) != null) {
            return new AstNode.LiteralNode(null);
        }
        if (check(TokenType.NUMBER)) {
            Token tok = advance();
            return new AstNode.LiteralNode(tok.value());
        }
        if (check(TokenType.STRING)) {
            Token tok = advance();
            String str = (String) tok.value();
            if (str.contains("\\(")) {
                return parseStringInterpolation(str);
            }
            return new AstNode.LiteralNode(str);
        }

        if (match(TokenType.LBRACKET) != null) {
            if (match(TokenType.RBRACKET) != null) {
                return new AstNode.ArrayNode(null);
            }
            AstNode elements = parseExpr();
            expect(TokenType.RBRACKET, "Expected ']'");
            return new AstNode.ArrayNode(elements);
        }

        if (match(TokenType.LBRACE) != null) {
            return parseObjectConstruction();
        }

        if (match(TokenType.LPAREN) != null) {
            AstNode expr = parseExpr();
            expect(TokenType.RPAREN, "Expected ')'");
            return new AstNode.ParenNode(expr);
        }

        if (match(TokenType.IF) != null) {
            return parseIf();
        }

        if (match(TokenType.TRY) != null) {
            AstNode body = parsePostfix();
            AstNode catchExpr = null;
            if (match(TokenType.CATCH) != null) {
                catchExpr = parsePostfix();
            }
            return new AstNode.TryNode(body, catchExpr);
        }

        if (match(TokenType.REDUCE) != null) {
            AstNode expr = parseAddSub();
            expect(TokenType.AS, "Expected 'as' after reduce expression");
            DestructurePattern pattern = parsePattern();
            expect(TokenType.LPAREN, "Expected '(' after variable");
            AstNode init = parseExpr();
            expect(TokenType.SEMICOLON, "Expected ';' after init expression");
            AstNode update = parseExpr();
            expect(TokenType.RPAREN, "Expected ')' after update expression");
            String varName = pattern instanceof DestructurePattern.VarPattern ? ((DestructurePattern.VarPattern) pattern).name() : "";
            return new AstNode.ReduceNode(expr, varName,
                pattern instanceof DestructurePattern.VarPattern ? null : pattern, init, update);
        }

        if (match(TokenType.FOREACH) != null) {
            AstNode expr = parseAddSub();
            expect(TokenType.AS, "Expected 'as' after foreach expression");
            DestructurePattern pattern = parsePattern();
            expect(TokenType.LPAREN, "Expected '(' after variable");
            AstNode init = parseExpr();
            expect(TokenType.SEMICOLON, "Expected ';' after init expression");
            AstNode update = parseExpr();
            AstNode extract = null;
            if (match(TokenType.SEMICOLON) != null) {
                extract = parseExpr();
            }
            expect(TokenType.RPAREN, "Expected ')' after expressions");
            String varName = pattern instanceof DestructurePattern.VarPattern ? ((DestructurePattern.VarPattern) pattern).name() : "";
            return new AstNode.ForeachNode(expr, varName,
                pattern instanceof DestructurePattern.VarPattern ? null : pattern, init, update, extract);
        }

        if (match(TokenType.LABEL) != null) {
            Token labelToken = expect(TokenType.IDENT, "Expected label name (e.g., $out)");
            String labelName = (String) labelToken.value();
            if (!labelName.startsWith("$")) {
                throw new ParseException("Label name must start with $ at position " + labelToken.pos());
            }
            expect(TokenType.PIPE, "Expected '|' after label name");
            AstNode labelBody = parseExpr();
            return new AstNode.LabelNode(labelName, labelBody);
        }

        if (match(TokenType.BREAK) != null) {
            Token breakToken = expect(TokenType.IDENT, "Expected label name to break to");
            String breakLabel = (String) breakToken.value();
            if (!breakLabel.startsWith("$")) {
                throw new ParseException("Break label must start with $ at position " + breakToken.pos());
            }
            return new AstNode.BreakNode(breakLabel);
        }

        if (match(TokenType.DEF) != null) {
            Token nameToken = expect(TokenType.IDENT, "Expected function name after def");
            String funcName = (String) nameToken.value();
            List<String> params = new ArrayList<>();

            if (match(TokenType.LPAREN) != null) {
                if (!check(TokenType.RPAREN)) {
                    Token firstParam = expect(TokenType.IDENT, "Expected parameter name");
                    params.add((String) firstParam.value());
                    while (match(TokenType.SEMICOLON) != null) {
                        Token param = expect(TokenType.IDENT, "Expected parameter name");
                        params.add((String) param.value());
                    }
                }
                expect(TokenType.RPAREN, "Expected ')' after parameters");
            }

            expect(TokenType.COLON, "Expected ':' after function name");
            AstNode funcBody = parseExpr();
            expect(TokenType.SEMICOLON, "Expected ';' after function body");
            AstNode body = parseExpr();

            return new AstNode.DefNode(funcName, params, funcBody, body);
        }

        if (match(TokenType.NOT) != null) {
            return new AstNode.CallNode("not", List.of());
        }

        if (check(TokenType.IDENT)) {
            Token tok = advance();
            String name = (String) tok.value();

            if (name.startsWith("$")) {
                return new AstNode.VarRefNode(name);
            }

            if (match(TokenType.LPAREN) != null) {
                List<AstNode> args = new ArrayList<>();
                if (!check(TokenType.RPAREN)) {
                    args.add(parseExpr());
                    while (match(TokenType.SEMICOLON) != null) {
                        args.add(parseExpr());
                    }
                }
                expect(TokenType.RPAREN, "Expected ')'");
                return new AstNode.CallNode(name, args);
            }

            return new AstNode.CallNode(name, List.of());
        }

        throw new ParseException("Unexpected token " + peek().type() + " at position " + peek().pos());
    }

    private AstNode.ObjectNode parseObjectConstruction() {
        List<AstNode.ObjectNode.ObjectEntry> entries = new ArrayList<>();

        if (!check(TokenType.RBRACE)) {
            do {
                AstNode key;
                AstNode value;

                if (match(TokenType.LPAREN) != null) {
                    key = parseExpr();
                    expect(TokenType.RPAREN, "Expected ')'");
                    expect(TokenType.COLON, "Expected ':'");
                    value = parseObjectValue();
                } else if (isIdentLike()) {
                    String ident = (String) advance().value();
                    if (match(TokenType.COLON) != null) {
                        key = new AstNode.LiteralNode(ident);
                        value = parseObjectValue();
                    } else {
                        key = new AstNode.LiteralNode(ident);
                        value = new AstNode.FieldNode(ident, null);
                    }
                } else if (check(TokenType.STRING)) {
                    key = new AstNode.LiteralNode(advance().value());
                    expect(TokenType.COLON, "Expected ':'");
                    value = parseObjectValue();
                } else {
                    throw new ParseException("Expected object key at position " + peek().pos());
                }

                entries.add(new AstNode.ObjectNode.ObjectEntry(key, value));
            } while (match(TokenType.COMMA) != null);
        }

        expect(TokenType.RBRACE, "Expected '}'");
        return new AstNode.ObjectNode(entries);
    }

    private AstNode parseObjectValue() {
        AstNode left = parseVarBind();
        while (match(TokenType.PIPE) != null) {
            AstNode right = parseVarBind();
            left = new AstNode.PipeNode(left, right);
        }
        return left;
    }

    private AstNode.CondNode parseIf() {
        AstNode cond = parseExpr();
        expect(TokenType.THEN, "Expected 'then'");
        AstNode thenBranch = parseExpr();

        List<AstNode.CondNode.ElifBranch> elifs = new ArrayList<>();
        while (match(TokenType.ELIF) != null) {
            AstNode elifCond = parseExpr();
            expect(TokenType.THEN, "Expected 'then' after elif");
            AstNode elifThen = parseExpr();
            elifs.add(new AstNode.CondNode.ElifBranch(elifCond, elifThen));
        }

        AstNode elseBranch = null;
        if (match(TokenType.ELSE) != null) {
            elseBranch = parseExpr();
        }

        expect(TokenType.END, "Expected 'end'");
        return new AstNode.CondNode(cond, thenBranch, elifs, elseBranch);
    }

    private AstNode.StringInterpNode parseStringInterpolation(String str) {
        List<StringInterpPart> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int i = 0;

        while (i < str.length()) {
            if (str.charAt(i) == '\\' && i + 1 < str.length() && str.charAt(i + 1) == '(') {
                if (current.length() > 0) {
                    parts.add(new StringPart(current.toString()));
                    current = new StringBuilder();
                }
                i += 2;
                int depth = 1;
                StringBuilder exprStr = new StringBuilder();
                while (i < str.length() && depth > 0) {
                    if (str.charAt(i) == '(') depth++;
                    else if (str.charAt(i) == ')') depth--;
                    if (depth > 0) exprStr.append(str.charAt(i));
                    i++;
                }
                AstNode parsed = Parser.parse(exprStr.toString());
                parts.add(new ExprPart(parsed));
            } else {
                current.append(str.charAt(i));
                i++;
            }
        }

        if (current.length() > 0) {
            parts.add(new StringPart(current.toString()));
        }

        return new AstNode.StringInterpNode(parts);
    }
}
