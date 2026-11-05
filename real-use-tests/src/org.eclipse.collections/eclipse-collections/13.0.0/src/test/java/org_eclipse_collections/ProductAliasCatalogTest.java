/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections;

import org.eclipse.collections.api.bimap.BiMap;
import org.eclipse.collections.api.bimap.ImmutableBiMap;
import org.eclipse.collections.api.bimap.MutableBiMap;
import org.eclipse.collections.api.factory.BiMaps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Eclipse Collections' bidirectional map subsystem for product aliases.
public class ProductAliasCatalogTest {
    @Test
    @Timeout(60)
    public void resolvesProductAliasesInBothDirectionsAfterAnAliasUpdate() {
        MutableBiMap<String, String> aliasToProduct = BiMaps.mutable.with(
                "coffee-mug", "SKU-101",
                "desk-lamp", "SKU-205",
                "travel-tote", "SKU-309");
        aliasToProduct.forcePut("coffee-cup", "SKU-101");

        ImmutableBiMap<String, String> catalogSnapshot = aliasToProduct.toImmutable();
        BiMap<String, String> productToAlias = catalogSnapshot.inverse();

        assertEquals(3, catalogSnapshot.size());
        assertFalse(catalogSnapshot.containsKey("coffee-mug"));
        assertEquals("SKU-101", catalogSnapshot.get("coffee-cup"));
        assertEquals("coffee-cup", productToAlias.get("SKU-101"));
        assertEquals("desk-lamp", productToAlias.get("SKU-205"));
        assertTrue(catalogSnapshot.containsValue("SKU-309"));
    }
}
