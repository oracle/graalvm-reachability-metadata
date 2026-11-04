/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.TCollections;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises Trove's collection-wrapper subsystem to publish a read-only product catalog.
public class ReadOnlyCatalogWrapperTest {
    @Test
    @Timeout(60)
    public void publishesCatalogWithoutAllowingConsumerChanges() {
        TObjectIntHashMap<String> catalog = new TObjectIntHashMap<>();
        catalog.put("cable", 12);
        catalog.put("adapter", 3);

        TObjectIntMap<String> publishedCatalog = TCollections.unmodifiableMap(catalog);

        assertEquals(2, publishedCatalog.size());
        assertEquals(12, publishedCatalog.get("cable"));
        assertEquals(3, publishedCatalog.get("adapter"));
        assertThrows(UnsupportedOperationException.class,
                () -> publishedCatalog.put("charger", 2));
        assertEquals(2, catalog.size());
    }
}
