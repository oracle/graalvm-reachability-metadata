/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.org_roaringbitmap;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.roaringbitmap.FastRankRoaringBitmap;
import org.roaringbitmap.PeekableIntRankIterator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the fast-rank bitmap subsystem to serve ranked audience membership updates.
public class FastRankAudienceIndexTest {
    @Test
    @Timeout(60)
    public void servesAudienceRanksAfterEligibilityUpdates() {
        FastRankRoaringBitmap eligibleCustomers = new FastRankRoaringBitmap();
        eligibleCustomers.add(101);
        eligibleCustomers.add(102);
        eligibleCustomers.add(103);
        eligibleCustomers.add(104);
        eligibleCustomers.add(65_536);
        eligibleCustomers.add(65_540);
        eligibleCustomers.add(120_000);

        assertFalse(eligibleCustomers.checkedAdd(104));
        assertTrue(eligibleCustomers.checkedAdd(65_541));
        assertTrue(eligibleCustomers.checkedRemove(104));
        assertFalse(eligibleCustomers.checkedRemove(106));

        assertEquals(7L, eligibleCustomers.getLongCardinality());
        assertEquals(4L, eligibleCustomers.rankLong(65_536));
        assertEquals(6L, eligibleCustomers.rankLong(65_541));
        assertEquals(103, eligibleCustomers.select(2));
        assertArrayEquals(
                new int[] {101, 102, 103, 65_536, 65_540, 65_541, 120_000},
                eligibleCustomers.toArray());

        PeekableIntRankIterator rankedCustomers = eligibleCustomers.getIntRankIterator();
        List<Integer> customerIds = new ArrayList<>();
        List<Integer> ranks = new ArrayList<>();
        while (rankedCustomers.hasNext()) {
            ranks.add(rankedCustomers.peekNextRank());
            customerIds.add(rankedCustomers.next());
        }

        assertEquals(
                List.of(101, 102, 103, 65_536, 65_540, 65_541, 120_000),
                customerIds);
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), ranks);
    }
}
