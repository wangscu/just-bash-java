package com.justbash.parser;

import com.justbash.ast.*;
import com.justbash.ast.command.*;
import com.justbash.ast.operations.ParameterOperation;
import com.justbash.ast.word.*;
import com.justbash.interpreter.errors.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Parser {
    private final List<Token> tokens;
    private int pos = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public static ScriptNode parse(String input) {
        Lexer lexer = new Lexer(input);
        Parser parser = new Parser(lexer.tokenize());
        return parser.parseScript();
    }

    public ScriptNode parseScript() {
        List<StatementNode> statements = new ArrayList<>();
        while (!check(TokenType.EOF)) {
            StatementNode stmt = parseStatement();
            if (stmt != null) statements.add(stmt);
            if (match(TokenType.NEWLINE)) {
                // skip extra newlines
            }
        }
        return new ScriptNode(1, statements);
    }

    private StatementNode parseStatement() {
        if (!canStartStatement()) return null;
        List<PipelineNode> pipelines = new ArrayList<>();
        List<StatementNode.StatementOperator> operators = new ArrayList<>();
        pipelines.add(parsePipeline());
        while (match(TokenType.AND_IF, TokenType.OR_IF, TokenType.SEMI)) {
            operators.add(switch (previous().type()) {
                case AND_IF -> StatementNode.StatementOperator.AND;
                case OR_IF -> StatementNode.StatementOperator.OR;
                default -> StatementNode.StatementOperator.SEMICOLON;
            });
            if (!check(TokenType.EOF) && !check(TokenType.NEWLINE) && canStartCommand()) {
                pipelines.add(parsePipeline());
            }
        }
        match(TokenType.NEWLINE);
        return new StatementNode(current().line(), pipelines, operators, false);
    }

    private PipelineNode parsePipeline() {
        boolean negated = match(TokenType.BANG);
        List<CommandNode> commands = new ArrayList<>();
        commands.add(parseCommand());
        while (match(TokenType.PIPE)) {
            commands.add(parseCommand());
        }
        return new PipelineNode(current().line(), commands, negated);
    }

    private CommandNode parseCommand() {
        if (check(TokenType.IF)) return parseIf();
        if (check(TokenType.FOR)) return parseFor();
        if (check(TokenType.WHILE)) return parseWhile();
        if (check(TokenType.UNTIL)) return parseUntil();
        if (check(TokenType.CASE)) return parseCase();
        if (check(TokenType.LPAREN)) return parseSubshell();
        if (check(TokenType.LBRACE)) return parseGroup();
        return parseSimpleCommand();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Compound command parsers
    // ─────────────────────────────────────────────────────────────────────────

    private IfNode parseIf() {
        int line = current().line();
        expect(TokenType.IF);
        List<StatementNode> condition = parseCompoundList();
        skipNewlines();
        expect(TokenType.THEN);
        List<StatementNode> thenBody = parseCompoundList();

        List<IfNode.IfClause> clauses = new ArrayList<>();
        clauses.add(new IfNode.IfClause(condition, thenBody));

        List<StatementNode> elseBody = new ArrayList<>();
        while (match(TokenType.ELIF)) {
            List<StatementNode> elifCondition = parseCompoundList();
            skipNewlines();
            expect(TokenType.THEN);
            List<StatementNode> elifBody = parseCompoundList();
            clauses.add(new IfNode.IfClause(elifCondition, elifBody));
        }

        if (match(TokenType.ELSE)) {
            elseBody = parseCompoundList();
        }

        skipNewlines();
        expect(TokenType.FI);
        return new IfNode(line, clauses, elseBody, List.of());
    }

    private ForNode parseFor() {
        int line = current().line();
        expect(TokenType.FOR);
        String variable = expect(TokenType.WORD).value();

        Optional<List<WordNode>> words = Optional.empty();
        skipNewlines();

        if (match(TokenType.IN)) {
            List<WordNode> wordList = new ArrayList<>();
            while (check(TokenType.WORD)) {
                wordList.add(parseWord());
            }
            words = Optional.of(wordList);
            match(TokenType.SEMI);
        } else {
            match(TokenType.SEMI);
        }

        skipNewlines();
        expect(TokenType.DO);
        List<StatementNode> body = parseCompoundList();
        skipNewlines();
        expect(TokenType.DONE);
        return new ForNode(line, variable, words, body, List.of());
    }

    private WhileNode parseWhile() {
        int line = current().line();
        expect(TokenType.WHILE);
        List<StatementNode> condition = parseCompoundList();
        skipNewlines();
        expect(TokenType.DO);
        List<StatementNode> body = parseCompoundList();
        skipNewlines();
        expect(TokenType.DONE);
        return new WhileNode(line, condition, body, false, List.of());
    }

    private WhileNode parseUntil() {
        int line = current().line();
        expect(TokenType.UNTIL);
        List<StatementNode> condition = parseCompoundList();
        skipNewlines();
        expect(TokenType.DO);
        List<StatementNode> body = parseCompoundList();
        skipNewlines();
        expect(TokenType.DONE);
        return new WhileNode(line, condition, body, true, List.of());
    }

    private GroupNode parseGroup() {
        int line = current().line();
        expect(TokenType.LBRACE);
        List<StatementNode> body = parseCompoundList();
        skipNewlines();
        expect(TokenType.RBRACE);
        return new GroupNode(line, body, List.of());
    }

    private SubshellNode parseSubshell() {
        int line = current().line();
        expect(TokenType.LPAREN);
        List<StatementNode> body = parseCompoundList();
        skipNewlines();
        expect(TokenType.RPAREN);
        return new SubshellNode(line, body, List.of());
    }

    private CaseNode parseCase() {
        int line = current().line();
        expect(TokenType.CASE);
        WordNode word = parseWord();
        skipNewlines();
        expect(TokenType.IN);
        skipNewlines();

        List<CaseNode.CaseItemNode> items = new ArrayList<>();
        while (!check(TokenType.ESAC) && !check(TokenType.EOF)) {
            // Patterns: word | word | word
            List<WordNode> patterns = new ArrayList<>();
            patterns.add(parseWord());
            while (match(TokenType.PIPE)) {
                patterns.add(parseWord());
            }
            expect(TokenType.RPAREN);
            skipNewlines();

            // Body (stops at ;; or esac)
            List<StatementNode> body = parseCaseItemBody();

            // Terminator ;; (DSEMI)
            if (!match(TokenType.DSEMI)) {
                // Tolerate missing terminator before esac
                if (!check(TokenType.ESAC) && !check(TokenType.EOF)) {
                    throw new ParseException("Expected ;;",
                        current().line(), current().column());
                }
            }
            skipNewlines();
            items.add(new CaseNode.CaseItemNode(line, patterns, body,
                CaseNode.CaseItemNode.Terminator.DSEMI));
        }

        skipNewlines();
        expect(TokenType.ESAC);
        return new CaseNode(line, word, items, List.of());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Compound list parser (used for bodies of compound commands)
    // ─────────────────────────────────────────────────────────────────────────

    private List<StatementNode> parseCompoundList() {
        skipNewlines();
        List<StatementNode> statements = new ArrayList<>();
        while (!isCompoundEndToken() && !check(TokenType.EOF)) {
            if (!canStartStatement()) break;
            StatementNode stmt = parseStatement();
            if (stmt != null) statements.add(stmt);
            skipNewlines();
        }
        return statements;
    }

    private List<StatementNode> parseCaseItemBody() {
        List<StatementNode> statements = new ArrayList<>();
        while (!check(TokenType.DSEMI, TokenType.ESAC, TokenType.EOF)) {
            if (!canStartStatement()) break;
            StatementNode stmt = parseStatement();
            if (stmt != null) statements.add(stmt);
            skipNewlines();
        }
        return statements;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Simple command parsing
    // ─────────────────────────────────────────────────────────────────────────

    private SimpleCommandNode parseSimpleCommand() {
        List<AssignmentNode> assignments = new ArrayList<>();
        List<WordNode> args = new ArrayList<>();
        List<RedirectionNode> redirections = new ArrayList<>();
        while (checkAssignment()) {
            assignments.add(parseAssignment());
        }
        WordNode name = null;
        if (check(TokenType.WORD)) {
            name = parseWord();
            while (check(TokenType.WORD)) {
                args.add(parseWord());
            }
        }
        return new SimpleCommandNode(
            current().line(), name, args, assignments, redirections);
    }

    private AssignmentNode parseAssignment() {
        Token token = advance();
        String text = token.value();
        int eqPos = text.indexOf("=");
        String name = text.substring(0, eqPos);
        String value = text.substring(eqPos + 1);
        return new AssignmentNode(
            token.line(), name,
            Optional.of(new WordNode(token.line(),
                List.of(new LiteralPart(token.line(), value)))),
            false, Optional.empty()
        );
    }

    private WordNode parseWord() {
        Token token = advance();
        return parseWordValue(token.value(), token.line());
    }

    private WordNode parseWordValue(String value, int line) {
        List<WordPart> parts = new ArrayList<>();
        int i = 0;
        while (i < value.length()) {
            char c = value.charAt(i);
            if (c == '\'') {
                int end = value.indexOf('\'', i + 1);
                if (end == -1) {
                    end = value.length();
                }
                parts.add(new SingleQuotedPart(line, value.substring(i + 1, end)));
                i = end + 1;
            } else if (c == '"') {
                int end = value.indexOf('"', i + 1);
                if (end == -1) {
                    end = value.length();
                }
                String inner = value.substring(i + 1, end);
                List<WordPart> innerParts = new ArrayList<>();
                innerParts.add(new LiteralPart(line, inner));
                parts.add(new DoubleQuotedPart(line, innerParts));
                i = end + 1;
            } else if (c == '$') {
                int paramStart = i + 1;
                int paramEnd = paramStart;
                if (paramEnd < value.length() && value.charAt(paramEnd) == '{') {
                    int closeBrace = value.indexOf('}', paramEnd);
                    if (closeBrace == -1) {
                        paramEnd = value.length();
                        parts.add(new ParameterExpansionPart(line, value.substring(paramStart + 1, paramEnd), Optional.empty()));
                        i = paramEnd;
                    } else {
                        parts.add(new ParameterExpansionPart(line, value.substring(paramStart + 1, closeBrace), Optional.empty()));
                        i = closeBrace + 1;
                    }
                } else {
                    while (paramEnd < value.length()
                           && (Character.isLetterOrDigit(value.charAt(paramEnd))
                               || value.charAt(paramEnd) == '_')) {
                        paramEnd++;
                    }
                    if (paramEnd > paramStart) {
                        parts.add(new ParameterExpansionPart(line, value.substring(paramStart, paramEnd), Optional.empty()));
                        i = paramEnd;
                    } else {
                        parts.add(new LiteralPart(line, "$"));
                        i = paramStart;
                    }
                }
            } else {
                int end = i;
                while (end < value.length()
                       && value.charAt(end) != '\''
                       && value.charAt(end) != '"'
                       && value.charAt(end) != '$') {
                    end++;
                }
                if (end > i) {
                    parts.add(new LiteralPart(line, value.substring(i, end)));
                }
                i = end;
            }
        }
        return new WordNode(line, parts);
    }

    private boolean checkAssignment() {
        if (!check(TokenType.WORD)) return false;
        String value = current().value();
        int eqPos = value.indexOf("=");
        if (eqPos <= 0) return false;
        String name = value.substring(0, eqPos);
        return name.matches("[a-zA-Z_][a-zA-Z0-9_]*");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper methods
    // ─────────────────────────────────────────────────────────────────────────

    private Token expect(TokenType type) {
        if (check(type)) return advance();
        throw new ParseException(
            "Expected " + type + " but found " + current().type() +
            " at line " + current().line(),
            current().line(), current().column());
    }

    private void skipNewlines() {
        while (match(TokenType.NEWLINE)) {}
    }

    private boolean isCompoundEndToken() {
        return check(TokenType.EOF, TokenType.FI, TokenType.ELSE, TokenType.ELIF,
                     TokenType.THEN, TokenType.DO, TokenType.DONE, TokenType.ESAC,
                     TokenType.RPAREN, TokenType.RBRACE);
    }

    private boolean canStartCommand() {
        return check(TokenType.WORD, TokenType.IF, TokenType.FOR, TokenType.WHILE,
                     TokenType.UNTIL, TokenType.CASE, TokenType.LPAREN, TokenType.LBRACE,
                     TokenType.BANG);
    }

    private boolean canStartStatement() {
        return canStartCommand();
    }

    private Token current() { return tokens.get(pos); }
    private Token previous() { return tokens.get(pos - 1); }
    private Token advance() {
        if (!check(TokenType.EOF)) pos++;
        return previous();
    }

    @SafeVarargs
    private boolean check(TokenType... types) {
        for (TokenType type : types) {
            if (current().type() == type) return true;
        }
        return false;
    }

    @SafeVarargs
    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }
}
