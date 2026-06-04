package com.justbash.encoding;

import java.nio.charset.StandardCharsets;

public final class ByteString {
    private final String internal;

    private ByteString(String internal) {
        this.internal = internal;
    }

    public static ByteString fromLatin1(String latin1) {
        return new ByteString(latin1);
    }

    public static ByteString fromUtf8Bytes(byte[] bytes) {
        char[] latin1 = new char[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            latin1[i] = (char) (bytes[i] & 0xFF);
        }
        return new ByteString(new String(latin1));
    }

    public static ByteString fromUtf8String(String utf8) {
        return fromUtf8Bytes(utf8.getBytes(StandardCharsets.UTF_8));
    }

    public byte[] toBytes() {
        byte[] bytes = new byte[internal.length()];
        for (int i = 0; i < internal.length(); i++) {
            bytes[i] = (byte) internal.charAt(i);
        }
        return bytes;
    }

    public String asLatin1() {
        return internal;
    }

    public String decodeUtf8() {
        return new String(toBytes(), StandardCharsets.UTF_8);
    }

    public boolean isEmpty() {
        return internal.isEmpty();
    }

    public int length() {
        return internal.length();
    }

    public ByteString concat(ByteString other) {
        return new ByteString(this.internal + other.internal);
    }

    @Override
    public String toString() {
        throw new UnsupportedOperationException(
            "Use decodeUtf8() or asLatin1() explicitly");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ByteString other)) return false;
        return internal.equals(other.internal);
    }

    @Override
    public int hashCode() {
        return internal.hashCode();
    }
}
