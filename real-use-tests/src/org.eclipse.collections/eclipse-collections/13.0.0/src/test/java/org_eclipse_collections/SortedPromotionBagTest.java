/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections;

import java.util.List;

import org.eclipse.collections.api.bag.sorted.ImmutableSortedBag;
import org.eclipse.collections.api.bag.sorted.MutableSortedBag;
import org.eclipse.collections.api.factory.SortedBags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Eclipse Collections' sorted bag subsystem to publish a deterministic promotion catalog.
public class SortedPromotionBagTest {
    @Test
    @Timeout(60)
    public void publishesSortedPromotionLabelsWhilePreservingPopularityCounts() {
        MutableSortedBag<String> requestedPromotions = SortedBags.mutable.with(
                "free-shipping", "new-customer", "free-shipping", "bundle", "new-customer",
                "free-shipping");

        ImmutableSortedBag<String> publishedCatalog = requestedPromotions.toImmutable();
        ImmutableSortedBag<String> repeatedPromotions = publishedCatalog.selectDuplicates();
        ImmutableSortedBag<String> expandedCatalog = publishedCatalog.newWith("clearance");

        assertIterableEquals(
                List.of("bundle", "free-shipping", "free-shipping", "free-shipping",
                        "new-customer", "new-customer"),
                publishedCatalog);
        assertEquals(6, publishedCatalog.size());
        assertEquals(3, publishedCatalog.occurrencesOf("free-shipping"));
        assertEquals("bundle", publishedCatalog.min());
        assertEquals("new-customer", publishedCatalog.max());
        assertTrue(publishedCatalog.selectUnique().contains("bundle"));

        assertIterableEquals(
                List.of("free-shipping", "free-shipping", "free-shipping", "new-customer",
                        "new-customer"),
                repeatedPromotions);
        assertIterableEquals(
                List.of("bundle", "clearance", "free-shipping", "free-shipping", "free-shipping",
                        "new-customer", "new-customer"),
                expandedCatalog);
        assertEquals(6, publishedCatalog.size());
    }
}
