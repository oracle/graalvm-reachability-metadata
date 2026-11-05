/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections;

import org.eclipse.collections.api.factory.primitive.IntIntMaps;
import org.eclipse.collections.api.factory.primitive.IntLists;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.map.primitive.IntIntMap;
import org.eclipse.collections.api.map.primitive.MutableIntIntMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Eclipse Collections' primitive collections subsystem for inventory allocation.
public class PrimitiveInventoryAnalyticsTest {
    @Test
    @Timeout(60)
    public void findsLowStockItemsFromAnImmutableInventorySnapshot() {
        MutableIntIntMap unitsBySku = IntIntMaps.mutable.with(
                101, 12,
                205, 4,
                309, 9,
                412, 2);
        unitsBySku.addToValue(101, -3);
        unitsBySku.addToValue(205, 6);
        unitsBySku.addToValue(412, -1);

        IntIntMap inventorySnapshot = unitsBySku.toImmutable();
        MutableIntList catalogOrder = IntLists.mutable.with(309, 412, 101, 205);
        MutableIntList lowStockSkus = catalogOrder.select(
                sku -> inventorySnapshot.getIfAbsent(sku, 0) < 5);
        lowStockSkus.sortThis();

        assertArrayEquals(new int[]{412}, lowStockSkus.toArray());
        assertEquals(9, inventorySnapshot.get(101));
        assertEquals(10, inventorySnapshot.get(205));
        assertEquals(9, inventorySnapshot.get(309));
        assertEquals(1, inventorySnapshot.get(412));
        assertEquals(29, inventorySnapshot.values().sum());
    }
}
