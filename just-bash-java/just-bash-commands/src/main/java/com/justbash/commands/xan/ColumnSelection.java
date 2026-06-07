package com.justbash.commands.xan;

import java.util.*;

public class ColumnSelection {

    public static List<String> parseColumnSpec(String spec, List<String> headers) {
        List<String> result = new ArrayList<>();
        Set<String> excludes = new HashSet<>();

        for (String part : spec.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;

            // Handle negation (exclude)
            if (trimmed.startsWith("!")) {
                String toExclude = trimmed.substring(1);
                excludes.addAll(parseColumnSpec(toExclude, headers));
                continue;
            }

            // Check if it's * (select all)
            if (trimmed.equals("*")) {
                for (String h : headers) {
                    if (!result.contains(h)) result.add(h);
                }
                continue;
            }

            // Check if it's a glob pattern
            if (trimmed.contains("*")) {
                String regex = trimmed.replace("*", ".*");
                for (String h : headers) {
                    if (h.matches(regex) && !result.contains(h)) {
                        result.add(h);
                    }
                }
                continue;
            }

            // Check if it's a column name range (e.g., "name:email", ":email", "name:")
            int colonIdx = trimmed.indexOf(':');
            if (colonIdx != -1 && colonIdx == trimmed.lastIndexOf(':')) {
                String startCol = trimmed.substring(0, colonIdx);
                String endCol = trimmed.substring(colonIdx + 1);
                int startIdx = startCol.isEmpty() ? 0 : headers.indexOf(startCol);
                int endIdx = endCol.isEmpty() ? headers.size() - 1 : headers.indexOf(endCol);

                if (startIdx != -1 && endIdx != -1) {
                    int step = startIdx <= endIdx ? 1 : -1;
                    for (int i = startIdx; step > 0 ? i <= endIdx : i >= endIdx; i += step) {
                        if (!result.contains(headers.get(i))) {
                            result.add(headers.get(i));
                        }
                    }
                }
                continue;
            }

            // Check if it's a numeric range (e.g., "0-2")
            if (trimmed.matches("\\d+-\\d+")) {
                String[] parts = trimmed.split("-");
                int start = Integer.parseInt(parts[0]);
                int end = Integer.parseInt(parts[1]);
                for (int i = start; i <= end && i < headers.size(); i++) {
                    result.add(headers.get(i));
                }
                continue;
            }

            // Check if it's an index
            try {
                int idx = Integer.parseInt(trimmed);
                if (idx >= 0 && idx < headers.size()) {
                    result.add(headers.get(idx));
                    continue;
                }
            } catch (NumberFormatException e) {
                // Not an index
            }

            // Otherwise treat as column name
            if (headers.contains(trimmed)) {
                result.add(trimmed);
            }
        }

        if (!excludes.isEmpty()) {
            return result.stream().filter(col -> !excludes.contains(col)).toList();
        }
        return result;
    }
}
