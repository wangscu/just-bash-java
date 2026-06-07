package com.justbash.commands.queryengine;

/**
 * Custom error that preserves the original jq error value.
 */
public class JqException extends RuntimeException {
    private final Object value;

    public JqException(Object value) {
        super(value instanceof String ? (String) value : valueToString(value));
        this.value = value;
    }

    public Object getValue() {
        return value;
    }

    private static String valueToString(Object value) {
        if (value == null) return "null";
        try {
            // Simple JSON-like representation for common types
            if (value instanceof String) return "\"" + value + "\"";
            if (value instanceof Boolean) return value.toString();
            if (value instanceof Number) return value.toString();
            return value.toString();
        } catch (Exception e) {
            return value.toString();
        }
    }
}
