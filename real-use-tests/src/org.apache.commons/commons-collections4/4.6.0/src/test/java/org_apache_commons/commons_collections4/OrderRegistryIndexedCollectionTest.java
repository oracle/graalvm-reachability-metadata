/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.collection.IndexedCollection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the indexed collection subsystem for looking up orders by their application id.
 */
public class OrderRegistryIndexedCollectionTest {
    @Test
    @Timeout(60)
    public void indexesOrdersAndRebuildsTheIndexAfterAnOrderIdChange() {
        Order pending = new Order("order-100", "pending");
        Order shipped = new Order("order-101", "shipped");
        IndexedCollection<String, Order> orders = IndexedCollection.uniqueIndexedCollection(
                new ArrayList<>(List.of(pending, shipped)), Order::id);

        assertEquals(2, orders.size());
        assertSame(pending, orders.get("order-100"));
        assertSame(shipped, orders.get("order-101"));
        assertNull(orders.get("order-999"));

        pending.changeId("order-200");
        orders.reindex();

        assertNull(orders.get("order-100"));
        assertSame(pending, orders.get("order-200"));
        assertTrue(orders.contains(pending));

        assertTrue(orders.remove(shipped));
        assertEquals(List.of(pending), List.copyOf(orders));
    }

    private static final class Order {
        private String id;
        private final String status;

        private Order(String id, String status) {
            this.id = id;
            this.status = status;
        }

        private String id() {
            return id;
        }

        private void changeId(String newId) {
            id = newId;
        }

        @Override
        public String toString() {
            return id + ":" + status;
        }
    }
}
