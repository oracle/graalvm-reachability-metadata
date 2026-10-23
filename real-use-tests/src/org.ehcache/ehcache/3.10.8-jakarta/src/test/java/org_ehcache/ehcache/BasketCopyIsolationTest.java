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
import org.ehcache.spi.copy.Copier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises Ehcache's heap value-copying subsystem for isolated shopping basket edits.
 * Request handlers edit private basket copies and publish changes only when the shopper saves them.
 */
public class BasketCopyIsolationTest {
    @Test
    @Timeout(60)
    public void keepsUnsavedEditsOutOfTheSharedBasketCache() {
        try (CacheManager manager = CacheManagerBuilder.newCacheManagerBuilder()
                .withCache("baskets", CacheConfigurationBuilder.newCacheConfigurationBuilder(
                        String.class, Basket.class, ResourcePoolsBuilder.heap(100))
                        .withValueCopier(new BasketCopier()))
                .build(true)) {
            Cache<String, Basket> baskets = manager.getCache("baskets", String.class, Basket.class);
            Basket submitted = new Basket(Map.of("coffee", 2, "tea", 1));
            baskets.put("alice", submitted);
            baskets.put("bob", new Basket(Map.of("cocoa", 3)));

            // Reusing a submitted form object must not change the saved basket.
            submitted.items.clear();
            assertEquals(Map.of("coffee", 2, "tea", 1), baskets.get("alice").items);

            Basket editor = baskets.get("alice");
            Basket checkoutPreview = baskets.get("alice");
            assertNotNull(editor);
            assertNotNull(checkoutPreview);
            editor.items.put("coffee", 4);
            editor.items.remove("tea");
            editor.items.put("biscuits", 2);

            assertEquals(Map.of("coffee", 2, "tea", 1), checkoutPreview.items);
            assertEquals(Map.of("coffee", 2, "tea", 1), baskets.get("alice").items);

            // Saving publishes the edited contents without giving the editor ownership of stored data.
            baskets.put("alice", editor);
            editor.items.put("coffee", 99);
            assertEquals(Map.of("coffee", 4, "biscuits", 2), baskets.get("alice").items);
            assertEquals(Map.of("coffee", 2, "tea", 1), checkoutPreview.items);

            Basket abandonedEdit = baskets.get("alice");
            assertNotNull(abandonedEdit);
            abandonedEdit.items.clear();
            assertEquals(Map.of("coffee", 4, "biscuits", 2), baskets.get("alice").items);
            assertEquals(Map.of("cocoa", 3), baskets.get("bob").items);
        }
    }

    private static final class Basket {
        private final Map<String, Integer> items;

        private Basket(Map<String, Integer> items) {
            this.items = new LinkedHashMap<>(items);
        }
    }

    private static final class BasketCopier implements Copier<Basket> {
        @Override
        public Basket copyForRead(Basket basket) {
            return new Basket(basket.items);
        }

        @Override
        public Basket copyForWrite(Basket basket) {
            return new Basket(basket.items);
        }
    }
}
