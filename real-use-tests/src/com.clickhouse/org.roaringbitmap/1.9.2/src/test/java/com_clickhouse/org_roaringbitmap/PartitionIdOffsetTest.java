/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.org_roaringbitmap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.roaringbitmap.RoaringBitmap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the bitmap offset subsystem to translate partition-local customer IDs into global IDs.
public class PartitionIdOffsetTest {
    @Test
    @Timeout(60)
    public void translatesPartitionIdsWithoutChangingTheLocalIndex() {
        RoaringBitmap partitionCustomers = RoaringBitmap.bitmapOf(
                3, 7, 65_534, 65_536, 131_073);
        long partitionBase = 10_000_000L;

        RoaringBitmap globalCustomers = RoaringBitmap.addOffset(
                partitionCustomers, partitionBase);

        assertArrayEquals(
                new int[] {10_000_003, 10_000_007, 10_065_534, 10_065_536, 10_131_073},
                globalCustomers.toArray());
        assertEquals(5L, globalCustomers.getLongCardinality());
        assertTrue(globalCustomers.contains(10_065_536));
        assertFalse(globalCustomers.contains(65_536));
        assertEquals(10_065_534, globalCustomers.select(2));
        assertEquals(4L, globalCustomers.rankLong(10_065_536));

        assertArrayEquals(
                new int[] {3, 7, 65_534, 65_536, 131_073}, partitionCustomers.toArray());
        assertTrue(partitionCustomers.contains(65_536));
        assertFalse(partitionCustomers.contains(10_065_536));
    }
}
