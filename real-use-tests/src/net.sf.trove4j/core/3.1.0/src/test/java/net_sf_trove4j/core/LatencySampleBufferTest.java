/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Trove's primitive-array-list subsystem to summarize request latency samples.
public class LatencySampleBufferTest {
    @Test
    @Timeout(60)
    public void filtersAndRanksSamplesForLatencySummary() {
        TIntArrayList samples = new TIntArrayList();
        samples.add(new int[]{240, 85, 130, 95, 410, 175});

        TIntList withinServiceLevel = samples.grep(latency -> latency <= 250);
        withinServiceLevel.sort();
        TIntList outliers = samples.inverseGrep(latency -> latency <= 250);

        assertEquals(5, withinServiceLevel.size());
        assertArrayEquals(new int[]{85, 95, 130, 175, 240}, withinServiceLevel.toArray());
        assertEquals(1, outliers.size());
        assertEquals(410, outliers.get(0));
        assertEquals(2, withinServiceLevel.binarySearch(130));
    }
}
