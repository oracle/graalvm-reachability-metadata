/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections;

import java.util.List;

import org.eclipse.collections.api.LazyIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

/// Exercises Eclipse Collections' lazy iterable subsystem to build a featured product feed.
public class LazyRecommendationPipelineTest {
    @Test
    @Timeout(60)
    public void publishesTheFirstAvailableFeaturedProducts() {
        MutableList<Product> catalog = Lists.mutable.with(
                new Product("starter", true, 12),
                new Product("pro", true, 4),
                new Product("enterprise", true, 0),
                new Product("legacy", false, 20));

        LazyIterable<String> availableFeaturedProducts = catalog.asLazy()
                .select(Product::featured)
                .select(product -> product.stock() > 0)
                .collect(Product::sku)
                .take(2);
        ImmutableList<String> publishedFeed = availableFeaturedProducts.toImmutableList();

        assertIterableEquals(List.of("starter", "pro"), publishedFeed);
        assertEquals(2, publishedFeed.size());
        assertEquals("starter", publishedFeed.getFirst());
        assertEquals("pro", publishedFeed.getLast());
    }

    private record Product(String sku, boolean featured, int stock) {
    }
}
