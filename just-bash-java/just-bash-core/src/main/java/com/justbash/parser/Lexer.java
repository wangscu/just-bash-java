package com.justbash.parser;

import com.justbash.ast.HereDocNode;
import com.justbash.ast.word.LiteralPart;
import com.justbash.ast.word.WordNode;
import java.util.ArrayList;
import java.util.List;

public class Lexer {
    private final String input;
    private int pos = 0;
    private int line = 1;
    private int column = 1;
    private final List<HereDocNode> heredocs = new ArrayList<>();

    public Lexer(String input) {
        this.input = input;
    }

    public List<HereDocNode> getHeredocs() {
        return heredocs;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        List<PendingHeredoc> pending = new ArrayList<>();
        boolean heredocBodyMode = false;

        while (!isAtEnd()) {
            // If we're collecting heredoc bodies, read lines directly
            if (heredocBodyMode && !pending.isEmpty()) {
                String lineStr = readLine();
                if (lineStr == null) break;

                // Check if this line matches any pending delimiter (FIFO order)
                boolean foundDelimiter = false;
                for (int i = 0; i < pending.size(); i++) {
                    PendingHeredoc ph = pending.get(i);
                    String check = ph.stripTabs
                        ? lineStr.replaceFirst("^\t+", "")
                        : lineStr;
                    if (check.equals(ph.delimiter)) {
                        heredocs.add(makeHereDocNode(ph));
                        pending.remove(i);
                        foundDelimiter = true;
                        break;
                    }
                }

                if (!foundDelimiter) {
                    // Line belongs to the first pending heredoc
                    PendingHeredoc ph = pending.get(0);
                    String bodyLine = ph.stripTabs
                        ? lineStr.replaceFirst("^\t+", "")
                        : lineStr;
                    ph.body.append(bodyLine).append('\n');
                }

                if (pending.isEmpty()) {
                    heredocBodyMode = false;
                }
                continue;
            }

            skipWhitespace();
            if (isAtEnd()) break;

            Token token = nextToken();
            if (token != null) {
                tokens.add(token);
                if (token.type() == TokenType.DLESS || token.type() == TokenType.DLESSDASH) {
                    // Read the delimiter word
                    skipWhitespace();
                    if (!isAtEnd() && peek() != '\n') {
                        Token delimToken = readWord(line, column);
                        if (delimToken != null) {
                            tokens.add(delimToken);
                            String rawDelim = delimToken.value();
                            boolean quoted = isQuoted(rawDelim);
                            String cleanDelim = quoted ? unquote(rawDelim) : rawDelim;
                            pending.add(new PendingHeredoc(cleanDelim, quoted,
                                token.type() == TokenType.DLESSDASH));
                        }
                    }
                }
                if (token.type() == TokenType.NEWLINE && !pending.isEmpty()) {
                    heredocBodyMode = true;
                }
            }
        }

        // Complete any remaining pending heredocs with empty body
        for (PendingHeredoc ph : pending) {
            heredocs.add(makeHereDocNode(ph));
        }

        tokens.add(new Token(TokenType.EOF, "", line, column));
        return tokens;
    }

    private String readLine() {
        int startPos = pos;
        while (!isAtEnd() && peek() != '\n') {
            advance();
        }
        String result = input.substring(startPos, pos);
        // Consume the trailing newline
        if (!isAtEnd() && peek() == '\n') {
            advance();
            line++;
            column = 1;
        }
        return result;
    }

    private HereDocNode makeHereDocNode(PendingHeredoc ph) {
        String body = ph.body.toString();
        // Remove trailing newline that was added after the last body line
        // In bash, the heredoc body does not include a trailing newline
        // after the last line (each line's newline separates it from the next)
        if (body.endsWith("\n")) {
            body = body.substring(0, body.length() - 1);
        }
        WordNode content = new WordNode(ph.line,
            List.of(new LiteralPart(ph.line, body)));
        return new HereDocNode(ph.line, ph.delimiter, content, ph.stripTabs, ph.quoted);
    }

    private static boolean isQuoted(String s) {
        return (s.startsWith("'") && s.endsWith("'"))
            || (s.startsWith("\"") && s.endsWith("\""));
    }

    private static String unquote(String s) {
        return s.substring(1, s.length() - 1);
    }

    private static class PendingHeredoc {
        final String delimiter;
        final boolean quoted;
        final boolean stripTabs;
        final int line;
        final StringBuilder body = new StringBuilder();

        PendingHeredoc(String delimiter, boolean quoted, boolean stripTabs) {
            this.delimiter = delimiter;
            this.quoted = quoted;
            this.stripTabs = stripTabs;
            this.line = 0; // line info not critical for MVP
        }
    }

    private Token nextToken() {
        int startLine = line;
        int startCol = column;
        char c = peek();

        if (c == '\n') {
            advance();
            line++;
            column = 1;
            return new Token(TokenType.NEWLINE, "\n", startLine, startCol);
        }
        if (c == ';') {
            advance();
            if (match(';')) {
                return new Token(TokenType.DSEMI, ";;", startLine, startCol);
            }
            return new Token(TokenType.SEMI, ";", startLine, startCol);
        }
        if (c == '|') {
            advance();
            if (match('|')) {
                return new Token(TokenType.OR_IF, "||", startLine, startCol);
            }
            if (match('&')) {
                return new Token(TokenType.PIPE_AMPERSAND, "|&", startLine, startCol);
            }
            return new Token(TokenType.PIPE, "|", startLine, startCol);
        }
        if (c == '&') {
            advance();
            if (match('&')) {
                return new Token(TokenType.AND_IF, "&&", startLine, startCol);
            }
            return readWord(startLine, startCol);
        }
        if (c == '<') {
            advance();
            if (match('<')) {
                if (match('<')) {
                    return new Token(TokenType.TRIPLE_LESS, "<<<", startLine, startCol);
                }
                if (match('-')) {
                    return new Token(TokenType.DLESSDASH, "<<-", startLine, startCol);
                }
                return new Token(TokenType.DLESS, "<<", startLine, startCol);
            } else if (match('(')) {
                // Process substitution <(...)
                StringBuilder sb = new StringBuilder();
                sb.append("<(");
                int depth = 1;
                boolean inSingleQuote = false;
                boolean inDoubleQuote = false;
                while (!isAtEnd() && depth > 0) {
                    char ch = advance();
                    sb.append(ch);
                    if (!inSingleQuote && ch == '"' && !inDoubleQuote) {
                        inDoubleQuote = !inDoubleQuote;
                    } else if (!inDoubleQuote && ch == '\'') {
                        inSingleQuote = !inSingleQuote;
                    } else if (!inSingleQuote && !inDoubleQuote) {
                        if (ch == '(') depth++;
                        else if (ch == ')') depth--;
                    }
                }
                return new Token(TokenType.WORD, sb.toString(), startLine, startCol);
            } else if (match('&')) {
                return new Token(TokenType.LESSAND, "<&", startLine, startCol);
            } else if (match('>')) {
                return new Token(TokenType.LESSGREAT, "<>", startLine, startCol);
            }
            return new Token(TokenType.LESS, "<", startLine, startCol);
        }
        if (c == '>') {
            advance();
            if (match('>')) {
                return new Token(TokenType.DGREAT, ">>", startLine, startCol);
            } else if (match('(')) {
                // Process substitution >(...)
                StringBuilder sb = new StringBuilder();
                sb.append(">(");
                int depth = 1;
                boolean inSingleQuote = false;
                boolean inDoubleQuote = false;
                while (!isAtEnd() && depth > 0) {
                    char ch = advance();
                    sb.append(ch);
                    if (!inSingleQuote && ch == '"' && !inDoubleQuote) {
                        inDoubleQuote = !inDoubleQuote;
                    } else if (!inDoubleQuote && ch == '\'') {
                        inSingleQuote = !inSingleQuote;
                    } else if (!inSingleQuote && !inDoubleQuote) {
                        if (ch == '(') depth++;
                        else if (ch == ')') depth--;
                    }
                }
                return new Token(TokenType.WORD, sb.toString(), startLine, startCol);
            } else if (match('&')) {
                return new Token(TokenType.GREATAND, ">&", startLine, startCol);
            } else if (match('|')) {
                return new Token(TokenType.CLOBBER, ">|", startLine, startCol);
            }
            return new Token(TokenType.GREAT, ">", startLine, startCol);
        }
        if (c == '[' && !isAtEnd(1) && peek(1) == '[') {
            advance(); advance();
            return new Token(TokenType.DLBRACKET, "[[", startLine, startCol);
        }
        if (c == ']' && !isAtEnd(1) && peek(1) == ']') {
            advance(); advance();
            return new Token(TokenType.DRBRACKET, "]]", startLine, startCol);
        }
        if (c == '(') {
            advance();
            return new Token(TokenType.LPAREN, "(", startLine, startCol);
        }
        if (c == ')') {
            advance();
            return new Token(TokenType.RPAREN, ")", startLine, startCol);
        }
        if (c == '!') {
            advance();
            if (match('=')) {
                return new Token(TokenType.WORD, "!=", startLine, startCol);
            }
            return new Token(TokenType.BANG, "!", startLine, startCol);
        }
        if (c == '{') {
            advance();
            return new Token(TokenType.LBRACE, "{", startLine, startCol);
        }
        if (c == '}') {
            advance();
            return new Token(TokenType.RBRACE, "}", startLine, startCol);
        }
        if (c == '#') {
            skipComment();
            return null;
        }
        return readWord(startLine, startCol);
    }

    private Token readWord(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        while (!isAtEnd()) {
            char ch = peek();
            if (ch == '$') {
                // Check for $(( arithmetic expansion
                if (!isAtEnd(1) && peek(1) == '(' && !isAtEnd(2) && peek(2) == '(') {
                    sb.append(advance()); // $
                    sb.append(advance()); // (
                    sb.append(advance()); // (
                    int depth = 2;
                    while (!isAtEnd() && depth > 0) {
                        char c = advance();
                        sb.append(c);
                        if (c == '(') depth++;
                        else if (c == ')') depth--;
                    }
                    continue;
                }
                // Check for $( command substitution
                if (!isAtEnd(1) && peek(1) == '(') {
                    sb.append(advance()); // $
                    sb.append(advance()); // (
                    int depth = 1;
                    boolean inSingleQuote = false;
                    boolean inDoubleQuote = false;
                    while (!isAtEnd() && depth > 0) {
                        char c = advance();
                        sb.append(c);
                        if (!inSingleQuote && c == '"' && !inDoubleQuote) {
                            inDoubleQuote = !inDoubleQuote;
                        } else if (!inDoubleQuote && c == '\'') {
                            inSingleQuote = !inSingleQuote;
                        } else if (!inSingleQuote && !inDoubleQuote) {
                            if (c == '(') depth++;
                            else if (c == ')') depth--;
                        }
                    }
                    continue;
                }
                // Check for ${ parameter expansion
                if (!isAtEnd(1) && peek(1) == '{') {
                    sb.append(advance()); // $
                    sb.append(advance()); // {
                    while (!isAtEnd() && peek() != '}') {
                        sb.append(advance());
                    }
                    if (!isAtEnd() && peek() == '}') {
                        sb.append(advance()); // }
                    }
                    continue;
                }
            }
            if (isWordDelimiter(ch)) {
                break;
            }
            sb.append(advance());
        }
        String value = sb.toString();
        TokenType type = classifyWord(value);
        return new Token(type, value, startLine, startCol);
    }

    private TokenType classifyWord(String word) {
        return switch (word) {
            case "if" -> TokenType.IF;
            case "then" -> TokenType.THEN;
            case "else" -> TokenType.ELSE;
            case "elif" -> TokenType.ELIF;
            case "fi" -> TokenType.FI;
            case "for" -> TokenType.FOR;
            case "while" -> TokenType.WHILE;
            case "until" -> TokenType.UNTIL;
            case "do" -> TokenType.DO;
            case "done" -> TokenType.DONE;
            case "case" -> TokenType.CASE;
            case "esac" -> TokenType.ESAC;
            case "in" -> TokenType.IN;
            case "function" -> TokenType.FUNCTION;
            case "time" -> TokenType.TIME;
            default -> TokenType.WORD;
        };
    }

    private boolean isWordDelimiter(char c) {
        return Character.isWhitespace(c) || ";|&<>()!{}\n".indexOf(c) >= 0;
    }

    private void skipWhitespace() {
        while (!isAtEnd() && (peek() == ' ' || peek() == '\t' || peek() == '\r')) {
            advance();
        }
    }

    private void skipComment() {
        while (!isAtEnd() && peek() != '\n') {
            advance();
        }
    }

    private char peek() {
        return isAtEnd() ? '\0' : input.charAt(pos);
    }

    private char peek(int offset) {
        return isAtEnd(offset) ? '\0' : input.charAt(pos + offset);
    }

    private char advance() {
        char c = input.charAt(pos++);
        column++;
        return c;
    }

    private boolean match(char expected) {
        if (isAtEnd() || peek() != expected) return false;
        advance();
        return true;
    }

    private boolean isAtEnd() {
        return pos >= input.length();
    }

    private boolean isAtEnd(int offset) {
        return pos + offset >= input.length();
    }
}
