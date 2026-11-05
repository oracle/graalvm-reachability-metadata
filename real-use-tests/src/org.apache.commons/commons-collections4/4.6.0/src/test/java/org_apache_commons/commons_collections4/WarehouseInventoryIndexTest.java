/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Commons Collections' multi-valued map subsystem to maintain a warehouse inventory index.
 */
public class WarehouseInventoryIndexTest {
    @Test
    @Timeout(60)
    public void indexesProductsByWarehouseAndBuildsReverseLookup() {
        ArrayListValuedHashMap<String, String> productsByWarehouse = new ArrayListValuedHashMap<>();
        productsByWarehouse.putAll("east", List.of("coffee", "tea"));
        productsByWarehouse.putAll("west", List.of("coffee", "cocoa"));

        assertEquals(4, productsByWarehouse.size());
        assertEquals(Set.of("coffee", "tea"), Set.copyOf(productsByWarehouse.get("east")));
        assertTrue(productsByWarehouse.containsMapping("west", "cocoa"));

        MultiValuedMap<String, String> warehousesByProduct = productsByWarehouse.inverted();
        assertEquals(Set.of("east", "west"), Set.copyOf(warehousesByProduct.get("coffee")));
        assertEquals(Set.of("east"), Set.copyOf(warehousesByProduct.get("tea")));
        assertFalse(warehousesByProduct.containsMapping("east", "cocoa"));

        productsByWarehouse.removeMapping("east", "tea");
        assertEquals(Set.of("coffee"), Set.copyOf(productsByWarehouse.get("east")));
    }
}
