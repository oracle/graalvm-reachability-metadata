/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ehcache.ehcache;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.MemoryUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Exercises Ehcache's heap/off-heap tiered storage subsystem for a product-manual download catalog.
 * A storefront keeps a large catalog behind a small heap tier and serves revised manuals without stale copies.
 */
public class ProductManualTieredCacheTest {
    @Test
    @Timeout(60)
    public void retainsManualsBeyondTheHeapBudgetAndKeepsRevisionsConsistent() {
        Map<String, String> publishedManuals = new LinkedHashMap<>();
        for (int product = 0; product < 24; product++) {
            publishedManuals.put("product-" + product, manual(product, "First edition"));
        }

        try (CacheManager manager = CacheManagerBuilder.newCacheManagerBuilder()
                .withCache("product-manuals", CacheConfigurationBuilder.newCacheConfigurationBuilder(
                        String.class, String.class,
                        ResourcePoolsBuilder.heap(2).offheap(4, MemoryUnit.MB)))
                .build(true)) {
            Cache<String, String> manuals = manager.getCache("product-manuals", String.class, String.class);
            manuals.putAll(publishedManuals);

            // All manuals must remain available even though only two fit in the heap tier.
            // Do not depend on which entries the heap tier happens to retain.
            for (Map.Entry<String, String> publication : publishedManuals.entrySet()) {
                assertEquals(publication.getValue(), manuals.get(publication.getKey()));
            }

            String revisedManual = manual(0, "Revised safety instructions");
            manuals.put("product-0", revisedManual);
            publishedManuals.put("product-0", revisedManual);
            manuals.remove("product-1");
            publishedManuals.remove("product-1");
            assertEquals(revisedManual, manuals.get("product-0"));
            assertNull(manuals.get("product-1"));

            // Cycle through the catalog in both directions so reads cannot rely on a warm heap entry.
            for (int product = 23; product >= 0; product--) {
                assertEquals(publishedManuals.get("product-" + product), manuals.get("product-" + product));
            }
            for (Map.Entry<String, String> publication : publishedManuals.entrySet()) {
                assertEquals(publication.getValue(), manuals.get(publication.getKey()));
            }
            assertEquals(revisedManual, manuals.get("product-0"));
            assertNull(manuals.get("product-1"), "A retired manual must not return from the off-heap tier");
        }
    }

    private static String manual(int product, String edition) {
        return "Product " + product + " | " + edition + "\n"
                + "Installation: disconnect power before servicing. Keep ventilation openings clear.\n".repeat(128);
    }
}
