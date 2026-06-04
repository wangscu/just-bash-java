package com.justbash;

import java.util.Map;

public record TraceEvent(
    String category,
    String name,
    long durationMs,
    Map<String, Object> details
) {}
