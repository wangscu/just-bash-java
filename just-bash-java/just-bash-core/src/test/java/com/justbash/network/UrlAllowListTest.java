package com.justbash.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class UrlAllowListTest {

    // ------------------------------------------------------------------
    // parseUrl
    // ------------------------------------------------------------------

    @Test
    void testParseUrlValid() {
        UrlAllowList.ParsedUrl parsed = UrlAllowList.parseUrl("https://example.com/path");
        assertNotNull(parsed);
        assertEquals("https://example.com", parsed.origin());
        assertEquals("/path", parsed.pathname());
    }

    @Test
    void testParseUrlWithPort() {
        UrlAllowList.ParsedUrl parsed = UrlAllowList.parseUrl("http://example.com:8080/path");
        assertNotNull(parsed);
        assertEquals("http://example.com:8080", parsed.origin());
    }

    @Test
    void testParseUrlInvalid() {
        assertNull(UrlAllowList.parseUrl("not-a-url"));
        assertNull(UrlAllowList.parseUrl("/relative/path"));
    }

    // ------------------------------------------------------------------
    // normalizeAllowListEntry
    // ------------------------------------------------------------------

    @Test
    void testNormalizeAllowListEntry() {
        UrlAllowList.NormalizedEntry entry = UrlAllowList.normalizeAllowListEntry("https://api.example.com/v1/");
        assertNotNull(entry);
        assertEquals("https://api.example.com", entry.origin());
        assertEquals("/v1/", entry.pathPrefix());
    }

    // ------------------------------------------------------------------
    // matchesAllowListEntry
    // ------------------------------------------------------------------

    @Test
    void testMatchesAllowListEntryOriginOnly() {
        assertTrue(UrlAllowList.matchesAllowListEntry("https://example.com/", "https://example.com"));
        assertTrue(UrlAllowList.matchesAllowListEntry("https://example.com/path", "https://example.com"));
    }

    @Test
    void testMatchesAllowListEntryPathPrefix() {
        assertTrue(UrlAllowList.matchesAllowListEntry("https://api.example.com/v1/users", "https://api.example.com/v1"));
        assertTrue(UrlAllowList.matchesAllowListEntry("https://api.example.com/v1/", "https://api.example.com/v1"));
        assertFalse(UrlAllowList.matchesAllowListEntry("https://api.example.com/v10", "https://api.example.com/v1"));
        assertFalse(UrlAllowList.matchesAllowListEntry("https://api.example.com/v1-admin", "https://api.example.com/v1"));
    }

    @Test
    void testMatchesAllowListEntryTrailingSlash() {
        assertTrue(UrlAllowList.matchesAllowListEntry("https://api.example.com/v1/users", "https://api.example.com/v1/"));
        assertFalse(UrlAllowList.matchesAllowListEntry("https://api.example.com/v2/users", "https://api.example.com/v1/"));
    }

    @Test
    void testMatchesAllowListEntryDifferentOrigin() {
        assertFalse(UrlAllowList.matchesAllowListEntry("https://evil.com/path", "https://example.com/path"));
    }

    @Test
    void testMatchesAllowListEntryAmbiguousSeparators() {
        assertFalse(UrlAllowList.matchesAllowListEntry("https://example.com/v1%2fadmin", "https://example.com/v1"));
        assertFalse(UrlAllowList.matchesAllowListEntry("https://example.com/v1%5cadmin", "https://example.com/v1"));
        assertFalse(UrlAllowList.matchesAllowListEntry("https://example.com/v1\\admin", "https://example.com/v1"));
    }

    @Test
    void testMatchesAllowListEntryCaseSensitivity() {
        assertFalse(UrlAllowList.matchesAllowListEntry("https://Example.com/path", "https://example.com/path"));
        assertFalse(UrlAllowList.matchesAllowListEntry("HTTP://example.com/path", "https://example.com/path"));
    }

    // ------------------------------------------------------------------
    // isUrlAllowed
    // ------------------------------------------------------------------

    @Test
    void testIsUrlAllowedEmptyList() {
        assertFalse(UrlAllowList.isUrlAllowed("https://example.com", List.of()));
    }

    @Test
    void testIsUrlAllowedWithStringEntries() {
        List<AllowedUrlEntry> entries = List.of(
            new AllowedUrlEntry.StringEntry("https://api.example.com/v1"),
            new AllowedUrlEntry.StringEntry("https://other.com")
        );
        assertTrue(UrlAllowList.isUrlAllowed("https://api.example.com/v1/users", entries));
        assertTrue(UrlAllowList.isUrlAllowed("https://other.com/path", entries));
        assertFalse(UrlAllowList.isUrlAllowed("https://evil.com", entries));
    }

    @Test
    void testIsUrlAllowedWithObjectEntries() {
        List<AllowedUrlEntry> entries = List.of(
            new AllowedUrlEntry.ObjectEntry(new AllowedUrl("https://api.example.com/v1"))
        );
        assertTrue(UrlAllowList.isUrlAllowed("https://api.example.com/v1/users", entries));
    }

    // ------------------------------------------------------------------
    // isPrivateIp - IPv4
    // ------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
        "127.0.0.1", "127.255.255.255", "10.0.0.1", "10.255.255.255",
        "172.16.0.1", "172.31.255.255", "192.168.1.1", "192.168.255.255",
        "169.254.1.1", "0.0.0.0", "100.64.0.1", "100.127.255.255",
        "198.18.0.1", "198.19.255.255", "192.0.0.1", "192.0.2.1",
        "198.51.100.1", "203.0.113.1", "240.0.0.1", "255.255.255.255"
    })
    void testPrivateIpv4(String ip) {
        assertTrue(UrlAllowList.isPrivateIp(ip), ip + " should be private");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "8.8.8.8", "1.1.1.1", "203.0.113.255" // 203.0.113.255 is still in TEST-NET-3
    })
    void testPublicIpv4(String ip) {
        if (ip.equals("203.0.113.255")) {
            assertTrue(UrlAllowList.isPrivateIp(ip));
        } else {
            assertFalse(UrlAllowList.isPrivateIp(ip), ip + " should be public");
        }
    }

    @Test
    void testPrivateIpv4CompactForms() {
        assertTrue(UrlAllowList.isPrivateIp("2130706433")); // 127.0.0.1 as single number
    }

    @Test
    void testPrivateIpv4HexOctal() {
        assertTrue(UrlAllowList.isPrivateIp("0x7f.0x00.0x00.0x01")); // 127.0.0.1 in hex
        assertTrue(UrlAllowList.isPrivateIp("0177.0000.0000.0001")); // 127.0.0.1 in octal
    }

    // ------------------------------------------------------------------
    // isPrivateIp - IPv6
    // ------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
        "::", "::1", "fe80::1", "febf::1", "fc00::1", "fdff::1",
        "2001:db8::1", "[::1]", "[fe80::1]"
    })
    void testPrivateIpv6(String ip) {
        assertTrue(UrlAllowList.isPrivateIp(ip), ip + " should be private");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "2001:4860:4860::8888", // Google DNS
        "2606:4700:4700::1111"  // Cloudflare DNS
    })
    void testPublicIpv6(String ip) {
        assertFalse(UrlAllowList.isPrivateIp(ip), ip + " should be public");
    }

    @Test
    void testPrivateIpv6MappedIpv4() {
        assertTrue(UrlAllowList.isPrivateIp("::ffff:127.0.0.1"));
        assertTrue(UrlAllowList.isPrivateIp("::ffff:10.0.0.1"));
        assertFalse(UrlAllowList.isPrivateIp("::ffff:8.8.8.8"));
    }

    @Test
    void testPrivateIpv6Nat64() {
        // NAT64 with embedded private IPv4 (hex form)
        assertTrue(UrlAllowList.isPrivateIp("64:ff9b::7f00:1"));
        // NAT64 with embedded public IPv4 (hex form: 8.8.8.8 = 0808:0808)
        assertFalse(UrlAllowList.isPrivateIp("64:ff9b::0808:0808"));
    }

    @Test
    void testPrivateIpv66to4() {
        assertTrue(UrlAllowList.isPrivateIp("2002:7f00:1::")); // 127.0.0.1 embedded
        assertFalse(UrlAllowList.isPrivateIp("2002:808:808::")); // 8.8.8.8 embedded
    }

    // ------------------------------------------------------------------
    // localhost
    // ------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
        "localhost", "sub.localhost", "myapp.localhost"
    })
    void testLocalhost(String host) {
        assertTrue(UrlAllowList.isPrivateIp(host));
    }

    // ------------------------------------------------------------------
    // validateAllowList
    // ------------------------------------------------------------------

    @Test
    void testValidateAllowListValid() {
        List<String> errors = UrlAllowList.validateAllowList(List.of(
            new AllowedUrlEntry.StringEntry("https://example.com"),
            new AllowedUrlEntry.StringEntry("https://api.example.com/v1/")
        ));
        assertTrue(errors.isEmpty());
    }

    @Test
    void testValidateAllowListInvalidUrl() {
        List<String> errors = UrlAllowList.validateAllowList(List.of(
            new AllowedUrlEntry.StringEntry("not-a-url")
        ));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("Invalid URL"));
    }

    @Test
    void testValidateAllowListWrongProtocol() {
        List<String> errors = UrlAllowList.validateAllowList(List.of(
            new AllowedUrlEntry.StringEntry("ftp://example.com")
        ));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("Only http and https"));
    }

    @Test
    void testValidateAllowListAmbiguousSeparators() {
        List<String> errors = UrlAllowList.validateAllowList(List.of(
            new AllowedUrlEntry.StringEntry("https://example.com/v1%2fadmin")
        ));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("ambiguous path separators"));
    }

    @Test
    void testValidateAllowListQueryStringWarning() {
        List<String> errors = UrlAllowList.validateAllowList(List.of(
            new AllowedUrlEntry.StringEntry("https://example.com?foo=bar")
        ));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("Query strings"));
    }

    @Test
    void testValidateAllowListMultipleErrors() {
        List<String> errors = UrlAllowList.validateAllowList(List.of(
            new AllowedUrlEntry.StringEntry("ftp://example.com"),
            new AllowedUrlEntry.StringEntry("not-a-url")
        ));
        assertEquals(2, errors.size());
    }

    @Test
    void testValidateAllowListObjectEntry() {
        List<String> errors = UrlAllowList.validateAllowList(List.of(
            new AllowedUrlEntry.ObjectEntry(new AllowedUrl("https://example.com"))
        ));
        assertTrue(errors.isEmpty());
    }

    // ------------------------------------------------------------------
    // parseIpv4 / parseIpv6
    // ------------------------------------------------------------------

    @Test
    void testParseIpv4Standard() {
        int[] result = UrlAllowList.parseIpv4("192.168.1.1");
        assertNotNull(result);
        assertArrayEquals(new int[]{192, 168, 1, 1}, result);
    }

    @Test
    void testParseIpv4OnePart() {
        int[] result = UrlAllowList.parseIpv4("2130706433");
        assertNotNull(result);
        assertArrayEquals(new int[]{127, 0, 0, 1}, result);
    }

    @Test
    void testParseIpv4TwoParts() {
        int[] result = UrlAllowList.parseIpv4("127.1");
        assertNotNull(result);
        assertArrayEquals(new int[]{127, 0, 0, 1}, result);
    }

    @Test
    void testParseIpv4Invalid() {
        assertNull(UrlAllowList.parseIpv4("999.999.999.999"));
        assertNull(UrlAllowList.parseIpv4("a.b.c.d"));
        assertNull(UrlAllowList.parseIpv4("1.2.3.4.5"));
    }

    @Test
    void testParseIpv6Full() {
        int[] result = UrlAllowList.parseIpv6("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
        assertNotNull(result);
        assertEquals(8, result.length);
        assertEquals(0x2001, result[0]);
    }

    @Test
    void testParseIpv6Compressed() {
        int[] result = UrlAllowList.parseIpv6("2001:db8::1");
        assertNotNull(result);
        assertEquals(8, result.length);
        assertEquals(0x2001, result[0]);
        assertEquals(1, result[7]);
    }

    @Test
    void testParseIpv6Loopback() {
        int[] result = UrlAllowList.parseIpv6("::1");
        assertNotNull(result);
        assertArrayEquals(new int[]{0, 0, 0, 0, 0, 0, 0, 1}, result);
    }

    @Test
    void testParseIpv6Invalid() {
        assertNull(UrlAllowList.parseIpv6(":::1"));
        assertNull(UrlAllowList.parseIpv6("2001:db8::1::1"));
    }
}
