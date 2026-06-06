package com.justbash.network;

import java.util.Map;

/**
 * Result of a network fetch operation.
 *
 * @param status     HTTP status code
 * @param statusText HTTP status text
 * @param headers    Response headers (key is lower-cased header name)
 * @param body       Raw response bytes (never decoded as UTF-8 text)
 * @param url        Final URL after redirects
 */
public record FetchResult(
    int status,
    String statusText,
    Map<String, String> headers,
    byte[] body,
    String url
) {}
