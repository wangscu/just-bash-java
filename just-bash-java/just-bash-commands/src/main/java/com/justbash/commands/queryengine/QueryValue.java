package com.justbash.commands.queryengine;

/**
 * QueryValue represents any value that can be processed by the query engine.
 * This is a type alias for Object in Java, where valid types are:
 * - null
 * - Boolean
 * - Number (Integer, Long, Double)
 * - String
 * - List&lt;QueryValue&gt;
 * - Map&lt;String, QueryValue&gt; (must use null-prototype, i.e. LinkedHashMap)
 */
public final class QueryValue {
    private QueryValue() {}
}
