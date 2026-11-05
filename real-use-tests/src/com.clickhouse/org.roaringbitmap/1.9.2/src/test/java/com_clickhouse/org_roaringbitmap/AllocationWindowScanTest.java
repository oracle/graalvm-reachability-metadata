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
import org.roaringbitmap.RelativeRangeConsumer;
import org.roaringbitmap.RoaringBitmap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises the relative range traversal subsystem to find available slots in a partitioned allocation window.
public class AllocationWindowScanTest {
    @Test
    @Timeout(60)
    public void scansAllocationWindowAcrossBitmapContainers() {
        RoaringBitmap allocatedSlots = RoaringBitmap.bitmapOf(
                65_535, 65_536, 65_537, 65_539);
        AvailabilityScan scan = new AvailabilityScan();

        allocatedSlots.forAllInRange(65_534, 7, scan);

        assertEquals(List.of(1, 2, 3, 5), scan.occupiedOffsets);
        assertEquals(List.of("0-1", "4-5", "6-7"), scan.availableRanges);
        assertEquals(0, scan.firstAvailableOffset());
    }

    private static final class AvailabilityScan implements RelativeRangeConsumer {
        private final List<Integer> occupiedOffsets = new ArrayList<>();
        private final List<String> availableRanges = new ArrayList<>();

        @Override
        public void acceptPresent(int offset) {
            occupiedOffsets.add(offset);
        }

        @Override
        public void acceptAbsent(int offset) {
            availableRanges.add(offset + "-" + (offset + 1));
        }

        @Override
        public void acceptAllPresent(int start, int end) {
            for (int offset = start; offset < end; offset++) {
                occupiedOffsets.add(offset);
            }
        }

        @Override
        public void acceptAllAbsent(int start, int end) {
            availableRanges.add(start + "-" + end);
        }

        private int firstAvailableOffset() {
            return Integer.parseInt(availableRanges.get(0).split("-", 2)[0]);
        }
    }
}
