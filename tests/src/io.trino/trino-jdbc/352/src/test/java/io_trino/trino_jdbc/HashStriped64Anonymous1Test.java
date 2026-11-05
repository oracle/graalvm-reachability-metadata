/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.hash.BloomFilter;
import io.trino.jdbc.$internal.guava.hash.Funnels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HashStriped64Anonymous1Test {
    @Test
    void countsInsertedBloomFilterBits() {
        BloomFilter<Integer> filter = BloomFilter.create(Funnels.integerFunnel(), 100);
        assertTrue(filter.put(37));
        assertFalse(filter.put(37));
        assertTrue(filter.mightContain(37));
        assertTrue(filter.approximateElementCount() >= 1);
    }
}
