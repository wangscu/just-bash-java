package com.justbash.parser;

import com.justbash.ast.*;
import com.justbash.ast.command.*;
import com.justbash.ast.word.*;
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
            statements.add(parseStatement());
            if (match(TokenType.NEWLINE)) {
                // skip extra newlines
            }
        }
        return new ScriptNode(1, statements);
    }

    private StatementNode parseStatement() {
        List<PipelineNode> pipelines = new ArrayList<>();
        List<StatementNode.StatementOperator> operators = new ArrayList<>();
        pipelines.add(parsePipeline());
        while (match(TokenType.AND_IF, TokenType.OR_IF, TokenType.SEMI)) {
            operators.add(switch (previous().type()) {
                case AND_IF -> StatementNode.StatementOperator.AND;
                case OR_IF -> StatementNode.StatementOperator.OR;
                default -> StatementNode.StatementOperator.SEMICOLON;
            });
            if (!check(TokenType.EOF) && !check(TokenType.NEWLINE)) {
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
        return parseSimpleCommand();
    }

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
                // For now, double-quoted content is treated as a single literal part.
                // TODO: handle $var, `cmd`, and escape sequences inside double quotes.
                List<WordPart> innerParts = new ArrayList<>();
                innerParts.add(new LiteralPart(line, inner));
                parts.add(new DoubleQuotedPart(line, innerParts));
                i = end + 1;
            } else {
                int end = i;
                while (end < value.length()
                       && value.charAt(end) != '\''
                       && value.charAt(end) != '"') {
                    end++;
                }
                parts.add(new LiteralPart(line, value.substring(i, end)));
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

    private Token current() { return tokens.get(pos); }
    private Token previous() { return tokens.get(pos - 1); }
    private Token advance() {
        if (!check(TokenType.EOF)) pos++;
        return previous();
    }
    private boolean check(TokenType type) { return current().type() == type; }

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
