/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_core;

import com.diffplug.common.primitives.UnsignedBytes;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UnsignedBytesInnerLexicographicalComparatorHolderInnerUnsafeComparatorAnonymous1Test {
    @Test
    public void lexicographicalComparatorInitializesUnsafeComparator() {
        Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();

        assertThat(comparator.getClass().getName()).endsWith("UnsafeComparator");
        assertThat(comparator.compare(bytes(0, 1, 2, 255), bytes(0, 1, 2, 255))).isZero();
        assertThat(comparator.compare(bytes(0, 1, 2, 255), bytes(0, 1, 3, 0))).isNegative();
        assertThat(comparator.compare(bytes(1, 0, 0, 0, 0, 0, 0, 0, 0), bytes(0, 255))).isPositive();
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = UnsignedBytes.checkedCast(values[i]);
        }
        return bytes;
    }
}
