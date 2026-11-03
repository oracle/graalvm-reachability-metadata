/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.org_roaringbitmap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.roaringbitmap.longlong.Roaring64Bitmap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the 64-bit bitmap subsystem to merge globally unique event IDs from service shards.
public class GlobalEventLedgerTest {
    @Test
    @Timeout(60)
    public void mergesShardSnapshotsAndRemovesReplayedEvents() {
        long firstShardEvent = (3L << 32) | 11L;
        long firstShardTail = (3L << 32) | 15L;
        long secondShardEvent = (9L << 32) | 2L;
        long secondShardTail = (9L << 32) | 8L;

        Roaring64Bitmap firstShard = Roaring64Bitmap.bitmapOf(
                firstShardEvent, firstShardEvent + 1L, firstShardTail);
        Roaring64Bitmap secondShard = Roaring64Bitmap.bitmapOf(
                secondShardEvent, secondShardEvent + 1L, secondShardTail);
        Roaring64Bitmap replayedEvents = Roaring64Bitmap.bitmapOf(
                firstShardEvent + 1L, secondShardEvent + 1L);

        Roaring64Bitmap ledger = firstShard.clone();
        ledger.or(secondShard);
        ledger.andNot(replayedEvents);

        assertArrayEquals(
                new long[] {firstShardEvent, firstShardTail, secondShardEvent, secondShardTail},
                ledger.toArray());
        assertEquals(4L, ledger.getLongCardinality());
        assertTrue(ledger.contains(secondShardTail));
        assertFalse(ledger.contains(firstShardEvent + 1L));
        assertEquals(firstShardTail, ledger.select(1L));
        assertEquals(2L, ledger.rankLong(firstShardTail));
        assertEquals(secondShardTail, ledger.last());
    }
}
