/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.Bag;
import org.apache.commons.collections4.bag.HashBag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the bag subsystem for aggregating demand for warehouse products.
 */
public class InventoryDemandBagTest {
    @Test
    @Timeout(60)
    public void aggregatesDemandAndRemovesOneFulfilledUnit() {
        Bag<String> demand = new HashBag<>();
        demand.add("coffee", 3);
        demand.add("tea", 2);
        demand.add("coffee");

        assertEquals(6, demand.size());
        assertEquals(4, demand.getCount("coffee"));
        assertEquals(2, demand.getCount("tea"));
        assertTrue(demand.contains("coffee"));
        assertFalse(demand.contains("cocoa"));

        assertTrue(demand.remove("coffee", 1));
        assertEquals(3, demand.getCount("coffee"));
        assertEquals(5, demand.size());

        assertTrue(demand.remove("tea", 2));
        assertEquals(0, demand.getCount("tea"));
        assertFalse(demand.contains("tea"));
    }
}
