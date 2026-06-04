package com.justbash.ast.word;

public record LiteralPart(int line, String value) implements WordPart {
    @Override public String type() { return "Literal"; }
}
