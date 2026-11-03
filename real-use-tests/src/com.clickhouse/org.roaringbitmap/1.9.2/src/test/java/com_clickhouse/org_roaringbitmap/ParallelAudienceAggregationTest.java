/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.org_roaringbitmap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.roaringbitmap.ParallelAggregation;
import org.roaringbitmap.RoaringBitmap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the parallel bitmap aggregation subsystem to merge regional customer postings.
public class ParallelAudienceAggregationTest {
    @Test
    @Timeout(60)
    public void mergesRegionalPostingsAndFindsSingleRegionCustomers() {
        RoaringBitmap northAmerica = RoaringBitmap.bitmapOf(
                11, 17, 65_536, 65_540, 131_072);
        RoaringBitmap europe = RoaringBitmap.bitmapOf(
                17, 23, 65_536, 65_541, 196_608);
        RoaringBitmap asiaPacific = RoaringBitmap.bitmapOf(
                23, 29, 65_542, 131_072, 196_608);

        RoaringBitmap allCustomers = ParallelAggregation.or(
                northAmerica, europe, asiaPacific);
        RoaringBitmap singleRegionCustomers = ParallelAggregation.xor(
                northAmerica, europe, asiaPacific);

        assertArrayEquals(
                new int[] {
                    11, 17, 23, 29, 65_536, 65_540, 65_541, 65_542, 131_072, 196_608
                },
                allCustomers.toArray());
        assertArrayEquals(
                new int[] {11, 29, 65_540, 65_541, 65_542},
                singleRegionCustomers.toArray());
        assertEquals(10L, allCustomers.getLongCardinality());
        assertEquals(5L, singleRegionCustomers.getLongCardinality());
        assertTrue(allCustomers.contains(196_608));
        assertFalse(singleRegionCustomers.contains(196_608));
        assertEquals(65_541, allCustomers.select(6));
        assertEquals(9L, allCustomers.rankLong(131_072));
    }
}
