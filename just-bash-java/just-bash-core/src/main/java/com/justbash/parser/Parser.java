package com.justbash.parser;

import com.justbash.ast.*;
import com.justbash.ast.command.*;
import com.justbash.ast.expression.ArithmeticExpressionNode;
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
        if (check(TokenType.FUNCTION) || isFunctionDefinition()) return parseFunction();
        if (check(TokenType.LPAREN) && pos + 1 < tokens.size() && tokens.get(pos + 1).type() == TokenType.LPAREN) {
            return parseArithmeticCommand();
        }
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
            while (check(TokenType.WORD) || check(TokenType.LBRACE)) {
                if (check(TokenType.WORD)) {
                    // Check if this word is followed by LBRACE (prefix + brace)
                    if (pos + 1 < tokens.size() && tokens.get(pos + 1).type() == TokenType.LBRACE) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(advance().value()); // consume WORD prefix
                        int braceDepth = 0;
                        while (!check(TokenType.EOF)) {
                            if (check(TokenType.LBRACE)) {
                                braceDepth++;
                                sb.append(advance().value());
                            } else if (check(TokenType.RBRACE)) {
                                braceDepth--;
                                sb.append(advance().value());
                                if (braceDepth == 0) break;
                            } else if (check(TokenType.WORD)) {
                                sb.append(advance().value());
                            } else {
                                break;
                            }
                        }
                        while (check(TokenType.WORD)) {
                            sb.append(advance().value());
                        }
                        wordList.add(parseWordValue(sb.toString(), previous().line()));
                    } else {
                        wordList.add(parseWord());
                    }
                } else if (check(TokenType.LBRACE)) {
                    StringBuilder sb = new StringBuilder();
                    int braceDepth = 0;
                    while (!check(TokenType.EOF)) {
                        if (check(TokenType.LBRACE)) {
                            braceDepth++;
                            sb.append(advance().value());
                        } else if (check(TokenType.RBRACE)) {
                            braceDepth--;
                            sb.append(advance().value());
                            if (braceDepth == 0) break;
                        } else if (check(TokenType.WORD)) {
                            sb.append(advance().value());
                        } else {
                            break;
                        }
                    }
                    while (check(TokenType.WORD)) {
                        sb.append(advance().value());
                    }
                    wordList.add(parseWordValue(sb.toString(), previous().line()));
                }
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

    private boolean isFunctionDefinition() {
        if (!check(TokenType.WORD)) return false;
        String value = current().value();
        if (value.endsWith("=")) return false; // arr=() is array assignment, not function
        if (pos + 1 >= tokens.size() || tokens.get(pos + 1).type() != TokenType.LPAREN) return false;
        if (pos + 2 >= tokens.size() || tokens.get(pos + 2).type() != TokenType.RPAREN) return false;
        return true;
    }

    private FunctionDefNode parseFunction() {
        int line = current().line();
        String name;
        if (match(TokenType.FUNCTION)) {
            name = expect(TokenType.WORD).value();
        } else {
            name = expect(TokenType.WORD).value();
        }
        if (match(TokenType.LPAREN)) {
            expect(TokenType.RPAREN);
        }
        skipNewlines();
        CommandNode body = parseCommand();
        if (!(body instanceof CompoundCommandNode)) {
            throw new ParseException("Function body must be a compound command",
                current().line(), current().column());
        }
        return new FunctionDefNode(line, name, (CompoundCommandNode) body, List.of(), Optional.empty());
    }

    private ArithmeticCommandNode parseArithmeticCommand() {
        int line = current().line();
        expect(TokenType.LPAREN);
        expect(TokenType.LPAREN);

        // Read tokens until matching )).
        // parenDepth starts at 2 for the ((. Append ) only when depth stays >= 2
        // after decrement — that means it's closing an inner paren, not the outer ((.
        StringBuilder sb = new StringBuilder();
        int parenDepth = 2;
        while (!check(TokenType.EOF)) {
            Token tok = advance();
            if (tok.type() == TokenType.LPAREN) {
                parenDepth++;
                sb.append("(");
            } else if (tok.type() == TokenType.RPAREN) {
                parenDepth--;
                if (parenDepth == 0) break;
                if (parenDepth >= 2) sb.append(")");
            } else if (tok.type() == TokenType.NEWLINE) {
                sb.append(" ");
            } else {
                sb.append(tok.value()).append(" ");
            }
        }

        String exprStr = sb.toString().trim();
        var arithExpr = ArithmeticParser.parse(exprStr, line);
        return new ArithmeticCommandNode(line,
            new ArithmeticExpressionNode(line, arithExpr, Optional.of(exprStr)),
            List.of());
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
        // Array literal assignment: arr=(a b c)
        while (checkArrayAssignment()) {
            assignments.add(parseArrayAssignment());
        }
        while (checkAssignment()) {
            assignments.add(parseAssignment());
        }
        WordNode name = null;
        if (check(TokenType.WORD)) {
            name = parseWord();
            while (true) {
                if (check(TokenType.WORD) && !isFdPrefixForRedirect()) {
                    // Check if this word is followed by LBRACE (prefix + brace like file{1,2}.txt)
                    if (pos + 1 < tokens.size() && tokens.get(pos + 1).type() == TokenType.LBRACE) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(advance().value()); // consume WORD prefix
                        // Consume LBRACE...RBRACE
                        int braceDepth = 0;
                        while (!check(TokenType.EOF)) {
                            if (check(TokenType.LBRACE)) {
                                braceDepth++;
                                sb.append(advance().value());
                            } else if (check(TokenType.RBRACE)) {
                                braceDepth--;
                                sb.append(advance().value());
                                if (braceDepth == 0) break;
                            } else if (check(TokenType.WORD)) {
                                sb.append(advance().value());
                            } else {
                                break;
                            }
                        }
                        // Consume trailing WORD(s) as suffix
                        while (check(TokenType.WORD) && !isFdPrefixForRedirect()) {
                            sb.append(advance().value());
                        }
                        args.add(parseWordValue(sb.toString(), previous().line()));
                    } else {
                        args.add(parseWord());
                    }
                } else if (check(TokenType.LBRACE)) {
                    // Brace-only arg like {a,b,c} or {a,{b,c}}
                    StringBuilder sb = new StringBuilder();
                    int braceDepth = 0;
                    while (!check(TokenType.EOF)) {
                        if (check(TokenType.LBRACE)) {
                            braceDepth++;
                            sb.append(advance().value());
                        } else if (check(TokenType.RBRACE)) {
                            braceDepth--;
                            sb.append(advance().value());
                            if (braceDepth == 0) break;
                        } else if (check(TokenType.WORD)) {
                            sb.append(advance().value());
                        } else {
                            break;
                        }
                    }
                    // Consume trailing WORD(s) as suffix
                    while (check(TokenType.WORD) && !isFdPrefixForRedirect()) {
                        sb.append(advance().value());
                    }
                    args.add(parseWordValue(sb.toString(), previous().line()));
                } else {
                    break;
                }
            }
        }
        // Parse trailing redirections
        parseRedirections(redirections);
        return new SimpleCommandNode(
            current().line(), name, args, assignments, redirections);
    }

    private void parseRedirections(List<RedirectionNode> redirections) {
        while (true) {
            Optional<Integer> fd = Optional.empty();
            boolean ampersandPrefix = false;

            // Check for fd prefix: WORD(number)
            if (check(TokenType.WORD) && current().value().matches("\\d+")
                && pos + 1 < tokens.size()
                && isRedirectOperator(tokens.get(pos + 1).type())) {
                fd = Optional.of(Integer.parseInt(current().value()));
                advance();
            }
            // Check for &> or &>> prefix
            else if (check(TokenType.WORD) && current().value().equals("&")
                && pos + 1 < tokens.size()
                && (tokens.get(pos + 1).type() == TokenType.GREAT
                    || tokens.get(pos + 1).type() == TokenType.DGREAT)) {
                ampersandPrefix = true;
                advance(); // consume &
            }

            if (match(TokenType.GREAT)) {
                WordNode target = parseWord();
                if (ampersandPrefix) {
                    // &> redirects both stdout and stderr
                    redirections.add(newRedir(previous().line(), 1,
                        RedirectionNode.RedirectionOperator.GT, target));
                    redirections.add(newRedir(previous().line(), 2,
                        RedirectionNode.RedirectionOperator.GT, target));
                } else {
                    redirections.add(newRedir(previous().line(),
                        fd.orElse(1), RedirectionNode.RedirectionOperator.GT, target));
                }
            } else if (match(TokenType.DGREAT)) {
                WordNode target = parseWord();
                if (ampersandPrefix) {
                    redirections.add(newRedir(previous().line(), 1,
                        RedirectionNode.RedirectionOperator.GTGT, target));
                    redirections.add(newRedir(previous().line(), 2,
                        RedirectionNode.RedirectionOperator.GTGT, target));
                } else {
                    redirections.add(newRedir(previous().line(),
                        fd.orElse(1), RedirectionNode.RedirectionOperator.GTGT, target));
                }
            } else if (match(TokenType.LESS)) {
                WordNode target = parseWord();
                redirections.add(newRedir(previous().line(),
                    fd.orElse(0), RedirectionNode.RedirectionOperator.LT, target));
            } else if (match(TokenType.GREATAND)) {
                WordNode target = parseWord();
                redirections.add(newRedir(previous().line(),
                    fd.orElse(1), RedirectionNode.RedirectionOperator.GTAMP, target));
            } else if (match(TokenType.LESSAND)) {
                WordNode target = parseWord();
                redirections.add(newRedir(previous().line(),
                    fd.orElse(0), RedirectionNode.RedirectionOperator.LTAMP, target));
            } else if (match(TokenType.LESSGREAT)) {
                WordNode target = parseWord();
                redirections.add(newRedir(previous().line(),
                    fd.orElse(0), RedirectionNode.RedirectionOperator.LTGT, target));
            } else if (match(TokenType.CLOBBER)) {
                WordNode target = parseWord();
                redirections.add(newRedir(previous().line(),
                    fd.orElse(1), RedirectionNode.RedirectionOperator.GTPipe, target));
            } else {
                break;
            }
        }
    }

    private RedirectionNode newRedir(int line, int fd,
                                     RedirectionNode.RedirectionOperator op,
                                     WordNode target) {
        return new RedirectionNode(line, Optional.of(fd), Optional.empty(), op,
            new RedirectionNode.WordTarget(target));
    }

    private boolean isRedirectOperator(TokenType type) {
        return type == TokenType.GREAT || type == TokenType.DGREAT
            || type == TokenType.LESS || type == TokenType.GREATAND
            || type == TokenType.LESSAND || type == TokenType.LESSGREAT
            || type == TokenType.CLOBBER;
    }

    private boolean isFdPrefixForRedirect() {
        if (!check(TokenType.WORD)) return false;
        String value = current().value();
        if (!value.matches("\\d+")) return false;
        return pos + 1 < tokens.size() && isRedirectOperator(tokens.get(pos + 1).type());
    }

    private AssignmentNode parseAssignment() {
        Token token = advance();
        String text = token.value();
        int eqPos = text.indexOf("=");
        String namePart = text.substring(0, eqPos);
        String value = text.substring(eqPos + 1);

        // Check for indexed array assignment: arr[index]=value
        int bracketPos = namePart.indexOf('[');
        if (bracketPos > 0 && namePart.endsWith("]")) {
            String name = namePart.substring(0, bracketPos);
            String indexStr = namePart.substring(bracketPos + 1, namePart.length() - 1);
            WordNode indexWord = parseWordValue(indexStr, token.line());
            WordNode valueWord = parseWordValue(value, token.line());
            return AssignmentNode.ofIndexed(token.line(), name, indexWord, valueWord);
        }

        WordNode valueWord = parseWordValue(value, token.line());
        return AssignmentNode.ofSimple(token.line(), namePart, valueWord);
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
                // Arithmetic expansion $((...))
                if (i + 3 < value.length() && value.charAt(i + 1) == '(' && value.charAt(i + 2) == '(') {
                    int end = findMatchingDoubleParen(value, i + 3);
                    String inner = value.substring(i + 3, end - 2);
                    var arithExpr = ArithmeticParser.parse(inner, line);
                    parts.add(new ArithmeticExpansionPart(line,
                        new ArithmeticExpressionNode(line, arithExpr, Optional.of(inner))));
                    i = end;
                    continue;
                }
                // Command substitution $(...)
                if (i + 2 < value.length() && value.charAt(i + 1) == '(' && value.charAt(i + 2) != '(') {
                    int end = findMatchingParen(value, i + 2);
                    String inner = value.substring(i + 2, end);
                    ScriptNode body = Parser.parse(inner);
                    parts.add(new CommandSubstitutionPart(line, body, false));
                    i = end + 1;
                    continue;
                }
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
                    if (paramEnd < value.length() && "@*#?-$!".indexOf(value.charAt(paramEnd)) >= 0) {
                        parts.add(new ParameterExpansionPart(line, value.substring(paramStart, paramStart + 1), Optional.empty()));
                        i = paramStart + 1;
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
        // Don't match arr= if next token is LPAREN (array literal assignment)
        if (value.endsWith("=") && pos + 1 < tokens.size()
            && tokens.get(pos + 1).type() == TokenType.LPAREN) {
            return false;
        }
        int eqPos = value.indexOf("=");
        if (eqPos <= 0) return false;
        String name = value.substring(0, eqPos);
        // Simple assignment: name=value
        if (name.matches("[a-zA-Z_][a-zA-Z0-9_]*")) return true;
        // Indexed array assignment: name[index]=value
        int bracketPos = name.indexOf('[');
        if (bracketPos > 0 && name.endsWith("]")) {
            String arrName = name.substring(0, bracketPos);
            return arrName.matches("[a-zA-Z_][a-zA-Z0-9_]*");
        }
        return false;
    }

    private boolean checkArrayAssignment() {
        if (!check(TokenType.WORD)) return false;
        String value = current().value();
        // Handle arr= where lexer includes = in the word
        String name = value.endsWith("=") ? value.substring(0, value.length() - 1) : value;
        if (!name.matches("[a-zA-Z_][a-zA-Z0-9_]*")) return false;
        return pos + 1 < tokens.size() && tokens.get(pos + 1).type() == TokenType.LPAREN;
    }

    private AssignmentNode parseArrayAssignment() {
        Token nameToken = advance(); // WORD(name=) or WORD(name)
        String value = nameToken.value();
        // Handle arr= where lexer includes = in the word
        String name = value.endsWith("=") ? value.substring(0, value.length() - 1) : value;
        advance(); // LPAREN
        List<WordNode> elements = new ArrayList<>();
        while (!check(TokenType.RPAREN) && !check(TokenType.EOF)) {
            elements.add(parseWord());
        }
        if (check(TokenType.RPAREN)) {
            advance(); // RPAREN
        }
        return AssignmentNode.ofArray(nameToken.line(), name, elements);
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
                     TokenType.UNTIL, TokenType.CASE, TokenType.FUNCTION, TokenType.LPAREN,
                     TokenType.LBRACE, TokenType.BANG);
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

    private static int findMatchingDoubleParen(String value, int start) {
        int depth = 2;
        int i = start;
        while (i < value.length()) {
            char c = value.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            i++;
            if (depth == 0) return i;
        }
        return value.length();
    }

    private static int findMatchingParen(String value, int start) {
        int depth = 1;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        int i = start;
        while (i < value.length()) {
            char c = value.charAt(i);
            if (!inSingleQuote && c == '"' && !inDoubleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (!inDoubleQuote && c == '\'') {
                inSingleQuote = !inSingleQuote;
            } else if (!inSingleQuote && !inDoubleQuote) {
                if (c == '(') depth++;
                else if (c == ')') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
            i++;
        }
        return value.length();
    }
}
