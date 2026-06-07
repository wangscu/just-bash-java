package com.justbash.postgres;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PathEncodingTest {

    // encodeLabel

    @Test
    void encodeLabelPassesThroughAlphanumeric() {
        assertThat(PathEncoding.encodeLabel("abc123")).isEqualTo("abc123");
    }

    @Test
    void encodeLabelPassesThroughHyphen() {
        assertThat(PathEncoding.encodeLabel("my-file")).isEqualTo("my-file");
    }

    @Test
    void encodeLabelEncodesUnderscore() {
        assertThat(PathEncoding.encodeLabel("my_file")).isEqualTo("my__5F__file");
    }

    @Test
    void encodeLabelEncodesDot() {
        assertThat(PathEncoding.encodeLabel("readme.md")).isEqualTo("readme__dot__md");
    }

    @Test
    void encodeLabelEncodesSpace() {
        assertThat(PathEncoding.encodeLabel("my file")).isEqualTo("my__sp__file");
    }

    @Test
    void encodeLabelEncodesSpecialChars() {
        assertThat(PathEncoding.encodeLabel("a@b")).isEqualTo("a__40__b");
    }

    @Test
    void encodeLabelRejectsEmptyString() {
        assertThatThrownBy(() -> PathEncoding.encodeLabel(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot encode empty filename");
    }

    @Test
    void encodeLabelRejectsNullByte() {
        assertThatThrownBy(() -> PathEncoding.encodeLabel("a\0b"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null bytes");
    }

    @Test
    void encodeLabelRejectsTooLong() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) sb.append('a');
        assertThatThrownBy(() -> PathEncoding.encodeLabel(sb.toString()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds ltree label limit");
    }

    @Test
    void encodeLabelNoCollisionBetweenLiteralAndEncodedDot() {
        // A file literally named "__dot__" should encode differently from "."
        assertThat(PathEncoding.encodeLabel("__dot__")).isNotEqualTo(PathEncoding.encodeLabel("."));
    }

    // decodeLabel

    @Test
    void decodeLabelRoundtripsAlphanumeric() {
        assertThat(PathEncoding.decodeLabel("abc123")).isEqualTo("abc123");
    }

    @Test
    void decodeLabelDecodesDot() {
        assertThat(PathEncoding.decodeLabel("readme__dot__md")).isEqualTo("readme.md");
    }

    @Test
    void decodeLabelDecodesSpace() {
        assertThat(PathEncoding.decodeLabel("my__sp__file")).isEqualTo("my file");
    }

    @Test
    void decodeLabelDecodesUnderscore() {
        assertThat(PathEncoding.decodeLabel("my__5F__file")).isEqualTo("my_file");
    }

    @Test
    void decodeLabelDecodesSpecialChars() {
        assertThat(PathEncoding.decodeLabel("a__40__b")).isEqualTo("a@b");
    }

    @Test
    void decodeLabelRoundtripsEmoji() {
        String encoded = PathEncoding.encodeLabel("file😀");
        assertThat(PathEncoding.decodeLabel(encoded)).isEqualTo("file😀");
    }

    @Test
    void decodeLabelHandlesComplexName() {
        String original = "my_file.test v2";
        String encoded = PathEncoding.encodeLabel(original);
        assertThat(PathEncoding.decodeLabel(encoded)).isEqualTo(original);
    }

    // normalizePath

    @Test
    void normalizePathHandlesRoot() {
        assertThat(PathEncoding.normalizePath("/")).isEqualTo("/");
    }

    @Test
    void normalizePathHandlesSimplePath() {
        assertThat(PathEncoding.normalizePath("/home/user")).isEqualTo("/home/user");
    }

    @Test
    void normalizePathHandlesTrailingSlash() {
        assertThat(PathEncoding.normalizePath("/home/user/")).isEqualTo("/home/user");
    }

    @Test
    void normalizePathHandlesDoubleSlashes() {
        assertThat(PathEncoding.normalizePath("/home//user")).isEqualTo("/home/user");
    }

    @Test
    void normalizePathHandlesDot() {
        assertThat(PathEncoding.normalizePath("/home/./user")).isEqualTo("/home/user");
    }

    @Test
    void normalizePathHandlesDotDot() {
        assertThat(PathEncoding.normalizePath("/home/user/../docs")).isEqualTo("/home/docs");
    }

    @Test
    void normalizePathHandlesDotDotBeyondRoot() {
        assertThat(PathEncoding.normalizePath("/../../etc")).isEqualTo("/etc");
    }

    @Test
    void normalizePathRejectsNullByte() {
        assertThatThrownBy(() -> PathEncoding.normalizePath("/home\0user"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null bytes");
    }

    // pathToLtree / ltreeToPath

    @Test
    void pathToLtreeHandlesRoot() {
        assertThat(PathEncoding.pathToLtree("/", 42)).isEqualTo("s42");
    }

    @Test
    void pathToLtreeHandlesSimplePath() {
        assertThat(PathEncoding.pathToLtree("/home/user", 42)).isEqualTo("s42.home.user");
    }

    @Test
    void pathToLtreeEncodesSpecialChars() {
        assertThat(PathEncoding.pathToLtree("/readme.md", 1)).isEqualTo("s1.readme__dot__md");
    }

    @Test
    void ltreeToPathHandlesRoot() {
        assertThat(PathEncoding.ltreeToPath("s42")).isEqualTo("/");
    }

    @Test
    void ltreeToPathHandlesSimplePath() {
        assertThat(PathEncoding.ltreeToPath("s42.home.user")).isEqualTo("/home/user");
    }

    @Test
    void ltreeToPathDecodesSpecialChars() {
        assertThat(PathEncoding.ltreeToPath("s1.readme__dot__md")).isEqualTo("/readme.md");
    }

    @Test
    void pathToLtreeRoundtrip() {
        String[] paths = {"/", "/a", "/a/b/c", "/my file.txt", "/dir_with_underscore"};
        for (String path : paths) {
            String ltree = PathEncoding.pathToLtree(path, 99);
            assertThat(PathEncoding.ltreeToPath(ltree)).isEqualTo(path)
                .withFailMessage("Roundtrip failed for: " + path);
        }
    }

    @Test
    void pathToLtreeNormalizesBeforeEncoding() {
        assertThat(PathEncoding.pathToLtree("/home/./user/../docs", 1))
            .isEqualTo(PathEncoding.pathToLtree("/home/docs", 1));
    }
}
