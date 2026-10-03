/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.UnsignedBytes;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

public class UnsignedBytesInnerLexicographicalComparatorHolderInnerUnsafeComparatorTest {
    private static final String UNSAFE_COMPARATOR_CLASS_NAME =
            "com.google.common.primitives.UnsignedBytes"
                    + "$LexicographicalComparatorHolder$UnsafeComparator";
    private static final String UNSAFE_COMPARATOR_DESCRIPTION =
            "UnsignedBytes.lexicographicalComparator() "
                    + "(sun.misc.Unsafe version)";

    @Test
    void unsafeComparatorInitializesThroughPrivilegedUnsafeLookup() throws Exception {
        Class<?> unsafeComparatorClass = Class.forName(UNSAFE_COMPARATOR_CLASS_NAME);

        assertThat(unsafeComparatorClass.isEnum()).isTrue();
        assertThat(unsafeComparatorClass.getEnumConstants())
                .extracting(Object::toString)
                .containsExactly(UNSAFE_COMPARATOR_DESCRIPTION);
    }

    @Test
    void publicLexicographicalComparatorUsesUnsignedByteOrdering() {
        Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();

        assertThat(comparator.compare(bytes(0x00, 0x7F), bytes(0x00, 0x80))).isNegative();
        assertThat(comparator.compare(bytes(0x00, 0xFF), bytes(0x01, 0x00))).isNegative();
        assertThat(comparator.compare(bytes(0x01, 0x00), bytes(0x00, 0xFF))).isPositive();
        assertThat(comparator.compare(bytes(0x01, 0x02), bytes(0x01, 0x02))).isZero();
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            bytes[index] = (byte) values[index];
        }
        return bytes;
    }
}
