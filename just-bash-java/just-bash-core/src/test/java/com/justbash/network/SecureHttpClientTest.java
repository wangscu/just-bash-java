package com.justbash.network;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

public class SecureHttpClientTest {

    private static void assertThrowsNetwork(Class<? extends NetworkException> expectedType, Runnable runnable) {
        CompletionException thrown = assertThrows(CompletionException.class, runnable::run);
        assertInstanceOf(expectedType, thrown.getCause());
    }

    // ------------------------------------------------------------------
    // Construction / allow-list validation
    // ------------------------------------------------------------------

    @Test
    void testInvalidAllowListThrows() {
        NetworkConfig config = NetworkConfig.builder()
            .addAllowedUrlPrefix("not-a-url")
            .build();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> SecureHttpClient.create(config));
        assertTrue(thrown.getMessage().contains("Invalid network allow-list"));
    }

    @Test
    void testInvalidProtocolThrows() {
        NetworkConfig config = NetworkConfig.builder()
            .addAllowedUrlPrefix("ftp://example.com")
            .build();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> SecureHttpClient.create(config));
        assertTrue(thrown.getMessage().contains("Only http and https"));
    }

    @Test
    void testValidAllowListConstructs() {
        NetworkConfig config = NetworkConfig.builder()
            .addAllowedUrlPrefix("https://example.com")
            .build();

        assertDoesNotThrow(() -> SecureHttpClient.create(config));
    }

    @Test
    void testEmptyAllowListConstructsIfDangerousAccess() {
        NetworkConfig config = NetworkConfig.builder()
            .dangerouslyAllowFullInternetAccess(true)
            .build();

        assertDoesNotThrow(() -> SecureHttpClient.create(config));
    }

    // ------------------------------------------------------------------
    // URL allow-list enforcement
    // ------------------------------------------------------------------

    @Test
    void testBlockedUrlDenied() {
        NetworkConfig config = NetworkConfig.builder()
            .addAllowedUrlPrefix("https://example.com")
            .build();
        SecureHttpClient client = SecureHttpClient.create(config);

        assertThrowsNetwork(NetworkAccessDeniedError.class, () ->
            client.fetch("https://evil.com", SecureFetchOptions.defaults()).join()
        );
    }

    @Test
    void testAllowedUrlNotDeniedBeforeFetch() {
        // This test verifies that an allowed URL does not throw at the checkAllowed stage.
        NetworkConfig config = NetworkConfig.builder()
            .addAllowedUrlPrefix("https://example.com")
            .build();
        SecureHttpClient client = SecureHttpClient.create(config);

        try {
            client.fetch("https://example.com/path", SecureFetchOptions.defaults()).join();
        } catch (Exception e) {
            Throwable cause = e instanceof CompletionException ce ? ce.getCause() : e;
            assertFalse(cause instanceof NetworkAccessDeniedError,
                "Should not throw NetworkAccessDeniedError for allowed URL: " + cause);
        }
    }

    @Test
    void testDangerouslyAllowFullInternetAccess() {
        NetworkConfig config = NetworkConfig.builder()
            .dangerouslyAllowFullInternetAccess(true)
            .build();
        SecureHttpClient client = SecureHttpClient.create(config);

        // Should not throw NetworkAccessDeniedError for any URL
        Exception thrown = assertThrows(Exception.class, () ->
            client.fetch("https://any-random-site.com", SecureFetchOptions.defaults()).join()
        );
        Throwable cause = thrown instanceof CompletionException ce ? ce.getCause() : thrown;
        assertFalse(cause instanceof NetworkAccessDeniedError);
    }

    // ------------------------------------------------------------------
    // Method restriction
    // ------------------------------------------------------------------

    @Test
    void testBlockedMethodDenied() {
        NetworkConfig config = NetworkConfig.builder()
            .addAllowedUrlPrefix("https://example.com")
            .allowedMethods(List.of("GET"))
            .build();
        SecureHttpClient client = SecureHttpClient.create(config);

        assertThrowsNetwork(MethodNotAllowedError.class, () ->
            client.fetch("https://example.com/path",
                new SecureFetchOptions(Optional.of("POST"), Optional.empty(), Optional.empty(), true, Optional.empty())).join()
        );
    }

    @Test
    void testAllowedMethodNotDenied() {
        NetworkConfig config = NetworkConfig.builder()
            .addAllowedUrlPrefix("https://example.com")
            .allowedMethods(List.of("GET", "POST"))
            .build();
        SecureHttpClient client = SecureHttpClient.create(config);

        // Should not throw MethodNotAllowedError
        try {
            client.fetch("https://example.com/path",
                new SecureFetchOptions(Optional.of("POST"), Optional.empty(), Optional.empty(), true, Optional.empty())).join();
        } catch (Exception e) {
            Throwable cause = e instanceof CompletionException ce ? ce.getCause() : e;
            assertFalse(cause instanceof MethodNotAllowedError,
                "Should not throw MethodNotAllowedError for allowed method: " + cause);
        }
    }

    @Test
    void testDangerouslyAllowAllMethods() {
        NetworkConfig config = NetworkConfig.builder()
            .dangerouslyAllowFullInternetAccess(true)
            .build();
        SecureHttpClient client = SecureHttpClient.create(config);

        Exception thrown = assertThrows(Exception.class, () ->
            client.fetch("https://example.com/path",
                new SecureFetchOptions(Optional.of("DELETE"), Optional.empty(), Optional.empty(), true, Optional.empty())).join()
        );
        Throwable cause = thrown instanceof CompletionException ce ? ce.getCause() : thrown;
        assertFalse(cause instanceof MethodNotAllowedError);
    }

    // ------------------------------------------------------------------
    // Private IP blocking (lexical)
    // ------------------------------------------------------------------

    @Test
    void testPrivateIpBlockedLexical() {
        NetworkConfig config = NetworkConfig.builder()
            .addAllowedUrlPrefix("https://127.0.0.1")
            .denyPrivateRanges(true)
            .build();
        SecureHttpClient client = SecureHttpClient.create(config);

        assertThrowsNetwork(NetworkAccessDeniedError.class, () ->
            client.fetch("https://127.0.0.1/path", SecureFetchOptions.defaults()).join()
        );
    }

    @Test
    void testLocalhostBlocked() {
        NetworkConfig config = NetworkConfig.builder()
            .addAllowedUrlPrefix("https://localhost")
            .denyPrivateRanges(true)
            .build();
        SecureHttpClient client = SecureHttpClient.create(config);

        assertThrowsNetwork(NetworkAccessDeniedError.class, () ->
            client.fetch("https://localhost/path", SecureFetchOptions.defaults()).join()
        );
    }

    @Test
    void testPrivateIpNotBlockedWhenDisabled() {
        NetworkConfig config = NetworkConfig.builder()
            .addAllowedUrlPrefix("https://127.0.0.1")
            .denyPrivateRanges(false)
            .build();
        SecureHttpClient client = SecureHttpClient.create(config);

        // Should not throw NetworkAccessDeniedError for private IP
        Exception thrown = assertThrows(Exception.class, () ->
            client.fetch("https://127.0.0.1/path", SecureFetchOptions.defaults()).join()
        );
        Throwable cause = thrown instanceof CompletionException ce ? ce.getCause() : thrown;
        assertFalse(cause instanceof NetworkAccessDeniedError);
    }

    // ------------------------------------------------------------------
    // Private IP blocking (DNS mock)
    // ------------------------------------------------------------------

    @Test
    void testDnsResolvesToPrivateIpBlocked() {
        NetworkConfig config = NetworkConfig.builder()
            .addAllowedUrlPrefix("https://internal.example.com")
            .denyPrivateRanges(true)
            .dnsResolve(hostname -> CompletableFuture.completedFuture(List.of(
                new NetworkConfig.DnsLookupResult("10.0.0.1", 4)
            )))
            .build();
        SecureHttpClient client = SecureHttpClient.create(config);

        assertThrowsNetwork(NetworkAccessDeniedError.class, () ->
            client.fetch("https://internal.example.com/path", SecureFetchOptions.defaults()).join()
        );
    }

    @Test
    void testDnsResolvesToPublicIpAllowed() {
        NetworkConfig config = NetworkConfig.builder()
            .addAllowedUrlPrefix("https://example.com")
            .denyPrivateRanges(true)
            .dnsResolve(hostname -> CompletableFuture.completedFuture(List.of(
                new NetworkConfig.DnsLookupResult("8.8.8.8", 4)
            )))
            .build();
        SecureHttpClient client = SecureHttpClient.create(config);

        // Should not throw NetworkAccessDeniedError
        Exception thrown = assertThrows(Exception.class, () ->
            client.fetch("https://example.com/path", SecureFetchOptions.defaults()).join()
        );
        Throwable cause = thrown instanceof CompletionException ce ? ce.getCause() : thrown;
        assertFalse(cause instanceof NetworkAccessDeniedError);
    }

    @Test
    void testDnsFailureBlocked() {
        NetworkConfig config = NetworkConfig.builder()
            .addAllowedUrlPrefix("https://example.com")
            .denyPrivateRanges(true)
            .dnsResolve(hostname -> CompletableFuture.failedFuture(new RuntimeException("DNS failure")))
            .build();
        SecureHttpClient client = SecureHttpClient.create(config);

        assertThrowsNetwork(NetworkAccessDeniedError.class, () ->
            client.fetch("https://example.com/path", SecureFetchOptions.defaults()).join()
        );
    }

    // ------------------------------------------------------------------
    // Private IP blocking still enforced with dangerouslyAllowFullInternetAccess
    // ------------------------------------------------------------------

    @Test
    void testPrivateIpStillBlockedWithFullAccess() {
        NetworkConfig config = NetworkConfig.builder()
            .dangerouslyAllowFullInternetAccess(true)
            .denyPrivateRanges(true)
            .build();
        SecureHttpClient client = SecureHttpClient.create(config);

        assertThrowsNetwork(NetworkAccessDeniedError.class, () ->
            client.fetch("https://127.0.0.1/path", SecureFetchOptions.defaults()).join()
        );
    }

    // ------------------------------------------------------------------
    // Header transforms
    // ------------------------------------------------------------------

    @Test
    void testHeaderTransformEntryMatches() {
        // Verify that transform entries are collected correctly
        NetworkConfig config = NetworkConfig.builder()
            .addAllowedUrlPrefix("https://api.example.com", List.of(
                new RequestTransform(Map.of("Authorization", "Bearer secret"))
            ))
            .build();

        assertDoesNotThrow(() -> SecureHttpClient.create(config));
    }

    // ------------------------------------------------------------------
    // Response size limit
    // ------------------------------------------------------------------

    @Test
    void testResponseTooLargeErrorMessage() {
        ResponseTooLargeError error = new ResponseTooLargeError(100);
        assertTrue(error.getMessage().contains("100 bytes"));
    }

    // ------------------------------------------------------------------
    // Error messages
    // ------------------------------------------------------------------

    @Test
    void testNetworkAccessDeniedErrorMessages() {
        NetworkAccessDeniedError e1 = new NetworkAccessDeniedError("https://evil.com");
        assertTrue(e1.getMessage().contains("URL not in allow-list"));

        NetworkAccessDeniedError e2 = new NetworkAccessDeniedError("https://evil.com", "custom reason");
        assertTrue(e2.getMessage().contains("custom reason"));
    }

    @Test
    void testMethodNotAllowedErrorMessage() {
        MethodNotAllowedError error = new MethodNotAllowedError("POST", List.of("GET", "HEAD"));
        assertTrue(error.getMessage().contains("POST"));
        assertTrue(error.getMessage().contains("GET"));
    }

    @Test
    void testRedirectNotAllowedErrorMessage() {
        RedirectNotAllowedError error = new RedirectNotAllowedError("https://evil.com");
        assertTrue(error.getMessage().contains("evil.com"));
    }

    @Test
    void testTooManyRedirectsErrorMessage() {
        TooManyRedirectsError error = new TooManyRedirectsError(5);
        assertTrue(error.getMessage().contains("5"));
    }
}
