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
import org.roaringbitmap.longlong.LongIterator;
import org.roaringbitmap.longlong.Roaring64NavigableMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the navigable 64-bit bitmap subsystem to maintain a time-ordered event timeline.
public class Roaring64NavigableMapTest {
    @Test
    @Timeout(60)
    public void servesOrderedEventsAfterRangeUpdates() {
        long dayOneFirstEvent = (7L << 32) | 100L;
        long dayOneLastEvent = (7L << 32) | 104L;
        long dayTwoFirstEvent = (8L << 32) | 200L;

        Roaring64NavigableMap eventTimeline = new Roaring64NavigableMap();
        eventTimeline.addRange(dayOneFirstEvent, dayOneLastEvent);
        eventTimeline.add(dayTwoFirstEvent);
        eventTimeline.add(dayTwoFirstEvent + 1L);
        eventTimeline.add(dayOneFirstEvent + 2L);
        eventTimeline.removeLong(dayOneFirstEvent + 1L);

        assertArrayEquals(
                new long[] {
                    dayOneFirstEvent, dayOneFirstEvent + 2L, dayOneFirstEvent + 3L,
                    dayTwoFirstEvent, dayTwoFirstEvent + 1L
                },
                eventTimeline.toArray());
        assertEquals(5L, eventTimeline.getLongCardinality());
        assertEquals(dayOneFirstEvent, eventTimeline.first());
        assertEquals(dayTwoFirstEvent + 1L, eventTimeline.last());
        assertEquals(3L, eventTimeline.rankLong(dayOneFirstEvent + 3L));
        assertEquals(dayTwoFirstEvent, eventTimeline.select(3L));
        assertTrue(eventTimeline.contains(dayOneFirstEvent + 2L));
        assertFalse(eventTimeline.contains(dayOneFirstEvent + 1L));

        List<Long> newestFirst = new ArrayList<>();
        LongIterator reverseIterator = eventTimeline.getReverseLongIterator();
        while (reverseIterator.hasNext()) {
            newestFirst.add(reverseIterator.next());
        }
        assertEquals(
                List.of(
                        dayTwoFirstEvent + 1L, dayTwoFirstEvent, dayOneFirstEvent + 3L,
                        dayOneFirstEvent + 2L, dayOneFirstEvent),
                newestFirst);
    }
}
