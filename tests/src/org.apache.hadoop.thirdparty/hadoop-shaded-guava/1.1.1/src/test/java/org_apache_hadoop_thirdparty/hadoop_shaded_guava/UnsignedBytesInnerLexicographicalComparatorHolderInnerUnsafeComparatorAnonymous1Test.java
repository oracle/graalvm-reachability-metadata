/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;

import org.apache.hadoop.thirdparty.com.google.common.primitives.UnsignedBytes;
import org.junit.jupiter.api.Test;

public class UnsignedBytesInnerLexicographicalComparatorHolderInnerUnsafeComparatorAnonymous1Test {
    @Test
    void lexicographicalComparatorOrdersByteArraysByUnsignedValues() {
        Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();

        byte[] smallerUnsignedValue = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8};
        byte[] largerUnsignedValue = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, (byte) 0x80};
        byte[] samePrefixButLonger = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};

        assertThat(comparator.compare(smallerUnsignedValue, largerUnsignedValue)).isLessThan(0);
        assertThat(comparator.compare(largerUnsignedValue, smallerUnsignedValue)).isGreaterThan(0);
        assertThat(comparator.compare(smallerUnsignedValue, smallerUnsignedValue.clone())).isZero();
        assertThat(comparator.compare(smallerUnsignedValue, samePrefixButLonger)).isLessThan(0);
    }
}
