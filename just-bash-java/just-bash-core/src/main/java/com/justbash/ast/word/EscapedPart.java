package com.justbash.ast.word;

public record EscapedPart(int line, String value) implements WordPart {
    @Override public String type() { return "Escaped"; }
}
