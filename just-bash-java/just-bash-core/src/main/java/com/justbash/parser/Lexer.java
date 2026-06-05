package com.justbash.parser;

import java.util.ArrayList;
import java.util.List;

public class Lexer {
    private final String input;
    private int pos = 0;
    private int line = 1;
    private int column = 1;

    public Lexer(String input) {
        this.input = input;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (!isAtEnd()) {
            skipWhitespace();
            if (isAtEnd()) break;
            Token token = nextToken();
            if (token != null) {
                tokens.add(token);
            }
        }
        tokens.add(new Token(TokenType.EOF, "", line, column));
        return tokens;
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
                if (match('-')) {
                    return new Token(TokenType.DLESSDASH, "<<-", startLine, startCol);
                }
                return new Token(TokenType.DLESS, "<<", startLine, startCol);
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
            } else if (match('&')) {
                return new Token(TokenType.GREATAND, ">&", startLine, startCol);
            } else if (match('|')) {
                return new Token(TokenType.CLOBBER, ">|", startLine, startCol);
            }
            return new Token(TokenType.GREAT, ">", startLine, startCol);
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
