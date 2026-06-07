package com.justbash.commands.queryengine;

public record Token(TokenType type, Object value, int pos) {
    public Token(TokenType type, int pos) {
        this(type, null, pos);
    }
}
