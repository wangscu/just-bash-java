package com.justbash.commands.queryengine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Tokenizer {

    private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
        Map.entry("and", TokenType.AND),
        Map.entry("or", TokenType.OR),
        Map.entry("not", TokenType.NOT),
        Map.entry("if", TokenType.IF),
        Map.entry("then", TokenType.THEN),
        Map.entry("elif", TokenType.ELIF),
        Map.entry("else", TokenType.ELSE),
        Map.entry("end", TokenType.END),
        Map.entry("as", TokenType.AS),
        Map.entry("try", TokenType.TRY),
        Map.entry("catch", TokenType.CATCH),
        Map.entry("true", TokenType.TRUE),
        Map.entry("false", TokenType.FALSE),
        Map.entry("null", TokenType.NULL),
        Map.entry("reduce", TokenType.REDUCE),
        Map.entry("foreach", TokenType.FOREACH),
        Map.entry("label", TokenType.LABEL),
        Map.entry("break", TokenType.BREAK),
        Map.entry("def", TokenType.DEF)
    );

    private static final Set<TokenType> KEYWORD_TOKEN_TYPES = Set.copyOf(KEYWORDS.values());

    public static Set<TokenType> getKeywordTokenTypes() {
        return KEYWORD_TOKEN_TYPES;
    }

    public List<Token> tokenize(String input) {
        List<Token> tokens = new ArrayList<>();
        int pos = 0;
        int length = input.length();

        while (pos < length) {
            int start = pos;
            char c = input.charAt(pos++);

            // Whitespace
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                continue;
            }

            // Comments
            if (c == '#') {
                while (pos < length && input.charAt(pos) != '\n') {
                    pos++;
                }
                continue;
            }

            // Two-character operators
            if (c == '.' && pos < length && input.charAt(pos) == '.') {
                pos++;
                tokens.add(new Token(TokenType.DOTDOT, start));
                continue;
            }
            if (c == '=' && pos < length && input.charAt(pos) == '=') {
                pos++;
                tokens.add(new Token(TokenType.EQ, start));
                continue;
            }
            if (c == '!' && pos < length && input.charAt(pos) == '=') {
                pos++;
                tokens.add(new Token(TokenType.NE, start));
                continue;
            }
            if (c == '<' && pos < length && input.charAt(pos) == '=') {
                pos++;
                tokens.add(new Token(TokenType.LE, start));
                continue;
            }
            if (c == '>' && pos < length && input.charAt(pos) == '=') {
                pos++;
                tokens.add(new Token(TokenType.GE, start));
                continue;
            }
            if (c == '/' && pos < length && input.charAt(pos) == '/') {
                pos++;
                if (pos < length && input.charAt(pos) == '=') {
                    pos++;
                    tokens.add(new Token(TokenType.UPDATE_ALT, start));
                } else {
                    tokens.add(new Token(TokenType.ALT, start));
                }
                continue;
            }
            if (c == '+' && pos < length && input.charAt(pos) == '=') {
                pos++;
                tokens.add(new Token(TokenType.UPDATE_ADD, start));
                continue;
            }
            if (c == '-' && pos < length && input.charAt(pos) == '=') {
                pos++;
                tokens.add(new Token(TokenType.UPDATE_SUB, start));
                continue;
            }
            if (c == '*' && pos < length && input.charAt(pos) == '=') {
                pos++;
                tokens.add(new Token(TokenType.UPDATE_MUL, start));
                continue;
            }
            if (c == '/' && pos < length && input.charAt(pos) == '=') {
                pos++;
                tokens.add(new Token(TokenType.UPDATE_DIV, start));
                continue;
            }
            if (c == '%' && pos < length && input.charAt(pos) == '=') {
                pos++;
                tokens.add(new Token(TokenType.UPDATE_MOD, start));
                continue;
            }
            if (c == '=' && (pos >= length || input.charAt(pos) != '=')) {
                tokens.add(new Token(TokenType.ASSIGN, start));
                continue;
            }

            // Single-character tokens
            if (c == '.') {
                tokens.add(new Token(TokenType.DOT, start));
                continue;
            }
            if (c == '|') {
                if (pos < length && input.charAt(pos) == '=') {
                    pos++;
                    tokens.add(new Token(TokenType.UPDATE_PIPE, start));
                } else {
                    tokens.add(new Token(TokenType.PIPE, start));
                }
                continue;
            }
            if (c == ',') {
                tokens.add(new Token(TokenType.COMMA, start));
                continue;
            }
            if (c == ':') {
                tokens.add(new Token(TokenType.COLON, start));
                continue;
            }
            if (c == ';') {
                tokens.add(new Token(TokenType.SEMICOLON, start));
                continue;
            }
            if (c == '(') {
                tokens.add(new Token(TokenType.LPAREN, start));
                continue;
            }
            if (c == ')') {
                tokens.add(new Token(TokenType.RPAREN, start));
                continue;
            }
            if (c == '[') {
                tokens.add(new Token(TokenType.LBRACKET, start));
                continue;
            }
            if (c == ']') {
                tokens.add(new Token(TokenType.RBRACKET, start));
                continue;
            }
            if (c == '{') {
                tokens.add(new Token(TokenType.LBRACE, start));
                continue;
            }
            if (c == '}') {
                tokens.add(new Token(TokenType.RBRACE, start));
                continue;
            }
            if (c == '?') {
                tokens.add(new Token(TokenType.QUESTION, start));
                continue;
            }
            if (c == '+') {
                tokens.add(new Token(TokenType.PLUS, start));
                continue;
            }
            if (c == '-') {
                tokens.add(new Token(TokenType.MINUS, start));
                continue;
            }
            if (c == '*') {
                tokens.add(new Token(TokenType.STAR, start));
                continue;
            }
            if (c == '/') {
                tokens.add(new Token(TokenType.SLASH, start));
                continue;
            }
            if (c == '%') {
                tokens.add(new Token(TokenType.PERCENT, start));
                continue;
            }
            if (c == '<') {
                tokens.add(new Token(TokenType.LT, start));
                continue;
            }
            if (c == '>') {
                tokens.add(new Token(TokenType.GT, start));
                continue;
            }

            // Numbers
            if (isDigit(c)) {
                StringBuilder num = new StringBuilder();
                num.append(c);
                while (pos < length) {
                    char ch = input.charAt(pos);
                    if (isDigit(ch) || ch == '.' || ch == 'e' || ch == 'E') {
                        if ((ch == 'e' || ch == 'E') && pos + 1 < length) {
                            char next = input.charAt(pos + 1);
                            if (next == '+' || next == '-') {
                                num.append(ch);
                                num.append(next);
                                pos += 2;
                                continue;
                            }
                        }
                        num.append(ch);
                        pos++;
                    } else {
                        break;
                    }
                }
                String numStr = num.toString();
                Object value;
                if (numStr.contains(".") || numStr.contains("e") || numStr.contains("E")) {
                    value = Double.parseDouble(numStr);
                } else {
                    try {
                        value = Long.parseLong(numStr);
                    } catch (NumberFormatException e) {
                        value = Double.parseDouble(numStr);
                    }
                }
                tokens.add(new Token(TokenType.NUMBER, value, start));
                continue;
            }

            // Strings
            if (c == '"') {
                StringBuilder str = new StringBuilder();
                while (pos < length && input.charAt(pos) != '"') {
                    if (input.charAt(pos) == '\\') {
                        pos++;
                        if (pos >= length) break;
                        char escaped = input.charAt(pos++);
                        switch (escaped) {
                            case 'n':
                                str.append('\n');
                                break;
                            case 'r':
                                str.append('\r');
                                break;
                            case 't':
                                str.append('\t');
                                break;
                            case '\\':
                                str.append('\\');
                                break;
                            case '"':
                                str.append('"');
                                break;
                            case '(':
                                str.append("\\(");
                                break; // Keep for string interpolation
                            case 'b':
                                str.append('\b');
                                break;
                            case 'f':
                                str.append('\f');
                                break;
                            case 'u':
                                if (pos + 4 <= length) {
                                    String hex = input.substring(pos, pos + 4);
                                    try {
                                        int code = Integer.parseInt(hex, 16);
                                        str.append((char) code);
                                        pos += 4;
                                    } catch (NumberFormatException e) {
                                        str.append('u');
                                    }
                                } else {
                                    str.append('u');
                                }
                                break;
                            default:
                                if (isDigit(escaped)) {
                                    int octal = escaped - '0';
                                    while (pos < length && isDigit(input.charAt(pos)) && octal < 512) {
                                        octal = octal * 8 + (input.charAt(pos) - '0');
                                        pos++;
                                    }
                                    str.append((char) octal);
                                } else {
                                    str.append(escaped);
                                }
                        }
                    } else {
                        str.append(input.charAt(pos++));
                    }
                }
                if (pos < length) pos++; // closing quote
                tokens.add(new Token(TokenType.STRING, str.toString(), start));
                continue;
            }

            // Identifiers and keywords
            if (isAlpha(c) || c == '$' || c == '@') {
                StringBuilder ident = new StringBuilder();
                ident.append(c);
                while (pos < length && isAlnum(input.charAt(pos))) {
                    ident.append(input.charAt(pos++));
                }
                String identStr = ident.toString();
                TokenType keyword = KEYWORDS.get(identStr);
                if (keyword != null) {
                    tokens.add(new Token(keyword, identStr, start));
                } else {
                    tokens.add(new Token(TokenType.IDENT, identStr, start));
                }
                continue;
            }

            throw new ParseException("Unexpected character '" + c + "' at position " + start);
        }

        tokens.add(new Token(TokenType.EOF, pos));
        return tokens;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isAlnum(char c) {
        return isAlpha(c) || isDigit(c);
    }
}
