package com.justbash.ast.word;

public record TildeExpansionPart(int line, String user) implements WordPart {
    @Override public String type() { return "TildeExpansion"; }
}
