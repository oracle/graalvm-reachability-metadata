/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the bidirectional map subsystem for a product SKU directory.
 */
public class ProductSkuDirectoryTest {
    @Test
    @Timeout(60)
    public void resolvesProductsBySkuAndSkuByProduct() {
        BidiMap<String, String> skuByProduct = new DualHashBidiMap<>();
        skuByProduct.put("mechanical-keyboard", "SKU-1001");
        skuByProduct.put("ultrawide-monitor", "SKU-1002");

        assertEquals("SKU-1001", skuByProduct.get("mechanical-keyboard"));
        assertEquals("mechanical-keyboard", skuByProduct.getKey("SKU-1001"));
        assertTrue(skuByProduct.containsValue("SKU-1002"));

        BidiMap<String, String> productBySku = skuByProduct.inverseBidiMap();
        assertEquals("ultrawide-monitor", productBySku.get("SKU-1002"));
        assertEquals("SKU-1001", productBySku.getKey("mechanical-keyboard"));

        productBySku.put("SKU-1002", "gaming-monitor");
        assertFalse(skuByProduct.containsKey("ultrawide-monitor"));
        assertEquals("SKU-1002", skuByProduct.get("gaming-monitor"));
        assertEquals("gaming-monitor", productBySku.get("SKU-1002"));

        skuByProduct.removeValue("SKU-1001");
        assertFalse(productBySku.containsKey("SKU-1001"));
        assertEquals(1, skuByProduct.size());
    }
}
