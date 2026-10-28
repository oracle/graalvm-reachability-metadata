/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ehcache.ehcache;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.ResourceType;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Exercises Ehcache's runtime resource-pool resizing subsystem for a storefront promotion cache.
 * A shop expands a live cache before publishing a larger promotion and restores its normal budget afterward.
 */
public class PromotionCacheResizingTest {
    @Test
    @Timeout(60)
    public void expandsLiveCapacityAndRestoresTheNormalBudgetAfterThePromotion() {
        try (CacheManager manager = CacheManagerBuilder.newCacheManagerBuilder()
                .withCache("promotion-pages", CacheConfigurationBuilder.newCacheConfigurationBuilder(
                        String.class, String.class, ResourcePoolsBuilder.heap(10)))
                .withCache("checkout-messages", CacheConfigurationBuilder.newCacheConfigurationBuilder(
                        String.class, String.class, ResourcePoolsBuilder.heap(10)))
                .build(true)) {
            Cache<String, String> pages = manager.getCache("promotion-pages", String.class, String.class);
            Cache<String, String> checkout = manager.getCache("checkout-messages", String.class, String.class);
            checkout.put("delivery", "Free delivery on orders over 50");

            Map<String, String> publishedPages = new LinkedHashMap<>();
            publishedPages.put("coffee", "Coffee subscription: 1299");
            publishedPages.put("tea", "Tea selection: 799");
            pages.putAll(publishedPages);

            // Grow before publishing so the expanded working set fits without depending on eviction order.
            pages.getRuntimeConfiguration().updateResourcePools(ResourcePoolsBuilder.heap(100).build());
            assertEquals(100L, pages.getRuntimeConfiguration().getResourcePools()
                    .getPoolForResource(ResourceType.Core.HEAP).getSize());
            assertEquals(publishedPages, pages.getAll(publishedPages.keySet()));

            Map<String, String> promotionPages = new LinkedHashMap<>();
            for (int product = 0; product < 40; product++) {
                promotionPages.put("promotion-" + product, "Promotion product " + product + ": 499");
            }
            pages.putAll(promotionPages);
            publishedPages.putAll(promotionPages);
            assertEquals(publishedPages, pages.getAll(publishedPages.keySet()));

            // Retire the promotion before reducing the cache's budget for the smaller everyday catalog.
            pages.clear();
            pages.getRuntimeConfiguration().updateResourcePools(ResourcePoolsBuilder.heap(10).build());
            assertEquals(10L, pages.getRuntimeConfiguration().getResourcePools()
                    .getPoolForResource(ResourceType.Core.HEAP).getSize());
            pages.put("coffee", "Coffee subscription: 1299");
            pages.put("tea", "Tea selection: 799");
            assertEquals("Coffee subscription: 1299", pages.get("coffee"));
            assertEquals("Tea selection: 799", pages.get("tea"));
            assertNull(pages.get("promotion-0"));

            // Resizing one cache must not disturb another cache serving active checkouts.
            assertEquals("Free delivery on orders over 50", checkout.get("delivery"));
            assertEquals(10L, checkout.getRuntimeConfiguration().getResourcePools()
                    .getPoolForResource(ResourceType.Core.HEAP).getSize());
        }
    }
}
