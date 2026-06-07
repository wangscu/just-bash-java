package com.justbash.postgres;

import java.util.Map;

public final class PathEncoding {
    private static final int MAX_LTREE_LABEL_LENGTH = 255;

    private static final Map<String, String> SPECIAL_ENCODINGS = Map.of(
        ".", "__dot__",
        " ", "__sp__"
    );
    private static final Map<String, String> DECODE_MAP = Map.of(
        "__dot__", ".",
        "__sp__", " ",
        "__5F__", "_"
    );

    private PathEncoding() {}

    public static String encodeLabel(String name) {
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Cannot encode empty filename");
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '\0') {
                throw new IllegalArgumentException("Filenames cannot contain null bytes");
            }
            String special = SPECIAL_ENCODINGS.get(String.valueOf(c));
            if (special != null) {
                result.append(special);
            } else if (c == '_') {
                result.append("__5F__");
            } else if (isAsciiLetterOrDigitOrHyphen(c)) {
                result.append(c);
            } else {
                String hex = Integer.toHexString(c).toUpperCase();
                result.append("__").append(hex).append("__");
            }
        }
        if (result.length() > MAX_LTREE_LABEL_LENGTH) {
            throw new IllegalArgumentException(
                "Encoded filename exceeds ltree label limit of " + MAX_LTREE_LABEL_LENGTH + " characters");
        }
        return result.toString();
    }

    public static String decodeLabel(String label) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < label.length()) {
            int next = label.indexOf("__", i);
            if (next == -1) {
                result.append(label.substring(i));
                break;
            }
            result.append(label, i, next);
            int end = label.indexOf("__", next + 2);
            if (end == -1) {
                result.append(label.substring(next));
                break;
            }
            String code = label.substring(next, end + 2);
            String decoded = DECODE_MAP.get(code);
            if (decoded != null) {
                result.append(decoded);
            } else {
                String inner = code.substring(2, code.length() - 2);
                try {
                    int charCode = Integer.parseInt(inner, 16);
                    if (charCode > 0) {
                        result.append((char) charCode);
                    } else {
                        result.append(code);
                    }
                } catch (NumberFormatException e) {
                    result.append(code);
                }
            }
            i = end + 2;
        }
        return result.toString();
    }

    public static String normalizePath(String path) {
        if (path.contains("\0")) {
            throw new IllegalArgumentException("Paths cannot contain null bytes");
        }
        String[] parts = path.split("/");
        java.util.ArrayList<String> resolved = new java.util.ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (!resolved.isEmpty()) {
                    resolved.remove(resolved.size() - 1);
                }
                continue;
            }
            resolved.add(part);
        }
        return "/" + String.join("/", resolved);
    }

    public static String pathToLtree(String posixPath, long sessionId) {
        String normalized = normalizePath(posixPath);
        String[] segments = normalized.split("/");
        String prefix = "s" + sessionId;
        java.util.ArrayList<String> encoded = new java.util.ArrayList<>();
        for (String segment : segments) {
            if (!segment.isEmpty()) {
                encoded.add(encodeLabel(segment));
            }
        }
        if (encoded.isEmpty()) {
            return prefix;
        }
        return prefix + "." + String.join(".", encoded);
    }

    public static String ltreeToPath(String ltree) {
        String[] parts = ltree.split("\\.");
        // First part is the session prefix (s42), skip it
        java.util.ArrayList<String> segments = new java.util.ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            segments.add(decodeLabel(parts[i]));
        }
        if (segments.isEmpty()) {
            return "/";
        }
        return "/" + String.join("/", segments);
    }

    private static boolean isAsciiLetterOrDigitOrHyphen(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-';
    }
}
