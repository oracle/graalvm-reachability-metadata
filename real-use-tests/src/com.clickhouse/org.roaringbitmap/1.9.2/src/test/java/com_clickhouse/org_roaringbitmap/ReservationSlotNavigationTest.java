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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the sparse reservation navigation subsystem to find neighboring and available slots.
public class ReservationSlotNavigationTest {
    @Test
    @Timeout(60)
    public void navigatesReservationsAcrossSparseSlotRanges() {
        RoaringBitmap reservedSlots = RoaringBitmap.bitmapOf(
                100, 101, 105, 65_536, 65_540);

        assertTrue(reservedSlots.contains(105));
        assertEquals(105L, reservedSlots.nextValue(102));
        assertEquals(101L, reservedSlots.previousValue(104));
        assertEquals(102L, reservedSlots.nextAbsentValue(100));
        assertEquals(104L, reservedSlots.previousAbsentValue(105));
        assertEquals(3L, reservedSlots.rangeCardinality(100, 106));
        assertEquals(2L, reservedSlots.rangeCardinality(65_536, 65_541));
    }
}
