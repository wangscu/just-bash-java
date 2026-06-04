package com.justbash.encoding;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ByteStringTest {
    @Test
    void roundTripUtf8Bytes() {
        String original = "Hello, world!";
        ByteString bs = ByteString.fromUtf8String(original);
        assertThat(bs.decodeUtf8()).isEqualTo(original);
    }

    void latin1PreservesHighBytes() {
        byte[] bytes = {(byte) 0xC3, (byte) 0xA9};
        ByteString bs = ByteString.fromUtf8Bytes(bytes);
        assertThat(bs.length()).isEqualTo(2);
        assertThat(bs.toBytes()).containsExactly((byte) 0xC3, (byte) 0xA9);
    }

    @Test
    void toStringThrows() {
        ByteString bs = ByteString.fromLatin1("test");
        assertThatThrownBy(bs::toString)
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
