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

public class UnsignedBytesInnerLexicographicalComparatorHolderTest {
    @Test
    void ordersBytesAsUnsignedValues() {
        Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();
        assertTrue(comparator.compare(new byte[] {127}, new byte[] {(byte) 128}) < 0);
        assertTrue(comparator.compare(new byte[] {1}, new byte[] {1, 0}) < 0);
        assertEquals(0, comparator.compare(new byte[] {(byte) 255}, new byte[] {(byte) 255}));
    }
}
