/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ehcache.ehcache;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheEventListenerConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.event.CacheEventListener;
import org.ehcache.event.EventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Ehcache's cache event notification subsystem for an inventory audit trail.
 * A shop records stock arrivals, successful checkout adjustments, and retired products in order.
 */
public class InventoryEventAuditTest {
    @Test
    @Timeout(60)
    public void recordsOnlyCommittedInventoryChangesInOrder() {
        List<StockChange> auditTrail = new ArrayList<>();
        CacheEventListener<String, Integer> auditor = event -> auditTrail.add(new StockChange(
                event.getType(), event.getKey(), event.getOldValue(), event.getNewValue()));
        CacheEventListenerConfigurationBuilder notifications = CacheEventListenerConfigurationBuilder
                .newEventListenerConfiguration(auditor, EventType.CREATED, EventType.UPDATED, EventType.REMOVED)
                .ordered()
                .synchronous();

        try (CacheManager manager = CacheManagerBuilder.newCacheManagerBuilder()
                .withCache("inventory", CacheConfigurationBuilder.newCacheConfigurationBuilder(
                        String.class, Integer.class, ResourcePoolsBuilder.heap(100))
                        .withService(notifications))
                .build(true)) {
            Cache<String, Integer> inventory = manager.getCache("inventory", String.class, Integer.class);

            inventory.put("coffee", 20);
            assertEquals(List.of(new StockChange(EventType.CREATED, "coffee", null, 20)), auditTrail);

            // A checkout commits only if the stock count still matches the quantity it read.
            assertTrue(inventory.replace("coffee", 20, 17));
            assertFalse(inventory.replace("coffee", 20, 16));
            assertEquals(Integer.valueOf(17), inventory.get("coffee"));
            assertEquals(List.of(
                    new StockChange(EventType.CREATED, "coffee", null, 20),
                    new StockChange(EventType.UPDATED, "coffee", 20, 17)), auditTrail);

            inventory.put("tea", 8);
            inventory.remove("tea");
            assertNull(inventory.get("tea"));
            assertEquals(Integer.valueOf(17), inventory.get("coffee"));
            assertEquals(List.of(
                    new StockChange(EventType.CREATED, "coffee", null, 20),
                    new StockChange(EventType.UPDATED, "coffee", 20, 17),
                    new StockChange(EventType.CREATED, "tea", null, 8),
                    new StockChange(EventType.REMOVED, "tea", 8, null)), auditTrail);
        }
    }

    private record StockChange(EventType type, String sku, Integer previousStock, Integer currentStock) {
    }
}
