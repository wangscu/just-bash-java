package com.justbash.network;

/**
 * Base exception for network security errors.
 */
public class NetworkException extends RuntimeException {
    public NetworkException(String message) {
        super(message);
    }
}

/**
 * Thrown when a URL is not in the allow-list or is otherwise blocked.
 */
class NetworkAccessDeniedError extends NetworkException {
    public NetworkAccessDeniedError(String url) {
        super("Network access denied: URL not in allow-list: " + url);
    }

    public NetworkAccessDeniedError(String url, String reason) {
        super("Network access denied: " + reason + ": " + url);
    }
}

/**
 * Thrown when too many redirects occur.
 */
class TooManyRedirectsError extends NetworkException {
    public TooManyRedirectsError(int maxRedirects) {
        super("Too many redirects (max: " + maxRedirects + ")");
    }
}

/**
 * Thrown when a redirect target is not in the allow-list.
 */
class RedirectNotAllowedError extends NetworkException {
    public RedirectNotAllowedError(String url) {
        super("Redirect target not in allow-list: " + url);
    }
}

/**
 * Thrown when an HTTP method is not allowed.
 */
class MethodNotAllowedError extends NetworkException {
    public MethodNotAllowedError(String method, java.util.List<String> allowedMethods) {
        super("HTTP method '" + method + "' not allowed. Allowed methods: " + String.join(", ", allowedMethods));
    }
}

/**
 * Thrown when a response body exceeds the maximum allowed size.
 */
class ResponseTooLargeError extends NetworkException {
    public ResponseTooLargeError(long maxSize) {
        super("Response body too large (max: " + maxSize + " bytes)");
    }
}
