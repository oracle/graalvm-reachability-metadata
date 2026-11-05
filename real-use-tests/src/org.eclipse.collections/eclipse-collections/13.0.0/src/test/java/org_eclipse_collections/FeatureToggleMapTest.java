/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections;

import java.util.List;

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.map.MutableMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Eclipse Collections' map subsystem to publish a feature-toggle snapshot.
public class FeatureToggleMapTest {
    @Test
    @Timeout(60)
    public void publishesEnabledTogglesWithoutChangingTheNextRollout() {
        MutableMap<String, Toggle> configuredToggles = Maps.mutable.with(
                "search", new Toggle(true, "v2"),
                "recommendations", new Toggle(false, "v1"),
                "checkout", new Toggle(true, "v3"));

        MutableMap<String, Toggle> enabledToggles = configuredToggles.select(
                (name, toggle) -> toggle.enabled());
        ImmutableMap<String, Toggle> publishedSnapshot = enabledToggles.toImmutable();
        ImmutableMap<String, Toggle> nextSnapshot = publishedSnapshot.newWithKeyValue(
                "recommendations", new Toggle(true, "v2"));

        assertEquals(2, publishedSnapshot.size());
        assertTrue(publishedSnapshot.containsKey("search"));
        assertFalse(publishedSnapshot.containsKey("recommendations"));
        assertEquals("v3", publishedSnapshot.get("checkout").version());
        assertIterableEquals(List.of("checkout", "search"),
                publishedSnapshot.keysView().toSortedList());

        assertEquals(3, nextSnapshot.size());
        assertTrue(nextSnapshot.get("recommendations").enabled());
        assertFalse(publishedSnapshot.containsKey("recommendations"));
        assertFalse(configuredToggles.get("recommendations").enabled());
    }

    private record Toggle(boolean enabled, String version) {
    }
}
