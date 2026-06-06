package com.justbash.network;

import java.util.Map;
import java.util.Optional;

/**
 * Options for a secure fetch request.
 *
 * @param method          HTTP method (default: GET)
 * @param headers         User-provided headers
 * @param body            Request body
 * @param followRedirects Whether to follow redirects (default: true)
 * @param timeoutMs       Per-request timeout override (capped at global timeout)
 */
public record SecureFetchOptions(
    Optional<String> method,
    Optional<Map<String, String>> headers,
    Optional<String> body,
    boolean followRedirects,
    Optional<Integer> timeoutMs
) {
    public SecureFetchOptions() {
        this(Optional.of("GET"), Optional.empty(), Optional.empty(), true, Optional.empty());
    }

    public static SecureFetchOptions defaults() {
        return new SecureFetchOptions();
    }
}
