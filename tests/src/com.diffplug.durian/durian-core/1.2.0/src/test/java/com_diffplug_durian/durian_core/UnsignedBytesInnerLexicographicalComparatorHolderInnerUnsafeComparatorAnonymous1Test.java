/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_core;

import java.util.Comparator;

import com.diffplug.common.primitives.UnsignedBytes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UnsignedBytesInnerLexicographicalComparatorHolderInnerUnsafeComparatorAnonymous1Test {
    @Test
    void lexicographicalComparatorInitializesUnsafeComparatorAndComparesUnsignedBytes() {
        Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();

        assertThat(comparator.compare(
                new byte[] {1, 2, 3},
                new byte[] {1, 2, (byte) 255}))
                .isNegative();
        assertThat(comparator.compare(
                new byte[] {1, 2, (byte) 255},
                new byte[] {1, 2, 3}))
                .isPositive();
        assertThat(comparator.compare(
                new byte[] {9, 8, 7},
                new byte[] {9, 8, 7, 0}))
                .isNegative();
    }
}
