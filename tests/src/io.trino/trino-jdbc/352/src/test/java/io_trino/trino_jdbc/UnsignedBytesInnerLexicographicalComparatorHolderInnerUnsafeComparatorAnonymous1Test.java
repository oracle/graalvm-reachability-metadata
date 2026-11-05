/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.primitives.UnsignedBytes;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UnsignedBytesInnerLexicographicalComparatorHolderInnerUnsafeComparatorAnonymous1Test {
    @Test
    void comparesMachineWordAndTrailingByteSegments() {
        Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();
        byte[] first = new byte[19];
        byte[] second = first.clone();
        second[9] = (byte) 255;
        assertTrue(comparator.compare(first, second) < 0);
        first[9] = (byte) 255;
        first[18] = 1;
        assertTrue(comparator.compare(first, second) > 0);
        assertEquals(0, comparator.compare(first, first.clone()));
    }
}
