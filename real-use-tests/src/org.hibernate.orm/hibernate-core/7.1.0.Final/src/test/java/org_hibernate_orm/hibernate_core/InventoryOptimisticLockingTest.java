/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises Hibernate optimistic locking to prevent lost inventory updates.
@Timeout(60)
public class InventoryOptimisticLockingTest {

    @Test
    public void rejectsAStaleStockReservationWithoutLosingTheCommittedReservation() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(StockItem.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:optimistic_inventory;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.cache.use_second_level_cache", "false")
                .setProperty("jakarta.persistence.validation.mode", "none");

        try (SessionFactory factory = configuration.buildSessionFactory()) {
            factory.inTransaction(session -> session.persist(new StockItem(101L, "Trail boots", 12)));

            long initialVersion;
            try (Session checkout = factory.openSession(); Session staleCheckout = factory.openSession()) {
                checkout.beginTransaction();
                staleCheckout.beginTransaction();
                try {
                    StockItem current = checkout.find(StockItem.class, 101L);
                    StockItem stale = staleCheckout.find(StockItem.class, 101L);
                    assertNotNull(current);
                    assertNotNull(stale);
                    initialVersion = current.version;
                    assertEquals(initialVersion, stale.version);
                    assertEquals(12, stale.available);

                    current.available -= 3;
                    checkout.getTransaction().commit();
                    assertEquals(initialVersion + 1, current.version);

                    stale.available -= 2;
                    assertThrows(OptimisticLockException.class, staleCheckout::flush);
                } finally {
                    if (checkout.getTransaction().isActive()) {
                        checkout.getTransaction().rollback();
                    }
                    if (staleCheckout.getTransaction().isActive()) {
                        staleCheckout.getTransaction().rollback();
                    }
                }
            }

            try (Session verification = factory.openSession()) {
                StockItem persisted = verification.find(StockItem.class, 101L);
                assertNotNull(persisted);
                assertEquals("Trail boots", persisted.name);
                assertEquals(9, persisted.available);
                assertEquals(initialVersion + 1, persisted.version);
            }
        }
    }

    @Entity(name = "InventoryStockItem")
    @Table(name = "inventory_stock_item")
    public static final class StockItem {
        @Id
        private Long id;

        private String name;

        private int available;

        @Version
        private long version;

        public StockItem() {
        }

        public StockItem(Long id, String name, int available) {
            this.id = id;
            this.name = name;
            this.available = available;
        }
    }
}
