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

public class UnsignedBytesInnerLexicographicalComparatorHolderTest {
    @Test
    void selectsComparatorAndComparesUnsignedBytes() {
        Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();

        assertThat(comparator.compare(new byte[] {(byte) 0xff}, new byte[] {0})).isPositive();
        assertThat(comparator.compare(new byte[] {1, 2}, new byte[] {1, 2})).isZero();
    }
}
