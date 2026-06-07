package com.justbash.commands.queryengine;

import java.util.*;

/**
 * Query Path Utilities
 *
 * Utility functions for path-based operations on query values.
 */
public final class PathOperations {

    private static final int MAX_ARRAY_INDEX = 536870911; // jq's limit
    private static final Set<String> DANGEROUS_KEYS = new HashSet<>(Arrays.asList(
        "__proto__", "constructor", "prototype"
    ));

    private PathOperations() {}

    /**
     * Check if a key is safe to use for object property access/assignment.
     * Returns true if the key is safe, false if it could cause prototype pollution.
     */
    private static boolean isSafeKey(String key) {
        return !DANGEROUS_KEYS.contains(key);
    }

    /**
     * Set a value at a given path within a query value.
     * Creates intermediate arrays/objects as needed.
     */
    public static Object setPath(Object value, List<Object> path, Object newVal) {
        if (path.isEmpty()) {
            return newVal;
        }

        Object head = path.get(0);
        List<Object> rest = path.subList(1, path.size());

        if (head instanceof Number) {
            int index = ((Number) head).intValue();

            // jq: Cannot index object with number
            if (value instanceof Map) {
                throw new IllegalArgumentException("Cannot index object with number");
            }

            // jq: Array index too large (limit to prevent memory issues)
            if (index > MAX_ARRAY_INDEX) {
                throw new IllegalArgumentException("Array index too large");
            }

            // jq: Out of bounds negative array index
            if (index < 0) {
                throw new IllegalArgumentException("Out of bounds negative array index");
            }

            List<Object> arr;
            if (value instanceof List) {
                arr = new ArrayList<>((List<Object>) value);
            } else {
                arr = new ArrayList<>();
            }
            while (arr.size() <= index) {
                arr.add(null);
            }
            arr.set(index, setPath(arr.get(index), rest, newVal));
            return arr;
        }

        // jq: Cannot index array with string (path key must be string for objects)
        if (value instanceof List) {
            throw new IllegalArgumentException("Cannot index array with string");
        }

        String headStr = head.toString();

        // Defense against prototype pollution: skip dangerous keys
        if (!isSafeKey(headStr)) {
            if (value instanceof Map) {
                return value;
            }
            return new LinkedHashMap<String, Object>();
        }

        Map<String, Object> obj;
        if (value instanceof Map) {
            obj = new LinkedHashMap<>((Map<String, Object>) value);
        } else {
            obj = new LinkedHashMap<String, Object>();
        }
        Object currentVal = obj.containsKey(headStr) ? obj.get(headStr) : null;
        obj.put(headStr, setPath(currentVal, rest, newVal));
        return obj;
    }

    /**
     * Delete a value at a given path within a query value.
     */
    public static Object deletePath(Object value, List<Object> path) {
        if (path.isEmpty()) {
            return null;
        }

        if (path.size() == 1) {
            Object key = path.get(0);
            if (value instanceof List && key instanceof Number) {
                int index = ((Number) key).intValue();
                List<Object> arr = new ArrayList<>((List<Object>) value);
                if (index >= 0 && index < arr.size()) {
                    arr.remove(index);
                }
                return arr;
            }
            if (value instanceof Map) {
                String strKey = key.toString();
                // Defense against prototype pollution: skip dangerous keys
                if (!isSafeKey(strKey)) {
                    return value;
                }
                Map<String, Object> obj = new LinkedHashMap<>((Map<String, Object>) value);
                obj.remove(strKey);
                return obj;
            }
            return value;
        }

        Object head = path.get(0);
        List<Object> rest = path.subList(1, path.size());

        if (value instanceof List && head instanceof Number) {
            int index = ((Number) head).intValue();
            List<Object> arr = new ArrayList<>((List<Object>) value);
            if (index >= 0 && index < arr.size()) {
                arr.set(index, deletePath(arr.get(index), rest));
            }
            return arr;
        }
        if (value instanceof Map) {
            String strHead = head.toString();
            // Defense against prototype pollution: skip dangerous keys
            if (!isSafeKey(strHead)) {
                return value;
            }
            Map<String, Object> obj = new LinkedHashMap<>((Map<String, Object>) value);
            if (obj.containsKey(strHead)) {
                obj.put(strHead, deletePath(obj.get(strHead), rest));
            }
            return obj;
        }
        return value;
    }

    /**
     * Get a value at a given path within a query value.
     */
    public static Object getPath(Object value, List<Object> path) {
        if (path.isEmpty()) {
            return value;
        }

        Object head = path.get(0);
        List<Object> rest = path.subList(1, path.size());

        if (value instanceof List && head instanceof Number) {
            int index = ((Number) head).intValue();
            List<Object> arr = (List<Object>) value;
            if (index >= 0 && index < arr.size()) {
                return getPath(arr.get(index), rest);
            }
            return null;
        }
        if (value instanceof Map && head instanceof String) {
            Map<String, Object> obj = (Map<String, Object>) value;
            if (obj.containsKey(head)) {
                return getPath(obj.get(head), rest);
            }
            return null;
        }
        return null;
    }
}
