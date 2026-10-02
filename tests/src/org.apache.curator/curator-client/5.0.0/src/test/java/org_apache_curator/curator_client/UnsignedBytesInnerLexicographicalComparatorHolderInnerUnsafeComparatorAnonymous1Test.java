/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import java.util.Comparator;

import org.apache.curator.shaded.com.google.common.primitives.UnsignedBytes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UnsignedBytesInnerLexicographicalComparatorHolderInnerUnsafeComparatorAnonymous1Test {
    @Test
    void comparesByteArraysUsingUnsignedLexicographicalOrder() {
        Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();

        assertThat(comparator.compare(bytes(0x00, 0x7f), bytes(0x00, 0x80))).isNegative();
        assertThat(comparator.compare(bytes(0x00, 0xff), bytes(0x01, 0x00))).isNegative();
        assertThat(comparator.compare(bytes(0x01, 0x00), bytes(0x00, 0xff))).isPositive();
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            bytes[index] = (byte) values[index];
        }
        return bytes;
    }
}
