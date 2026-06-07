package com.justbash.commands.queryengine;

import java.util.*;

/**
 * Safe Object Utilities
 *
 * Defense-in-depth against prototype pollution attacks.
 * These utilities prevent malicious data from accessing or modifying
 * the prototype chain via keys like "__proto__", "constructor", etc.
 *
 * <p>In Java, we use {@link LinkedHashMap} (not {@link HashMap}) to preserve
 * insertion order and avoid any prototype chain issues since Java Maps have no
 * prototype chain equivalent.
 */
public final class SafeObject {

    private SafeObject() {
        // utility class
    }

    /**
     * Keys that could be used to access or pollute the prototype chain.
     * These should never be used as direct object property names when
     * setting values from untrusted input.
     */
    private static final Set<String> DANGEROUS_KEYS = Set.of(
            "__proto__", "constructor", "prototype"
    );

    /**
     * Extended list of potentially dangerous keys for extra paranoia.
     * These include Object.prototype methods.
     */
    private static final Set<String> EXTENDED_DANGEROUS_KEYS;

    static {
        Set<String> extended = new LinkedHashSet<>(DANGEROUS_KEYS);
        extended.addAll(Set.of(
                "__defineGetter__",
                "__defineSetter__",
                "__lookupGetter__",
                "__lookupSetter__",
                "hasOwnProperty",
                "isPrototypeOf",
                "propertyIsEnumerable",
                "toLocaleString",
                "toString",
                "valueOf"
        ));
        EXTENDED_DANGEROUS_KEYS = Collections.unmodifiableSet(extended);
    }

    /**
     * Assert that a value is a plain Map (not a List) without prototype chain issues.
     * Catches bugs where unsanitized or wrong-type values leak into safe helpers.
     */
    private static void assertSafeObject(Object obj, String caller) {
        if (obj instanceof List) {
            throw new IllegalArgumentException(caller + ": expected object, got array");
        }
        if (!(obj instanceof Map)) {
            throw new IllegalArgumentException(caller + ": expected null-prototype object, got " +
                    (obj == null ? "null" : obj.getClass().getName()));
        }
    }

    /**
     * Check if a key is safe to use for object property access/assignment.
     * Returns true if the key is safe, false if it could cause prototype pollution.
     */
    public static boolean isSafeKey(String key) {
        return !DANGEROUS_KEYS.contains(key);
    }

    /**
     * Check if a key is safe using the extended dangerous keys list.
     * More paranoid version that blocks additional Object.prototype methods.
     */
    public static boolean isSafeKeyStrict(String key) {
        return !EXTENDED_DANGEROUS_KEYS.contains(key);
    }

    /**
     * Safely get a property from a Map using containsKey check.
     * Returns null if the key is dangerous or doesn't exist as own property.
     */
    @SuppressWarnings("unchecked")
    public static <T> T safeGet(Map<String, T> obj, String key) {
        assertSafeObject(obj, "safeGet");
        if (!isSafeKey(key)) {
            return null;
        }
        if (obj.containsKey(key)) {
            return obj.get(key);
        }
        return null;
    }

    /**
     * Safely set a property on a Map.
     * Silently ignores dangerous keys to prevent prototype pollution.
     */
    public static <T> void safeSet(Map<String, T> obj, String key, T value) {
        assertSafeObject(obj, "safeSet");
        if (isSafeKey(key)) {
            obj.put(key, value);
        }
        // Dangerous keys are silently ignored - this matches jq behavior
        // where __proto__ is treated as a regular key that happens to not work
    }

    /**
     * Safely delete a property from a Map.
     * Ignores dangerous keys.
     */
    public static <T> void safeDelete(Map<String, T> obj, String key) {
        assertSafeObject(obj, "safeDelete");
        if (isSafeKey(key)) {
            obj.remove(key);
        }
    }

    /**
     * Create a safe Map from entries, filtering out dangerous keys.
     */
    public static <T> Map<String, T> safeFromEntries(Iterable<Map.Entry<String, T>> entries) {
        Map<String, T> result = new LinkedHashMap<>();
        for (Map.Entry<String, T> entry : entries) {
            safeSet(result, entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * Safely copy/assign properties from source to target.
     * Only copies entries and filters dangerous keys.
     */
    public static <T> Map<String, T> safeAssign(Map<String, T> target, Map<String, T> source) {
        assertSafeObject(target, "safeAssign target");
        assertSafeObject(source, "safeAssign source");
        for (String key : source.keySet()) {
            safeSet(target, key, source.get(key));
        }
        return target;
    }

    /**
     * Create a shallow copy of a Map, filtering dangerous keys.
     */
    @SuppressWarnings("unchecked")
    public static <T> Map<String, T> safeCopy(Map<String, T> obj) {
        Map<String, T> result = new LinkedHashMap<>();
        for (Map.Entry<String, T> entry : obj.entrySet()) {
            if (isSafeKey(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    /**
     * Check if Map contains key safely.
     */
    public static boolean safeHasOwn(Map<?, ?> obj, String key) {
        assertSafeObject(obj, "safeHasOwn");
        return obj.containsKey(key);
    }

    /**
     * SECURITY: Recursively convert parsed data to safe LinkedHashMap objects.
     * Call this on ALL data from untrusted parsers (JSON.parse, YAML.parse, etc.)
     * to eliminate prototype chain access at the boundary.
     * All keys (including __proto__, constructor) are preserved as own properties —
     * the defense is null-prototype, not key filtering.
     */
    @SuppressWarnings("unchecked")
    public static Object sanitizeParsedData(Object value) {
        Map<Object, Object> seen = new IdentityHashMap<>();
        return sanitize(value, seen);
    }

    @SuppressWarnings("unchecked")
    private static Object sanitize(Object current, Map<Object, Object> seen) {
        if (current == null || !(current instanceof Object)) {
            return current;
        }

        // Preserve Date objects (e.g. TOML datetimes) — they have no own keys
        // and destroying them would break datetime roundtripping.
        if (current instanceof Date) {
            return current;
        }

        Object cached = seen.get(current);
        if (cached != null) {
            return cached;
        }

        if (current instanceof List) {
            List<Object> sanitizedList = new ArrayList<>();
            seen.put(current, sanitizedList);
            for (Object item : (List<?>) current) {
                sanitizedList.add(sanitize(item, seen));
            }
            return sanitizedList;
        }

        if (current instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<>();
            seen.put(current, result);
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) current).entrySet()) {
                String key = entry.getKey() == null ? "null" : entry.getKey().toString();
                result.put(key, sanitize(entry.getValue(), seen));
            }
            return result;
        }

        // For unknown object types, return as-is
        return current;
    }

    /**
     * Type-safe cast from Object to Map for property access.
     * Returns null if the value is not a non-List object.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asQueryRecord(Object value) {
        if (value != null && !(value instanceof List) && value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    /**
     * Create a null-prototype object from a static lookup table literal.
     * In Java, this is just a new LinkedHashMap.
     *
     * <p>Use this to define Map/dictionary constants that are safe from
     * prototype pollution (e.g., {@code __proto__} lookups return {@code null}).
     */
    public static <T> Map<String, T> nullPrototype(Map<String, T> obj) {
        Map<String, T> result = new LinkedHashMap<>();
        result.putAll(obj);
        return result;
    }

    /**
     * Create a null-prototype shallow copy of a Map.
     * This prevents prototype chain lookups without filtering any keys.
     */
    public static <T> Map<String, T> nullPrototypeCopy(Map<String, T> obj) {
        return new LinkedHashMap<>(obj);
    }

    /**
     * Merge multiple Maps into a new null-prototype Map.
     * This prevents prototype chain lookups without filtering any keys.
     * Later maps override earlier ones for duplicate keys.
     */
    @SafeVarargs
    public static <T> Map<String, T> nullPrototypeMerge(Map<String, T>... objs) {
        Map<String, T> result = new LinkedHashMap<>();
        for (Map<String, T> obj : objs) {
            result.putAll(obj);
        }
        return result;
    }
}
