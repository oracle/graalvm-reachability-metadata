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

public class UnsignedBytesInnerLexicographicalComparatorHolderInnerUnsafeComparatorAnonymous1Test {
    @Test
    void comparatorHandlesLongPrefixesAndUnsignedDifference() {
        Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();
        byte[] lower = {10, 20, 30, 40, 50, 60, 70, (byte) 128, 1};
        byte[] higher = {10, 20, 30, 40, 50, 60, 70, (byte) 255, 0};

        assertThat(comparator.compare(lower, higher)).isNegative();
        assertThat(comparator.compare(higher, lower)).isPositive();
    }
}
