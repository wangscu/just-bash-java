package com.justbash.interpreter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BraceExpander {

    /** Expand brace patterns in a string. Returns the original string if no braces found. */
    public static List<String> expand(String input) {
        int braceStart = findBraceStart(input);
        if (braceStart == -1) {
            return List.of(input);
        }
        int braceEnd = findMatchingBrace(input, braceStart);
        if (braceEnd == -1) {
            return List.of(input);
        }

        String prefix = input.substring(0, braceStart);
        String suffix = input.substring(braceEnd + 1);
        String content = input.substring(braceStart + 1, braceEnd);

        // Check for range: {1..10} or {a..z} or {1..10..2}
        Optional<List<String>> range = tryParseRange(content);
        if (range.isPresent()) {
            List<String> result = new ArrayList<>();
            for (String item : range.get()) {
                result.addAll(expand(prefix + item + suffix));
            }
            return result;
        }

        // Split by commas at top brace level
        List<String> items = splitByCommas(content);
        List<String> result = new ArrayList<>();
        for (String item : items) {
            result.addAll(expand(prefix + item + suffix));
        }
        return result;
    }

    private static int findBraceStart(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '{' && (i == 0 || s.charAt(i - 1) != '\\')) {
                return i;
            }
        }
        return -1;
    }

    private static int findMatchingBrace(String s, int start) {
        int depth = 1;
        for (int i = start + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{' && (i == 0 || s.charAt(i - 1) != '\\')) {
                depth++;
            } else if (c == '}' && (i == 0 || s.charAt(i - 1) != '\\')) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static Optional<List<String>> tryParseRange(String content) {
        // Range format: start..end or start..end..step
        // Must not contain commas (commas mean it's a list, not a range)
        if (content.contains(",")) return Optional.empty();

        int firstDotDot = content.indexOf("..");
        if (firstDotDot == -1) return Optional.empty();

        String startStr = content.substring(0, firstDotDot);
        String rest = content.substring(firstDotDot + 2);

        int secondDotDot = rest.indexOf("..");
        String endStr;
        int step = 1;
        if (secondDotDot != -1) {
            endStr = rest.substring(0, secondDotDot);
            String stepStr = rest.substring(secondDotDot + 2);
            try {
                step = Integer.parseInt(stepStr);
                if (step == 0) return Optional.empty();
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        } else {
            endStr = rest;
        }

        if (startStr.isEmpty() || endStr.isEmpty()) return Optional.empty();

        // Try numeric range
        try {
            int start = Integer.parseInt(startStr);
            int end = Integer.parseInt(endStr);
            List<String> result = new ArrayList<>();
            if (step > 0) {
                if (start <= end) {
                    for (int i = start; i <= end; i += step) {
                        result.add(String.valueOf(i));
                    }
                } else {
                    for (int i = start; i >= end; i -= step) {
                        result.add(String.valueOf(i));
                    }
                }
            } else {
                if (start >= end) {
                    for (int i = start; i >= end; i += step) {
                        result.add(String.valueOf(i));
                    }
                } else {
                    for (int i = start; i <= end; i -= step) {
                        result.add(String.valueOf(i));
                    }
                }
            }
            return Optional.of(result);
        } catch (NumberFormatException e) {
            // Not numeric, try alphabetic
        }

        // Try alphabetic range
        if (startStr.length() == 1 && endStr.length() == 1) {
            char start = startStr.charAt(0);
            char end = endStr.charAt(0);
            if (Character.isLetter(start) && Character.isLetter(end)) {
                List<String> result = new ArrayList<>();
                if (start <= end) {
                    for (char c = start; c <= end; c++) {
                        result.add(String.valueOf(c));
                    }
                } else {
                    for (char c = start; c >= end; c--) {
                        result.add(String.valueOf(c));
                    }
                }
                return Optional.of(result);
            }
        }

        return Optional.empty();
    }

    private static List<String> splitByCommas(String s) {
        List<String> result = new ArrayList<>();
        int start = 0;
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') depth--;
            else if (c == ',' && depth == 0) {
                result.add(s.substring(start, i));
                start = i + 1;
            }
        }
        result.add(s.substring(start));
        return result;
    }
}
