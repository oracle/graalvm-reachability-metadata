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
import org.roaringbitmap.BatchIterator;
import org.roaringbitmap.RoaringBitmap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises the batch iterator subsystem to resume a customer export in bounded batches.
public class BatchAudienceExportTest {
    @Test
    @Timeout(60)
    public void exportsEligibleCustomersAfterCheckpoint() {
        RoaringBitmap eligibleCustomers = RoaringBitmap.bitmapOf(
                101, 103, 105, 65_536, 65_537, 65_540, 131_072, 131_073);

        BatchIterator customerBatches = eligibleCustomers.getBatchIterator();
        customerBatches.advanceIfNeeded(65_536);
        int[] batch = new int[2];
        List<Integer> exportedCustomerIds = new ArrayList<>();
        List<Integer> exportedBatchSizes = new ArrayList<>();

        while (customerBatches.hasNext()) {
            int count = customerBatches.nextBatch(batch);
            exportedBatchSizes.add(count);
            for (int index = 0; index < count; index++) {
                exportedCustomerIds.add(batch[index]);
            }
        }

        assertEquals(
                List.of(65_536, 65_537, 65_540, 131_072, 131_073),
                exportedCustomerIds);
        assertEquals(List.of(2, 1, 2), exportedBatchSizes);
    }
}
