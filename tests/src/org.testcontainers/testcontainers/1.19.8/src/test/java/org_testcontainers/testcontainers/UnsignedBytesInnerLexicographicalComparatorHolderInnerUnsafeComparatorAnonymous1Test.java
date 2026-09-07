/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.util.Comparator;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.primitives.UnsignedBytes;

import static org.assertj.core.api.Assertions.assertThat;

public class UnsignedBytesInnerLexicographicalComparatorHolderInnerUnsafeComparatorAnonymous1Test {
    @Test
    void comparesUnsignedBytesAcrossFullMachineWordsAndTails() {
        Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();
        byte[] lower = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        byte[] higher = {1, 2, 3, 4, 5, 6, 7, 8, -1};

        assertThat(comparator.compare(lower, higher)).isNegative();
        assertThat(comparator.compare(higher, lower)).isPositive();
    }
}
