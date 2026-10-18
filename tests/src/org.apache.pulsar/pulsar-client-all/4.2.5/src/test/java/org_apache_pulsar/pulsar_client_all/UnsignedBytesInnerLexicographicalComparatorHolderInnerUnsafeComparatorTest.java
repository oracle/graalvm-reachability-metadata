/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import org.apache.pulsar.shade.com.google.common.primitives.UnsignedBytes;
import org.junit.jupiter.api.Test;

public class UnsignedBytesInnerLexicographicalComparatorHolderInnerUnsafeComparatorTest {

    @Test
    void comparesUnsignedByteArraysLexicographically() {
        final Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();

        assertThat(comparator.compare(new byte[] {0, 1}, new byte[] {0, 2})).isNegative();
        assertThat(comparator.compare(new byte[] {(byte) 255}, new byte[] {1})).isPositive();
        assertThat(comparator.compare(new byte[] {4, 5}, new byte[] {4, 5})).isZero();
    }
}
