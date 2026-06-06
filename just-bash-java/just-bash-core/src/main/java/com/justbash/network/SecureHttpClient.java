package com.justbash.network;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.Function;

/**
 * Secure HTTP client that enforces URL allow-lists, method restrictions,
 * private IP blocking, DNS pinning, redirect validation, and response size limits.
 */
public class SecureHttpClient {

    private static final Set<String> BODYLESS_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Set<Integer> REDIRECT_CODES = Set.of(301, 302, 303, 307, 308);

    private final NetworkConfig config;
    private final HttpClient httpClient;
    private final List<AllowedUrlEntry> entries;
    private final List<AllowedUrl> transformEntries;
    private final int maxRedirects;
    private final int timeoutMs;
    private final long maxResponseSize;
    private final List<String> allowedMethods;
    private final boolean denyPrivateRanges;
    private final Function<String, CompletableFuture<List<NetworkConfig.DnsLookupResult>>> dnsResolve;

    public static SecureHttpClient create(NetworkConfig config) {
        return new SecureHttpClient(config);
    }

    private SecureHttpClient(NetworkConfig config) {
        this.config = config;
        this.entries = config.allowedUrlPrefixes();
        this.maxRedirects = config.maxRedirects();
        this.timeoutMs = config.timeoutMs();
        this.maxResponseSize = config.maxResponseSize();
        this.denyPrivateRanges = config.denyPrivateRanges();
        this.dnsResolve = config.dnsResolve() != null ? config.dnsResolve() : this::defaultDnsLookup;

        if (!config.dangerouslyAllowFullInternetAccess()) {
            List<String> errors = UrlAllowList.validateAllowList(entries);
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException("Invalid network allow-list:\n" + String.join("\n", errors));
            }
        }

        // Collect entries that carry transforms for firewall header injection
        this.transformEntries = new ArrayList<>();
        for (AllowedUrlEntry entry : entries) {
            if (entry instanceof AllowedUrlEntry.ObjectEntry oe) {
                AllowedUrl au = oe.allowedUrl();
                if (au.transform() != null && !au.transform().isEmpty()) {
                    transformEntries.add(au);
                }
            }
        }

        this.allowedMethods = config.dangerouslyAllowFullInternetAccess()
            ? NetworkConfig.ALL_METHODS
            : config.allowedMethods();

        this.httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofMillis(timeoutMs))
            .build();
    }

    /**
     * Fetches a URL with full security enforcement.
     */
    public CompletableFuture<FetchResult> fetch(String url, SecureFetchOptions options) {
        String method = options.method().orElse("GET").toUpperCase();

        return checkAllowed(url)
            .thenCompose(pinned -> {
                try {
                    checkMethodAllowed(method);
                    return doFetch(url, method, options, pinned);
                } catch (NetworkException e) {
                    return CompletableFuture.failedFuture(e);
                }
            });
    }

    private CompletableFuture<FetchResult> doFetch(String url, String method, SecureFetchOptions options, PinnedAddress pinned) {
        String currentUrl = url;
        PinnedAddress currentPinned = pinned;
        int redirectCount = 0;
        boolean followRedirects = options.followRedirects();

        // Effective timeout: per-request override capped at global timeout
        int effectiveTimeout = options.timeoutMs()
            .map(t -> Math.min(t, timeoutMs))
            .orElse(timeoutMs);

        return fetchLoop(currentUrl, method, options, currentPinned, redirectCount, followRedirects, effectiveTimeout);
    }

    private CompletableFuture<FetchResult> fetchLoop(String currentUrl, String method, SecureFetchOptions options,
                                                      PinnedAddress pinned, int redirectCount,
                                                      boolean followRedirects, int effectiveTimeout) {
        HttpRequest.Builder requestBuilder = buildRequest(currentUrl, method, options, pinned, effectiveTimeout);
        HttpRequest request = requestBuilder.build();

        return httpClient.sendAsync(request, responseInfo -> {
                if (maxResponseSize <= 0) {
                    return BodySubscribers.ofByteArray();
                }
                Optional<String> contentLengthOpt = responseInfo.headers().firstValue("content-length");
                if (contentLengthOpt.isPresent()) {
                    try {
                        long size = Long.parseLong(contentLengthOpt.get());
                        if (size > maxResponseSize) {
                            throw new ResponseTooLargeError(maxResponseSize);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
                return new SizeLimitedBodySubscriber(maxResponseSize);
            })
            .thenCompose(response -> {
                if (REDIRECT_CODES.contains(response.statusCode()) && followRedirects) {
                    Optional<String> locationOpt = response.headers().firstValue("location");
                    if (locationOpt.isEmpty()) {
                        return responseToResult(response, currentUrl);
                    }

                    String redirectUrl = resolveUrl(locationOpt.get(), currentUrl);

                    return checkAllowed(redirectUrl)
                        .thenCompose(newPinned -> {
                            try {
                                checkMethodAllowed(method);
                            } catch (NetworkException e) {
                                return CompletableFuture.<FetchResult>failedFuture(new RedirectNotAllowedError(redirectUrl));
                            }

                            int newCount = redirectCount + 1;
                            if (newCount > maxRedirects) {
                                return CompletableFuture.<FetchResult>failedFuture(new TooManyRedirectsError(maxRedirects));
                            }

                            return fetchLoop(redirectUrl, method, options, newPinned, newCount, followRedirects, effectiveTimeout);
                        })
                        .exceptionally(ex -> {
                            Throwable cause = (ex instanceof CompletionException ce) ? ce.getCause() : ex;
                            if (cause instanceof NetworkAccessDeniedError) {
                                throw new RedirectNotAllowedError(redirectUrl);
                            }
                            if (cause instanceof RuntimeException re) {
                                throw re;
                            }
                            throw new RuntimeException(cause);
                        });
                }
                return responseToResult(response, currentUrl);
            });
    }

    private HttpRequest.Builder buildRequest(String url, String method, SecureFetchOptions options,
                                              PinnedAddress pinned, int effectiveTimeout) {
        try {
            URI uri = new URI(url);
            URI requestUri;
            String originalHost = uri.getHost();

            if (pinned != null) {
                // Replace hostname with pinned IP, keep original Host header
                String scheme = uri.getScheme();
                int port = uri.getPort();
                String path = uri.getRawPath() != null ? uri.getRawPath() : "/";
                String query = uri.getRawQuery();
                if (query != null) {
                    path = path + "?" + query;
                }
                if (port == -1) {
                    requestUri = new URI(scheme, null, pinned.address(), -1, path, null, null);
                } else {
                    requestUri = new URI(scheme, null, pinned.address(), port, path, null, null);
                }
            } else {
                requestUri = uri;
            }

            HttpRequest.Builder builder = HttpRequest.newBuilder(requestUri)
                .timeout(Duration.ofMillis(effectiveTimeout));

            // Set method
            switch (method) {
                case "GET" -> builder.GET();
                case "DELETE" -> builder.DELETE();
                default -> {
                    String body = (options.body().isPresent() && !BODYLESS_METHODS.contains(method))
                        ? options.body().get()
                        : "";
                    builder.method(method, HttpRequest.BodyPublishers.ofString(body));
                }
            }

            // Merge headers: user headers first, then firewall overrides
            Map<String, String> mergedHeaders = new HashMap<>();
            if (options.headers().isPresent()) {
                mergedHeaders.putAll(options.headers().get());
            }

            // Firewall headers override user headers
            Map<String, String> firewallHeaders = getFirewallHeaders(url);
            if (firewallHeaders != null) {
                mergedHeaders.putAll(firewallHeaders);
            }

            // Set Host header for pinned connections
            if (pinned != null && originalHost != null) {
                mergedHeaders.put("Host", originalHost);
            }

            for (Map.Entry<String, String> entry : mergedHeaders.entrySet()) {
                builder.setHeader(entry.getKey(), entry.getValue());
            }

            return builder;
        } catch (URISyntaxException e) {
            throw new NetworkAccessDeniedError(url, "invalid URL syntax");
        }
    }

    private Map<String, String> getFirewallHeaders(String url) {
        if (transformEntries.isEmpty()) {
            return null;
        }
        Map<String, String> merged = null;
        for (AllowedUrl entry : transformEntries) {
            if (UrlAllowList.matchesAllowListEntry(url, entry.url()) && entry.transform() != null) {
                if (merged == null) {
                    merged = new HashMap<>();
                }
                for (RequestTransform t : entry.transform()) {
                    if (t.headers() != null) {
                        merged.putAll(t.headers());
                    }
                }
            }
        }
        return merged;
    }

    private CompletableFuture<PinnedAddress> checkAllowed(String url) {
        if (!config.dangerouslyAllowFullInternetAccess() && !UrlAllowList.isUrlAllowed(url, entries)) {
            return CompletableFuture.failedFuture(new NetworkAccessDeniedError(url));
        }

        if (!denyPrivateRanges) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            URI parsed = new URI(url);
            String hostname = parsed.getHost();
            if (hostname == null) {
                return CompletableFuture.completedFuture(null);
            }

            // Lexical check (fast path)
            if (UrlAllowList.isPrivateIp(hostname)) {
                return CompletableFuture.failedFuture(
                    new NetworkAccessDeniedError(url, "private/loopback IP address blocked"));
            }

            // Skip DNS check for IP literals
            boolean isDomainName = hostname.matches(".*[a-zA-Z].*");
            if (!isDomainName) {
                return CompletableFuture.completedFuture(null);
            }

            return dnsResolve.apply(hostname)
                .thenApply(addresses -> {
                    if (addresses == null || addresses.isEmpty()) {
                        return null;
                    }
                    // Check all addresses for private IPs
                    for (NetworkConfig.DnsLookupResult addr : addresses) {
                        if (UrlAllowList.isPrivateIp(addr.address())) {
                            throw new NetworkAccessDeniedError(url,
                                "hostname resolves to private/loopback IP address");
                        }
                    }
                    // Return first public address for pinning
                    NetworkConfig.DnsLookupResult first = addresses.get(0);
                    return new PinnedAddress(hostname, first.address(), first.family() == 6 ? 6 : 4);
                })
                .exceptionally(ex -> {
                    Throwable cause = (ex instanceof CompletionException ce) ? ce.getCause() : ex;
                    if (cause instanceof NetworkAccessDeniedError) {
                        throw (NetworkAccessDeniedError) cause;
                    }
                    // DNS resolution failure: fail closed if it's not a "not found" type error
                    // In Java, we can't easily distinguish ENOTFOUND vs other errors
                    // so we fail closed (block) on any DNS error
                    throw new NetworkAccessDeniedError(url, "DNS resolution failed for private IP check");
                });
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(new NetworkAccessDeniedError(url, "invalid URL"));
        }
    }

    private void checkMethodAllowed(String method) {
        if (config.dangerouslyAllowFullInternetAccess()) {
            return;
        }
        String upperMethod = method.toUpperCase();
        if (!allowedMethods.contains(upperMethod)) {
            throw new MethodNotAllowedError(upperMethod, allowedMethods);
        }
    }

    private CompletableFuture<FetchResult> responseToResult(HttpResponse<byte[]> response, String url) {
        Map<String, String> headers = new HashMap<>();
        response.headers().map().forEach((key, values) -> {
            if (!values.isEmpty()) {
                headers.put(key.toLowerCase(), values.getFirst());
            }
        });

        FetchResult result = new FetchResult(
            response.statusCode(),
            getStatusText(response.statusCode()),
            headers,
            response.body(),
            url
        );
        return CompletableFuture.completedFuture(result);
    }

    private String getStatusText(int statusCode) {
        return switch (statusCode) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 303 -> "See Other";
            case 304 -> "Not Modified";
            case 307 -> "Temporary Redirect";
            case 308 -> "Permanent Redirect";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            default -> "";
        };
    }

    private String resolveUrl(String location, String baseUrl) {
        try {
            URI base = new URI(baseUrl);
            URI resolved = base.resolve(location);
            return resolved.toString();
        } catch (URISyntaxException e) {
            return location;
        }
    }

    private CompletableFuture<List<NetworkConfig.DnsLookupResult>> defaultDnsLookup(String hostname) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                InetAddress[] addresses = InetAddress.getAllByName(hostname);
                List<NetworkConfig.DnsLookupResult> results = new ArrayList<>();
                for (InetAddress addr : addresses) {
                    int family = addr instanceof java.net.Inet6Address ? 6 : 4;
                    results.add(new NetworkConfig.DnsLookupResult(addr.getHostAddress(), family));
                }
                return results;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });
    }

    /**
     * DNS-pinned address for a hostname.
     */
    private record PinnedAddress(String hostname, String address, int family) {}

    /**
     * Body subscriber that accumulates chunks and throws if size limit exceeded.
     */
    private static class SizeLimitedBodySubscriber implements BodySubscriber<byte[]> {
        private final long maxSize;
        private final List<byte[]> chunks = new ArrayList<>();
        private long totalSize = 0;
        private volatile Flow.Subscription subscription;
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();

        SizeLimitedBodySubscriber(long maxSize) {
            this.maxSize = maxSize;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                int remaining = buffer.remaining();
                if (remaining > 0) {
                    totalSize += remaining;
                    if (totalSize > maxSize) {
                        subscription.cancel();
                        result.completeExceptionally(new ResponseTooLargeError(maxSize));
                        return;
                    }
                    byte[] bytes = new byte[remaining];
                    buffer.get(bytes);
                    chunks.add(bytes);
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            byte[] body = new byte[(int) totalSize];
            int offset = 0;
            for (byte[] chunk : chunks) {
                System.arraycopy(chunk, 0, body, offset, chunk.length);
                offset += chunk.length;
            }
            result.complete(body);
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }
    }
}
