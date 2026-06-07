package com.justbash.commands.queryengine;

import java.util.List;

public sealed interface DestructurePattern permits
    DestructurePattern.VarPattern, DestructurePattern.ArrayPattern, DestructurePattern.ObjectPattern {

    record VarPattern(String name) implements DestructurePattern {}

    record ArrayPattern(List<DestructurePattern> elements) implements DestructurePattern {}

    record ObjectPattern(List<ObjectField> fields) implements DestructurePattern {
        public ObjectPattern {
            fields = List.copyOf(fields);
        }

        public record ObjectField(Object key, DestructurePattern pattern, String keyVar) {}
    }
}
