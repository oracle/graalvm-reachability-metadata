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
import org.roaringbitmap.RoaringBitmapWriter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the streaming bitmap writer subsystem to build an audience index from ordered events.
public class StreamingAudienceBitmapWriterTest {
    @Test
    @Timeout(60)
    public void buildsAudienceIndexFromOrderedEvents() {
        RoaringBitmapWriter<RoaringBitmap> writer = RoaringBitmapWriter.writer()
                .constantMemory()
                .expectedRange(0, 1_000_000)
                .runCompress(true)
                .get();

        writer.add(101);
        writer.add(103);
        writer.add(65_536);
        writer.add(65_537);
        writer.add(1_000_000 - 1);
        writer.flush();

        RoaringBitmap audience = writer.get();

        assertArrayEquals(new int[] {101, 103, 65_536, 65_537, 999_999}, audience.toArray());
        assertEquals(5L, audience.getLongCardinality());
        assertTrue(audience.contains(65_537));
        assertFalse(audience.contains(102));
        assertEquals(65_536, audience.select(2));
        assertEquals(4L, audience.rankLong(65_537));
    }
}
