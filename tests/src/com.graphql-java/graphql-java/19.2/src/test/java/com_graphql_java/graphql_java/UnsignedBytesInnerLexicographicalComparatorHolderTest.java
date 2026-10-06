/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import static org.assertj.core.api.Assertions.assertThat;

import graphql.com.google.common.primitives.UnsignedBytes;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

public class UnsignedBytesInnerLexicographicalComparatorHolderTest {
    @Test
    void choosesComparatorAndOrdersBytesAsUnsignedValues() {
        Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();

        assertThat(comparator.compare(new byte[] {0, (byte) 255}, new byte[] {1, 0}))
                .isNegative();
        assertThat(comparator.compare(new byte[] {(byte) 255}, new byte[] {127}))
                .isPositive();
        assertThat(comparator.compare(new byte[] {1, 2}, new byte[] {1, 2})).isZero();
    }
}
