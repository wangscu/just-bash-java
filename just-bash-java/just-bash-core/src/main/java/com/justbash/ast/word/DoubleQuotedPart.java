package com.justbash.ast.word;

import java.util.List;

public record DoubleQuotedPart(int line, List<WordPart> parts) implements WordPart {
    @Override public String type() { return "DoubleQuoted"; }
}
