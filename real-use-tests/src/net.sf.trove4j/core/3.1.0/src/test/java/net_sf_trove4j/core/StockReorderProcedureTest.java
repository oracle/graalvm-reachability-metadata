/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.procedure.TObjectIntProcedure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Trove's procedure callback subsystem to produce a stock reorder report.
public class StockReorderProcedureTest {
    @Test
    @Timeout(60)
    public void buildsReorderReportFromInventoryEntries() {
        TObjectIntHashMap<String> inventory = new TObjectIntHashMap<>();
        inventory.put("cable", 12);
        inventory.put("adapter", 3);
        inventory.put("charger", 2);
        inventory.put("stand", 8);

        List<String> reorderSkus = new ArrayList<>();
        int[] totalUnits = {0};
        TObjectIntProcedure<String> reportEntry = (sku, units) -> {
            totalUnits[0] += units;
            if (units < 5) {
                reorderSkus.add(sku);
            }
            return true;
        };

        assertTrue(inventory.forEachEntry(reportEntry));
        reorderSkus.sort(String::compareTo);

        assertEquals(List.of("adapter", "charger"), reorderSkus);
        assertEquals(25, totalUnits[0]);

        assertTrue(inventory.retainEntries((sku, units) -> units >= 5));
        assertEquals(2, inventory.size());
        assertTrue(inventory.containsKey("cable"));
        assertTrue(inventory.containsKey("stand"));
        assertFalse(inventory.containsKey("adapter"));
        assertFalse(inventory.containsKey("charger"));
    }
}
