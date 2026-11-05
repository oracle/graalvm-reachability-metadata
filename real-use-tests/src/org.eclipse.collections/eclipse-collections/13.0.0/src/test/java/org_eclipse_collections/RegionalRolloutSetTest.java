/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections;

import java.util.List;

import org.eclipse.collections.api.factory.SortedSets;
import org.eclipse.collections.api.set.sorted.ImmutableSortedSet;
import org.eclipse.collections.api.set.sorted.MutableSortedSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Eclipse Collections' sorted set subsystem to prepare a regional feature rollout.
public class RegionalRolloutSetTest {
    @Test
    @Timeout(60)
    public void publishesApprovedRegionsInDeterministicOrder() {
        MutableSortedSet<String> requestedRegions = SortedSets.mutable.with(
                "us-west", "eu-central", "ap-south", "eu-central");
        MutableSortedSet<String> approvedRegions = SortedSets.mutable.with(
                "eu-central", "us-west", "us-east");

        MutableSortedSet<String> rolloutRegions = requestedRegions.intersect(approvedRegions);
        ImmutableSortedSet<String> publishedRegions = rolloutRegions.toImmutable();

        assertIterableEquals(List.of("eu-central", "us-west"), publishedRegions);
        assertEquals("eu-central", rolloutRegions.getFirst());
        assertEquals("us-west", rolloutRegions.getLast());
        assertEquals(2, publishedRegions.size());
        assertTrue(publishedRegions.contains("eu-central"));
        assertFalse(publishedRegions.contains("ap-south"));
    }
}
