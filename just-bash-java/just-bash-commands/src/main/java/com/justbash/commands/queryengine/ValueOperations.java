package com.justbash.commands.queryengine;

import java.util.*;

/**
 * Query Value Utilities
 *
 * Utility functions for working with jq/query values.
 */
public final class ValueOperations {

    private ValueOperations() {
        // utility class
    }

    /**
     * Keys that could be used to access or pollute the prototype chain.
     */
    private static final Set<String> DANGEROUS_KEYS = new HashSet<>(Arrays.asList(
        "__proto__", "constructor", "prototype"
    ));

    /**
     * Check if a key is safe to use for object property access/assignment.
     */
    private static boolean isSafeKey(String key) {
        return !DANGEROUS_KEYS.contains(key);
    }

    /**
     * Check if a value is truthy in jq semantics.
     * In jq: false and null are falsy, everything else is truthy.
     */
    public static boolean isTruthy(Object v) {
        return !Boolean.FALSE.equals(v) && v != null;
    }

    /**
     * Deep equality check for query values.
     */
    public static boolean deepEqual(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return ((Number) a).doubleValue() == ((Number) b).doubleValue();
        }
        if (a instanceof List && b instanceof List) {
            List<?> aList = (List<?>) a;
            List<?> bList = (List<?>) b;
            if (aList.size() != bList.size()) return false;
            for (int i = 0; i < aList.size(); i++) {
                if (!deepEqual(aList.get(i), bList.get(i))) return false;
            }
            return true;
        }
        Map<String, Object> aObj = asQueryRecord(a);
        Map<String, Object> bObj = asQueryRecord(b);
        if (aObj != null && bObj != null) {
            if (aObj.size() != bObj.size()) return false;
            for (Map.Entry<String, Object> e : aObj.entrySet()) {
                if (!bObj.containsKey(e.getKey())) return false;
                if (!deepEqual(e.getValue(), bObj.get(e.getKey()))) return false;
            }
            return true;
        }
        return Objects.equals(a, b);
    }

    /**
     * Compare two values for sorting.
     * Returns negative if a &lt; b, positive if a &gt; b, 0 if equal.
     */
    public static int compare(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }
        if (a instanceof String && b instanceof String) {
            return ((String) a).compareTo((String) b);
        }
        return 0;
    }

    /**
     * Deep merge two objects.
     * Values from b override values from a, except nested objects are merged recursively.
     * Filters out dangerous keys (__proto__, constructor, prototype) to prevent prototype pollution.
     * Uses LinkedHashMap to preserve order.
     */
    public static Map<String, Object> deepMerge(Map<String, Object> a, Map<String, Object> b) {
        Map<String, Object> result = new LinkedHashMap<>(a);
        for (Map.Entry<String, Object> entry : b.entrySet()) {
            String key = entry.getKey();
            if (!isSafeKey(key)) {
                continue;
            }
            Map<String, Object> resultRec = asQueryRecord(result.get(key));
            Map<String, Object> bRec = asQueryRecord(entry.getValue());
            if (resultRec != null && bRec != null) {
                result.put(key, deepMerge(resultRec, bRec));
            } else {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    /**
     * Calculate the nesting depth of a value (array or object).
     */
    public static int getValueDepth(Object value) {
        return getValueDepth(value, 3000);
    }

    public static int getValueDepth(Object value, int maxCheck) {
        int depth = 0;
        Object current = value;
        while (depth < maxCheck) {
            if (current instanceof List) {
                List<?> list = (List<?>) current;
                if (list.isEmpty()) {
                    return depth + 1;
                }
                current = list.get(0);
                depth++;
            } else if (current instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) current;
                if (map.isEmpty()) {
                    return depth + 1;
                }
                current = map.values().iterator().next();
                depth++;
            } else {
                return depth;
            }
        }
        return depth;
    }

    /**
     * Compare two values using jq's comparison semantics.
     * jq sorts by type first (null &lt; bool &lt; number &lt; string &lt; array &lt; object),
     * then by value within type.
     */
    public static int compareJq(Object a, Object b) {
        int ta = typeOrder(a);
        int tb = typeOrder(b);
        if (ta != tb) {
            return ta - tb;
        }

        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }
        if (a instanceof String && b instanceof String) {
            return ((String) a).compareTo((String) b);
        }
        if (a instanceof Boolean && b instanceof Boolean) {
            return (Boolean.TRUE.equals(a) ? 1 : 0) - (Boolean.TRUE.equals(b) ? 1 : 0);
        }
        if (a instanceof List && b instanceof List) {
            List<?> aList = (List<?>) a;
            List<?> bList = (List<?>) b;
            int minLen = Math.min(aList.size(), bList.size());
            for (int i = 0; i < minLen; i++) {
                int cmp = compareJq(aList.get(i), bList.get(i));
                if (cmp != 0) {
                    return cmp;
                }
            }
            return aList.size() - bList.size();
        }
        // Objects: compare by sorted keys, then values
        Map<String, Object> aObj = asQueryRecord(a);
        Map<String, Object> bObj = asQueryRecord(b);
        if (aObj != null && bObj != null) {
            List<String> aKeys = new ArrayList<>(aObj.keySet());
            List<String> bKeys = new ArrayList<>(bObj.keySet());
            Collections.sort(aKeys);
            Collections.sort(bKeys);
            int minLen = Math.min(aKeys.size(), bKeys.size());
            for (int i = 0; i < minLen; i++) {
                int keyCmp = aKeys.get(i).compareTo(bKeys.get(i));
                if (keyCmp != 0) {
                    return keyCmp;
                }
            }
            if (aKeys.size() != bKeys.size()) {
                return aKeys.size() - bKeys.size();
            }
            for (String key : aKeys) {
                int cmp = compareJq(aObj.get(key), bObj.get(key));
                if (cmp != 0) {
                    return cmp;
                }
            }
        }

        return 0;
    }

    private static int typeOrder(Object v) {
        if (v == null) return 0;
        if (v instanceof Boolean) return 1;
        if (v instanceof Number) return 2;
        if (v instanceof String) return 3;
        if (v instanceof List) return 4;
        if (v instanceof Map) return 5;
        return 6;
    }

    /**
     * Check if value a contains value b using jq's containment semantics.
     */
    public static boolean containsDeep(Object a, Object b) {
        if (deepEqual(a, b)) {
            return true;
        }
        // jq: string contains substring check
        if (a instanceof String && b instanceof String) {
            return ((String) a).contains((String) b);
        }
        if (a instanceof List && b instanceof List) {
            List<?> aList = (List<?>) a;
            List<?> bList = (List<?>) b;
            for (Object bItem : bList) {
                boolean found = false;
                for (Object aItem : aList) {
                    if (containsDeep(aItem, bItem)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return true;
        }
        // Array contains scalar
        if (a instanceof List && !(b instanceof List)) {
            for (Object aItem : (List<?>) a) {
                if (deepEqual(aItem, b)) {
                    return true;
                }
            }
            return false;
        }
        Map<String, Object> aObj = asQueryRecord(a);
        Map<String, Object> bObj = asQueryRecord(b);
        if (aObj != null && bObj != null) {
            for (Map.Entry<String, Object> entry : bObj.entrySet()) {
                String k = entry.getKey();
                if (!aObj.containsKey(k) || !containsDeep(aObj.get(k), entry.getValue())) {
                    return false;
                }
            }
            return true;
        }
        // Object contains scalar
        if (aObj != null && bObj == null) {
            for (Object v : aObj.values()) {
                if (deepEqual(v, b)) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    /**
     * Recursively normalize Long values to Double for jq compatibility.
     */
    @SuppressWarnings("unchecked")
    public static Object normalizeNumbers(Object value) {
        if (value instanceof Long l) return l.doubleValue();
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) result.add(normalizeNumbers(item));
            return result;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                result.put(e.getKey().toString(), normalizeNumbers(e.getValue()));
            }
            return result;
        }
        return value;
    }

    /**
     * Type-safe cast from Object to Map for property access.
     * Returns null if the value is not a non-list Map.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asQueryRecord(Object value) {
        if (value instanceof Map && !(value instanceof List)) {
            return (Map<String, Object>) value;
        }
        return null;
    }
}
