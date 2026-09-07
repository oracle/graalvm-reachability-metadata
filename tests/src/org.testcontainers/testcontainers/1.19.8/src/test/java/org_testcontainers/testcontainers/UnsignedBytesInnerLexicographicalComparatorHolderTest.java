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

public class UnsignedBytesInnerLexicographicalComparatorHolderTest {
    @Test
    void selectsAndUsesTheBestUnsignedByteArrayComparator() {
        Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();

        assertThat(comparator.compare(new byte[] { 0, -1 }, new byte[] { 1, 0 })).isNegative();
        assertThat(comparator.compare(new byte[] { -1 }, new byte[] { 127 })).isPositive();
    }
}
