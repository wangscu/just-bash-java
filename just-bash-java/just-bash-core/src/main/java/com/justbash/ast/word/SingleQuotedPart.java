package com.justbash.ast.word;

public record SingleQuotedPart(int line, String value) implements WordPart {
    @Override public String type() { return "SingleQuoted"; }
}
