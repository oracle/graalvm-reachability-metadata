/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.map.custom_hash.TObjectIntCustomHashMap;
import gnu.trove.strategy.HashingStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Trove's custom-hashing map subsystem for a case-insensitive inventory.
public class CustomHashingInventoryTest {
    @Test
    @Timeout(60)
    public void combinesStockUpdatesAcrossCaseVariants() {
        TObjectIntCustomHashMap<String> stock =
                new TObjectIntCustomHashMap<>(new CaseInsensitiveSkuStrategy());

        stock.adjustOrPutValue("sku-17", 4, 4);
        stock.adjustOrPutValue("SKU-17", 3, 3);
        stock.put("Sku-23", 8);

        assertEquals(2, stock.size());
        assertEquals(7, stock.get("sKu-17"));
        assertEquals(8, stock.get("SKU-23"));
        assertEquals(15, stock.values()[0] + stock.values()[1]);
    }

    private static final class CaseInsensitiveSkuStrategy implements HashingStrategy<String> {
        @Override
        public int computeHashCode(String sku) {
            return sku.toLowerCase(Locale.ROOT).hashCode();
        }

        @Override
        public boolean equals(String left, String right) {
            return left.equalsIgnoreCase(right);
        }
    }
}
