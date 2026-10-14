/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThat;

import autovalue.shaded.com.google.common.primitives.UnsignedBytes;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

public class UnsignedBytesInnerLexicographicalComparatorHolderTest {
    @Test
    void comparesBytesAsUnsignedValues() {
        Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();

        assertThat(comparator.compare(new byte[] {(byte) 0xff}, new byte[] {1})).isPositive();
        assertThat(comparator.compare(new byte[] {1, 2}, new byte[] {1, 3})).isNegative();
    }
}
