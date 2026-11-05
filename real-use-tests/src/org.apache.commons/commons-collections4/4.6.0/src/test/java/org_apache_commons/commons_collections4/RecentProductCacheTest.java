/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.map.LRUMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Commons Collections' bounded LRU map subsystem for a product lookup cache.
 */
public class RecentProductCacheTest {
    @Test
    @Timeout(60)
    public void keepsRecentlyUsedProductsAndEvictsTheLeastRecentlyUsedEntry() {
        LRUMap<String, Product> cache = new LRUMap<>(2);
        Product keyboard = new Product("Keyboard", 79);
        Product monitor = new Product("Monitor", 249);
        Product mouse = new Product("Mouse", 29);

        cache.put("keyboard", keyboard);
        cache.put("monitor", monitor);
        assertEquals(2, cache.size());
        assertSame(keyboard, cache.get("keyboard"));

        cache.put("mouse", mouse);

        assertTrue(cache.containsKey("keyboard"));
        assertTrue(cache.containsKey("mouse"));
        assertFalse(cache.containsKey("monitor"));
        assertEquals("Keyboard", cache.get("keyboard").name());
        assertEquals(29, cache.get("mouse").priceInDollars());
        assertEquals(2, cache.maxSize());
        assertTrue(cache.isFull());
    }

    private static final class Product {
        private final String name;
        private final int priceInDollars;

        private Product(String name, int priceInDollars) {
            this.name = name;
            this.priceInDollars = priceInDollars;
        }

        private String name() {
            return name;
        }

        private int priceInDollars() {
            return priceInDollars;
        }
    }
}
