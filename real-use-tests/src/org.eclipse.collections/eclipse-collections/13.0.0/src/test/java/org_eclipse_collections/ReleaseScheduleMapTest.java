/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections;

import java.util.List;

import org.eclipse.collections.api.factory.SortedMaps;
import org.eclipse.collections.api.map.sorted.ImmutableSortedMap;
import org.eclipse.collections.api.map.sorted.MutableSortedMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

/// Exercises Eclipse Collections' sorted map subsystem to publish an ordered release schedule.
public class ReleaseScheduleMapTest {
    @Test
    @Timeout(60)
    public void publishesApprovedReleasesInDateOrder() {
        MutableSortedMap<Integer, Release> schedule = SortedMaps.mutable.with(
                10, new Release("alpha", false),
                20, new Release("beta", true),
                30, new Release("ga", true));
        schedule.withKeyValue(15, new Release("preview", true));

        MutableSortedMap<Integer, Release> approved = schedule.select(
                (day, release) -> release.approved());
        ImmutableSortedMap<Integer, Release> publishedSchedule = approved.toImmutable();
        MutableSortedMap<Integer, Release> earlyWindow = approved.headMap(30);
        approved.withKeyValue(40, new Release("hotfix", true));

        assertIterableEquals(List.of(15, 20, 30), publishedSchedule.keysView().toSortedList());
        assertEquals("preview", publishedSchedule.get(15).name());
        assertEquals("ga", publishedSchedule.get(30).name());
        assertEquals(2, earlyWindow.size());
        assertFalse(earlyWindow.containsKey(30));
        assertEquals(4, approved.size());
        assertFalse(publishedSchedule.containsKey(40));
    }

    private record Release(String name, boolean approved) {
    }
}
