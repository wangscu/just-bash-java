package com.justbash.ast.word;

public record GlobPart(int line, String pattern) implements WordPart {
    @Override public String type() { return "Glob"; }
}
