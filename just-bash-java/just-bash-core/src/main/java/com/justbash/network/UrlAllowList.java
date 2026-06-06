package com.justbash.network;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * URL allow-list matching and private IP detection.
 *
 * This module provides URL allow-list matching that is enforced at the fetch layer,
 * independent of any parsing or user input manipulation.
 */
public final class UrlAllowList {

    private UrlAllowList() {}

    /**
     * Parsed URL components.
     */
    public record ParsedUrl(String origin, String pathname, String href) {}

    /**
     * Normalized allow-list entry.
     */
    public record NormalizedEntry(String origin, String pathPrefix) {}

    /**
     * Parses a URL string into its components.
     * Returns null if the URL is invalid.
     */
    public static ParsedUrl parseUrl(String urlString) {
        try {
            URI uri = new URI(urlString);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return null;
            }
            int port = uri.getPort();
            String origin;
            if (port == -1) {
                origin = scheme + "://" + host;
            } else {
                origin = scheme + "://" + host + ":" + port;
            }
            String pathname = uri.getRawPath();
            if (pathname == null || pathname.isEmpty()) {
                pathname = "/";
            }
            return new ParsedUrl(origin, pathname, uri.toString());
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /**
     * Normalizes an allow-list entry for consistent matching.
     */
    public static NormalizedEntry normalizeAllowListEntry(String entry) {
        ParsedUrl parsed = parseUrl(entry);
        if (parsed == null) {
            return null;
        }
        return new NormalizedEntry(parsed.origin(), parsed.pathname());
    }

    private static boolean hasAmbiguousPathSeparators(String pathname) {
        if (pathname.contains("\\")) {
            return true;
        }
        String normalized = pathname.toLowerCase();
        return normalized.contains("%2f") || normalized.contains("%5c");
    }

    private static boolean matchesPathPrefix(String pathname, String pathPrefix) {
        if (pathPrefix.equals("/") || pathPrefix.isEmpty()) {
            return true;
        }
        if (pathPrefix.endsWith("/")) {
            return pathname.startsWith(pathPrefix);
        }
        return pathname.equals(pathPrefix) || pathname.startsWith(pathPrefix + "/");
    }

    /**
     * Checks if a URL matches an allow-list entry.
     *
     * Matching rules:
     * 1. Origins must match exactly (case-sensitive for scheme and host)
     * 2. Path-scoped entries match on path segment boundaries, not raw string prefix
     * 3. Ambiguous encoded separators (%2f, %5c) are rejected for path-scoped entries
     * 4. If the allow-list entry has no path (or just "/"), all paths are allowed
     */
    public static boolean matchesAllowListEntry(String url, String allowedEntry) {
        ParsedUrl parsedUrl = parseUrl(url);
        if (parsedUrl == null) {
            return false;
        }

        NormalizedEntry normalizedEntry = normalizeAllowListEntry(allowedEntry);
        if (normalizedEntry == null) {
            return false;
        }

        // Origins must match exactly
        if (!parsedUrl.origin().equals(normalizedEntry.origin())) {
            return false;
        }

        if (!normalizedEntry.pathPrefix().equals("/")
            && !normalizedEntry.pathPrefix().isEmpty()
            && hasAmbiguousPathSeparators(parsedUrl.pathname())) {
            return false;
        }

        return matchesPathPrefix(parsedUrl.pathname(), normalizedEntry.pathPrefix());
    }

    /**
     * Extracts the URL string from an AllowedUrlEntry.
     */
    public static String entryToUrl(AllowedUrlEntry entry) {
        return switch (entry) {
            case AllowedUrlEntry.StringEntry se -> se.url();
            case AllowedUrlEntry.ObjectEntry oe -> oe.allowedUrl().url();
        };
    }

    /**
     * Checks if a URL is allowed by any entry in the allow-list.
     */
    public static boolean isUrlAllowed(String url, List<AllowedUrlEntry> allowedUrlPrefixes) {
        if (allowedUrlPrefixes == null || allowedUrlPrefixes.isEmpty()) {
            return false;
        }
        return allowedUrlPrefixes.stream().anyMatch(entry -> matchesAllowListEntry(url, entryToUrl(entry)));
    }

    // ------------------------------------------------------------------
    // Private IP detection
    // ------------------------------------------------------------------

    /**
     * Check if a hostname is a private/loopback IP address.
     * Only checks the string format — does not perform DNS resolution.
     */
    public static boolean isPrivateIp(String hostname) {
        String normalized = normalizeHostname(hostname);

        // localhost and *.localhost are always local-only hostnames.
        if (normalized.equals("localhost") || normalized.endsWith(".localhost")) {
            return true;
        }

        int[] ipv4 = parseIpv4(normalized);
        if (ipv4 != null) {
            return isPrivateIpv4(ipv4);
        }

        int[] ipv6 = parseIpv6(normalized);
        if (ipv6 != null) {
            return isPrivateIpv6(ipv6);
        }

        return false;
    }

    private static String normalizeHostname(String hostname) {
        String trimmed = hostname.trim().toLowerCase();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static Integer parseIpComponent(String part) {
        if (part == null || part.isEmpty()) {
            return null;
        }

        int base = 10;
        String digits = part;

        if (digits.startsWith("0x") || digits.startsWith("0X")) {
            base = 16;
            digits = digits.substring(2);
        } else if (digits.length() > 1 && digits.startsWith("0")) {
            base = 8;
        }

        if (digits.isEmpty()) {
            return null;
        }

        try {
            if (base == 16 && !digits.matches("^[0-9a-fA-F]+$")) {
                return null;
            }
            if (base == 10 && !digits.matches("^\\d+$")) {
                return null;
            }
            if (base == 8 && !digits.matches("^[0-7]+$")) {
                return null;
            }

            long value = Long.parseLong(digits, base);
            if (value < 0 || value > 0xffffffffL) {
                return null;
            }
            return (int) value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static int[] parseIpv4(String hostname) {
        String[] parts = hostname.split("\\.");
        if (parts.length == 0 || parts.length > 4) {
            return null;
        }

        Integer[] nums = new Integer[parts.length];
        for (int i = 0; i < parts.length; i++) {
            nums[i] = parseIpComponent(parts[i]);
            if (nums[i] == null) {
                return null;
            }
        }

        if (parts.length == 1) {
            int n = nums[0];
            if (n > 0xffffffffL) return null;
            return new int[] {
                (n >>> 24) & 0xff,
                (n >>> 16) & 0xff,
                (n >>> 8) & 0xff,
                n & 0xff
            };
        }

        if (parts.length == 2) {
            int a = nums[0];
            int b = nums[1];
            if (a > 0xff || b > 0xffffff) return null;
            return new int[] {
                a,
                (b >>> 16) & 0xff,
                (b >>> 8) & 0xff,
                b & 0xff
            };
        }

        if (parts.length == 3) {
            int a = nums[0];
            int b = nums[1];
            int c = nums[2];
            if (a > 0xff || b > 0xff || c > 0xffff) return null;
            return new int[] {
                a, b,
                (c >>> 8) & 0xff,
                c & 0xff
            };
        }

        int a = nums[0];
        int b = nums[1];
        int c = nums[2];
        int d = nums[3];
        if (a > 0xff || b > 0xff || c > 0xff || d > 0xff) return null;
        return new int[] { a, b, c, d };
    }

    static int[] parseIpv6(String hostname) {
        String host = hostname;
        int[] ipv4Tail = null;

        if (host.contains(".")) {
            int lastColon = host.lastIndexOf(':');
            if (lastColon < 0) return null;
            String v4Part = host.substring(lastColon + 1);
            int[] parsedV4 = parseIpv4(v4Part);
            if (parsedV4 == null) return null;
            ipv4Tail = parsedV4;
            host = host.substring(0, lastColon);
        }

        int doubleColonCount = 0;
        for (int i = 0; i < host.length() - 1; i++) {
            if (host.charAt(i) == ':' && host.charAt(i + 1) == ':') {
                doubleColonCount++;
            }
        }
        if (doubleColonCount > 1) return null;

        String[] split = host.split("::", -1);
        String leftRaw = split.length > 0 ? split[0] : "";
        String rightRaw = split.length > 1 ? split[1] : "";

        String[] leftParts = leftRaw.isEmpty() ? new String[0] : leftRaw.split(":");
        String[] rightParts = rightRaw.isEmpty() ? new String[0] : rightRaw.split(":");

        List<Integer> left = new ArrayList<>();
        for (String part : leftParts) {
            if (!part.isEmpty() || leftParts.length == 1) {
                Integer val = parseHextet(part);
                if (val == null) return null;
                left.add(val);
            }
        }
        List<Integer> right = new ArrayList<>();
        for (String part : rightParts) {
            if (!part.isEmpty() || rightParts.length == 1) {
                Integer val = parseHextet(part);
                if (val == null) return null;
                right.add(val);
            }
        }

        int tailLength = ipv4Tail != null ? 2 : 0;
        int explicitLength = left.size() + right.size() + tailLength;

        int zerosToInsert = 0;
        if (doubleColonCount == 1) {
            zerosToInsert = 8 - explicitLength;
            if (zerosToInsert < 0) return null;
        } else if (explicitLength != 8) {
            return null;
        }

        int[] hextets = new int[8];
        int idx = 0;
        for (int val : left) {
            hextets[idx++] = val;
        }
        for (int i = 0; i < zerosToInsert; i++) {
            hextets[idx++] = 0;
        }
        for (int val : right) {
            hextets[idx++] = val;
        }

        if (ipv4Tail != null) {
            hextets[6] = (ipv4Tail[0] << 8) | ipv4Tail[1];
            hextets[7] = (ipv4Tail[2] << 8) | ipv4Tail[3];
        }

        return hextets;
    }

    private static Integer parseHextet(String part) {
        if (!part.matches("^[0-9a-fA-F]{1,4}$")) {
            return null;
        }
        return Integer.parseInt(part, 16);
    }

    static boolean isPrivateIpv4(int[] ip) {
        int a = ip[0];
        int b = ip[1];
        if (a == 127) return true; // 127.0.0.0/8
        if (a == 10) return true; // 10.0.0.0/8
        if (a == 172 && b >= 16 && b <= 31) return true; // 172.16.0.0/12
        if (a == 192 && b == 168) return true; // 192.168.0.0/16
        if (a == 169 && b == 254) return true; // 169.254.0.0/16
        if (a == 0) return true; // 0.0.0.0/8
        // CGNAT / Shared Address Space (RFC 6598)
        if (a == 100 && b >= 64 && b <= 127) return true; // 100.64.0.0/10
        // Benchmarking (RFC 2544)
        if (a == 198 && (b == 18 || b == 19)) return true; // 198.18.0.0/15
        // IETF Protocol Assignments (RFC 6890)
        if (a == 192 && b == 0 && ip[2] == 0) return true; // 192.0.0.0/24
        // TEST-NET-1 (RFC 5737)
        if (a == 192 && b == 0 && ip[2] == 2) return true; // 192.0.2.0/24
        // TEST-NET-2 (RFC 5737)
        if (a == 198 && b == 51 && ip[2] == 100) return true; // 198.51.100.0/24
        // TEST-NET-3 (RFC 5737)
        if (a == 203 && b == 0 && ip[2] == 113) return true; // 203.0.113.0/24
        // Reserved + broadcast (RFC 1112)
        if (a >= 240) return true; // 240.0.0.0/4
        return false;
    }

    static boolean isPrivateIpv6(int[] hextets) {
        boolean allZero = true;
        for (int h : hextets) {
            if (h != 0) {
                allZero = false;
                break;
            }
        }
        if (allZero) return true; // ::

        boolean isLoopback = true;
        for (int i = 0; i < 7; i++) {
            if (hextets[i] != 0) {
                isLoopback = false;
                break;
            }
        }
        if (isLoopback && hextets[7] == 1) return true; // ::1

        // fe80::/10 link-local
        if ((hextets[0] & 0xffc0) == 0xfe80) return true;

        // fc00::/7 unique local
        if ((hextets[0] & 0xfe00) == 0xfc00) return true;

        // IPv4-mapped ::ffff:x.x.x.x
        boolean isMapped = hextets[0] == 0 && hextets[1] == 0 && hextets[2] == 0
            && hextets[3] == 0 && hextets[4] == 0 && hextets[5] == 0xffff;
        if (isMapped) {
            int[] mapped = new int[] {
                (hextets[6] >>> 8) & 0xff,
                hextets[6] & 0xff,
                (hextets[7] >>> 8) & 0xff,
                hextets[7] & 0xff
            };
            return isPrivateIpv4(mapped);
        }

        // 2001:db8::/32 — Documentation prefix (RFC 3849)
        if (hextets[0] == 0x2001 && hextets[1] == 0x0db8) return true;

        // 64:ff9b::/96 — NAT64 well-known prefix (RFC 6052)
        if (hextets[0] == 0x0064 && hextets[1] == 0xff9b
            && hextets[2] == 0 && hextets[3] == 0
            && hextets[4] == 0 && hextets[5] == 0) {
            int[] embedded = new int[] {
                (hextets[6] >>> 8) & 0xff,
                hextets[6] & 0xff,
                (hextets[7] >>> 8) & 0xff,
                hextets[7] & 0xff
            };
            return isPrivateIpv4(embedded);
        }

        // 64:ff9b:1::/48 — NAT64 local-use prefix (RFC 8215)
        if (hextets[0] == 0x0064 && hextets[1] == 0xff9b && hextets[2] == 0x0001) {
            return true;
        }

        // 2002::/16 — 6to4 (RFC 3056)
        if (hextets[0] == 0x2002) {
            int[] embedded = new int[] {
                (hextets[1] >>> 8) & 0xff,
                hextets[1] & 0xff,
                (hextets[2] >>> 8) & 0xff,
                hextets[2] & 0xff
            };
            return isPrivateIpv4(embedded);
        }

        return false;
    }

    /**
     * Validates an allow-list configuration.
     * Returns a list of error messages for invalid entries.
     */
    public static List<String> validateAllowList(List<AllowedUrlEntry> allowedUrlPrefixes) {
        List<String> errors = new ArrayList<>();

        for (AllowedUrlEntry rawEntry : allowedUrlPrefixes) {
            String entry;
            if (rawEntry instanceof AllowedUrlEntry.StringEntry se) {
                entry = se.url();
            } else if (rawEntry instanceof AllowedUrlEntry.ObjectEntry oe) {
                entry = oe.allowedUrl().url();
            } else {
                errors.add("Invalid allow-list entry: must be a string URL or an object with a \"url\" string property");
                continue;
            }

            ParsedUrl parsed = parseUrl(entry);
            if (parsed == null) {
                errors.add("Invalid URL in allow-list: \"" + entry + "\" - must be a valid URL with scheme and host (e.g., \"https://example.com\")");
                continue;
            }

            URI uri;
            try {
                uri = new URI(entry);
            } catch (URISyntaxException e) {
                errors.add("Invalid URL in allow-list: \"" + entry + "\"");
                continue;
            }

            // Only allow http and https
            String scheme = uri.getScheme();
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                errors.add("Only http and https URLs are allowed in allow-list: \"" + entry + "\"");
                continue;
            }

            // Must have a valid host
            if (uri.getHost() == null || uri.getHost().isEmpty()) {
                errors.add("Allow-list entry must include a hostname: \"" + entry + "\"");
                continue;
            }

            String pathname = uri.getRawPath();
            if (pathname == null || pathname.isEmpty()) {
                pathname = "/";
            }
            if (!pathname.equals("/") && hasAmbiguousPathSeparators(pathname)) {
                errors.add("Allow-list entry contains ambiguous path separators: \"" + entry + "\"");
                continue;
            }

            // Warn about query strings and fragments
            if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
                errors.add("Query strings and fragments are ignored in allow-list entries: \"" + entry + "\"");
            }
        }

        return errors;
    }
}
