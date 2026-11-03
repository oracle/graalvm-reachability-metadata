/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.org_roaringbitmap;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.roaringbitmap.buffer.ImmutableRoaringBitmap;
import org.roaringbitmap.buffer.MutableRoaringBitmap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the buffer-backed bitmap subsystem to query a serialized product segment.
public class BufferBackedProductSegmentTest {
    @Test
    @Timeout(60)
    public void queriesSerializedSegmentWithCurrentEligibilityFilter() {
        MutableRoaringBitmap productsInSegment = MutableRoaringBitmap.bitmapOf(
                12, 13, 14, 70_000, 70_001, 120_000);
        ByteBuffer serializedSegment = ByteBuffer.allocateDirect(
                productsInSegment.serializedSizeInBytes());
        productsInSegment.serialize(serializedSegment);
        serializedSegment.flip();

        ImmutableRoaringBitmap segmentIndex = new ImmutableRoaringBitmap(serializedSegment);
        MutableRoaringBitmap eligibleProducts = MutableRoaringBitmap.bitmapOf(
                13, 14, 70_001, 90_000, 120_000);
        ImmutableRoaringBitmap eligibleIndex = eligibleProducts.toImmutableRoaringBitmap();
        MutableRoaringBitmap matches = ImmutableRoaringBitmap.and(segmentIndex, eligibleIndex);

        assertArrayEquals(new int[] {12, 13, 14, 70_000, 70_001, 120_000},
                segmentIndex.toArray());
        assertArrayEquals(new int[] {13, 14, 70_001, 120_000}, matches.toArray());
        assertEquals(6L, segmentIndex.getLongCardinality());
        assertEquals(4L, matches.getLongCardinality());
        assertTrue(segmentIndex.contains(70_001));
        assertFalse(matches.contains(90_000));
        assertEquals(4L, segmentIndex.rankLong(70_000));
        assertEquals(70_001, matches.select(2));
        assertEquals(5L, segmentIndex.rangeCardinality(13, 120_001));
    }
}
