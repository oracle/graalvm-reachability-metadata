/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.thirdparty.com.google.common.primitives.UnsignedBytes;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

public class UnsignedBytesInnerLexicographicalComparatorHolderInnerUnsafeComparatorTest {
    @Test
    void lexicographicalComparatorUsesUnsignedLongBlockOrdering() {
        Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();

        byte[] lower = bytes(
                0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x7F,
                0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F);
        byte[] higher = bytes(
                0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x80,
                0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F);

        assertThat(comparator.compare(lower, higher)).isNegative();
        assertThat(comparator.compare(higher, lower)).isPositive();
        assertThat(comparator.compare(lower, lower.clone())).isZero();
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            bytes[index] = (byte) values[index];
        }
        return bytes;
    }
}
