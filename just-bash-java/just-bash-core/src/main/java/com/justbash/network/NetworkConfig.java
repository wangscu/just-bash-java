package com.justbash.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Header transform applied at the fetch boundary.
 * Headers specified here override any user-supplied headers with the same name.
 */
record RequestTransform(Map<String, String> headers) {}

/**
 * An allowed URL entry with optional header transforms.
 * Transforms are applied at the fetch boundary so secrets never enter the sandbox.
 */
record AllowedUrl(String url, List<RequestTransform> transform) {
    public AllowedUrl(String url) {
        this(url, Collections.emptyList());
    }
}

/**
 * An entry in the allowed URL list: either a plain URL string or
 * an object with a URL and optional transforms.
 */
sealed interface AllowedUrlEntry {
    record StringEntry(String url) implements AllowedUrlEntry {}
    record ObjectEntry(AllowedUrl allowedUrl) implements AllowedUrlEntry {}
}

/**
 * Configuration for network access.
 *
 * Network access is disabled by default. To enable network access (e.g., for curl),
 * you must explicitly configure allowed URLs.
 */
public class NetworkConfig {
    private final List<AllowedUrlEntry> allowedUrlPrefixes;
    private final List<String> allowedMethods;
    private final boolean dangerouslyAllowFullInternetAccess;
    private final int maxRedirects;
    private final int timeoutMs;
    private final long maxResponseSize;
    private final boolean denyPrivateRanges;
    private final Function<String, java.util.concurrent.CompletableFuture<List<DnsLookupResult>>> dnsResolve;

    public static final int DEFAULT_MAX_REDIRECTS = 20;
    public static final int DEFAULT_TIMEOUT_MS = 30000;
    public static final long DEFAULT_MAX_RESPONSE_SIZE = 10 * 1024 * 1024; // 10MB
    public static final List<String> DEFAULT_ALLOWED_METHODS = List.of("GET", "HEAD");
    public static final List<String> ALL_METHODS = List.of("GET", "HEAD", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");

    /**
     * DNS lookup result used for private IP resolution checks.
     */
    public record DnsLookupResult(String address, int family) {}

    private NetworkConfig(Builder builder) {
        this.allowedUrlPrefixes = List.copyOf(builder.allowedUrlPrefixes);
        this.allowedMethods = builder.allowedMethods != null ? List.copyOf(builder.allowedMethods) : DEFAULT_ALLOWED_METHODS;
        this.dangerouslyAllowFullInternetAccess = builder.dangerouslyAllowFullInternetAccess;
        this.maxRedirects = builder.maxRedirects;
        this.timeoutMs = builder.timeoutMs;
        this.maxResponseSize = builder.maxResponseSize;
        this.denyPrivateRanges = builder.denyPrivateRanges;
        this.dnsResolve = builder.dnsResolve;
    }

    public List<AllowedUrlEntry> allowedUrlPrefixes() { return allowedUrlPrefixes; }
    public List<String> allowedMethods() { return allowedMethods; }
    public boolean dangerouslyAllowFullInternetAccess() { return dangerouslyAllowFullInternetAccess; }
    public int maxRedirects() { return maxRedirects; }
    public int timeoutMs() { return timeoutMs; }
    public long maxResponseSize() { return maxResponseSize; }
    public boolean denyPrivateRanges() { return denyPrivateRanges; }
    public Function<String, java.util.concurrent.CompletableFuture<List<DnsLookupResult>>> dnsResolve() { return dnsResolve; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<AllowedUrlEntry> allowedUrlPrefixes = new ArrayList<>();
        private List<String> allowedMethods;
        private boolean dangerouslyAllowFullInternetAccess = false;
        private int maxRedirects = DEFAULT_MAX_REDIRECTS;
        private int timeoutMs = DEFAULT_TIMEOUT_MS;
        private long maxResponseSize = DEFAULT_MAX_RESPONSE_SIZE;
        private boolean denyPrivateRanges = false;
        private Function<String, java.util.concurrent.CompletableFuture<List<DnsLookupResult>>> dnsResolve;

        public Builder addAllowedUrlPrefix(String url) {
            this.allowedUrlPrefixes.add(new AllowedUrlEntry.StringEntry(url));
            return this;
        }

        public Builder addAllowedUrlPrefix(String url, List<RequestTransform> transforms) {
            this.allowedUrlPrefixes.add(new AllowedUrlEntry.ObjectEntry(new AllowedUrl(url, transforms)));
            return this;
        }

        public Builder allowedMethods(List<String> methods) {
            this.allowedMethods = methods != null ? new ArrayList<>(methods) : null;
            return this;
        }

        public Builder addAllowedMethod(String method) {
            if (this.allowedMethods == null) {
                this.allowedMethods = new ArrayList<>();
            }
            this.allowedMethods.add(method);
            return this;
        }

        public Builder dangerouslyAllowFullInternetAccess(boolean allow) {
            this.dangerouslyAllowFullInternetAccess = allow;
            return this;
        }

        public Builder maxRedirects(int maxRedirects) {
            this.maxRedirects = maxRedirects;
            return this;
        }

        public Builder timeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public Builder maxResponseSize(long maxResponseSize) {
            this.maxResponseSize = maxResponseSize;
            return this;
        }

        public Builder denyPrivateRanges(boolean deny) {
            this.denyPrivateRanges = deny;
            return this;
        }

        public Builder dnsResolve(Function<String, java.util.concurrent.CompletableFuture<List<DnsLookupResult>>> resolver) {
            this.dnsResolve = resolver;
            return this;
        }

        public NetworkConfig build() {
            return new NetworkConfig(this);
        }
    }
}
