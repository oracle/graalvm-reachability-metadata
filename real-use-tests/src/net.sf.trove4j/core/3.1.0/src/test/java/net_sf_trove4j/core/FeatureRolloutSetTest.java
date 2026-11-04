/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.set.hash.THashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Trove's object-set subsystem to maintain feature-rollout segments.
public class FeatureRolloutSetTest {
    @Test
    @Timeout(60)
    public void keepsOnlySupportedSegmentsForRollout() {
        THashSet<String> requestedSegments = new THashSet<>();
        requestedSegments.addAll(List.of("beta", "staff", "beta", "legacy"));

        Set<String> supportedSegments = Set.of("beta", "staff", "public");
        requestedSegments.retainAll(supportedSegments);

        assertEquals(2, requestedSegments.size());
        assertTrue(requestedSegments.containsAll(List.of("beta", "staff")));
        assertFalse(requestedSegments.contains("legacy"));

        requestedSegments.remove("staff");

        assertEquals(Set.of("beta"), requestedSegments);
    }
}
