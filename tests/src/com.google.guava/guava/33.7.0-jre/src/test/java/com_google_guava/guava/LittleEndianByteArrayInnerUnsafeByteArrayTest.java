/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import org.junit.jupiter.api.Test;

public class LittleEndianByteArrayInnerUnsafeByteArrayTest {
    private static final String UNSAFE_BYTE_ARRAY_CLASS_NAME =
            "com.google.common.hash.LittleEndianByteArray$UnsafeByteArray";

    @Test
    void unsafeByteArrayInitializesUnsafeThroughPrivilegedReflection() throws Exception {
        HashCode hashCode = Hashing.murmur3_128().hashBytes(bytesForLittleEndianRead());

        assertThat(hashCode.asBytes()).hasSize(16);

        Class<?> unsafeByteArrayClass = Class.forName(UNSAFE_BYTE_ARRAY_CLASS_NAME);

        assertThat(unsafeByteArrayClass.isEnum()).isTrue();
        assertThat(unsafeByteArrayClass.getEnumConstants())
                .extracting(Object::toString)
                .containsExactly("UNSAFE_LITTLE_ENDIAN", "UNSAFE_BIG_ENDIAN");
    }

    private static byte[] bytesForLittleEndianRead() {
        byte[] bytes = new byte[32];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) (index * 3 + 1);
        }
        return bytes;
    }
}
