package com.justbash.ast.word;

import com.justbash.ast.ScriptNode;

public record ProcessSubstitutionPart(
    int line, ScriptNode body, Direction direction
) implements WordPart {
    @Override public String type() { return "ProcessSubstitution"; }
    public enum Direction { INPUT, OUTPUT }
}
