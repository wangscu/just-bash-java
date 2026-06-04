package com.justbash.ast.word;

import com.justbash.ast.ScriptNode;

public record CommandSubstitutionPart(
    int line, ScriptNode body, boolean legacy
) implements WordPart {
    @Override public String type() { return "CommandSubstitution"; }
}
