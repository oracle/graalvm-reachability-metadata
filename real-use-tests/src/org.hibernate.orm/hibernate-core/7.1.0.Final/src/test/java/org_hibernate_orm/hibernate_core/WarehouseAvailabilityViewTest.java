/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises Hibernate immutable subselect mappings to publish warehouse availability from stock movements.
@Timeout(60)
public class WarehouseAvailabilityViewTest {

    @Test
    public void reportsLowStockAndSynchronizesPendingReceiptsAndDispatchesBeforeQuerying() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(StockMovement.class)
                .addAnnotatedClass(Availability.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:warehouse_availability;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.cache.use_second_level_cache", "false")
                .setProperty("jakarta.persistence.validation.mode", "none");

        try (SessionFactory factory = configuration.buildSessionFactory()) {
            factory.inTransaction(session -> {
                session.persist(new StockMovement(1L, "BOOTS", 12));
                session.persist(new StockMovement(2L, "BOOTS", -4));
                session.persist(new StockMovement(3L, "TENT", 5));
                session.persist(new StockMovement(4L, "TENT", -4));
                session.persist(new StockMovement(5L, "LAMP", 3));
            });

            List<Availability> initial = factory.fromTransaction(WarehouseAvailabilityViewTest::lowStock);
            assertEquals(List.of("LAMP", "TENT"), initial.stream().map(row -> row.sku).toList());
            assertEquals(List.of(3L, 1L), initial.stream().map(row -> row.availableUnits).toList());

            factory.inTransaction(session -> {
                session.persist(new StockMovement(6L, "BOOTS", -6));
                session.persist(new StockMovement(7L, "TENT", 10));

                // Synchronizing the mapped source table makes this query flush the pending movements.
                List<Availability> revised = lowStock(session);
                assertEquals(List.of("BOOTS", "LAMP"), revised.stream().map(row -> row.sku).toList());
                assertEquals(List.of(2L, 3L), revised.stream().map(row -> row.availableUnits).toList());
            });

            factory.inTransaction(session -> {
                Availability replenished = session.find(Availability.class, "TENT");
                assertNotNull(replenished);
                assertEquals(11L, replenished.availableUnits);
                assertEquals(7L, session.createSelectionQuery(
                        "select count(m) from WarehouseStockMovement m", Long.class).getSingleResult());
                assertEquals(16L, session.createSelectionQuery(
                        "select sum(a.availableUnits) from WarehouseAvailability a", Long.class).getSingleResult());
            });
        }
    }

    private static List<Availability> lowStock(Session session) {
        return session.createSelectionQuery("""
                from WarehouseAvailability a
                where a.availableUnits < :reorderPoint
                order by a.sku
                """, Availability.class)
                .setParameter("reorderPoint", 5L)
                .getResultList();
    }

    @Entity(name = "WarehouseStockMovement")
    @Table(name = "warehouse_stock_movement")
    public static final class StockMovement {
        @Id
        private Long id;

        @Column(nullable = false)
        private String sku;

        @Column(name = "quantity_delta", nullable = false)
        private long quantityDelta;

        public StockMovement() {
        }

        public StockMovement(Long id, String sku, long quantityDelta) {
            this.id = id;
            this.sku = sku;
            this.quantityDelta = quantityDelta;
        }
    }

    @Entity(name = "WarehouseAvailability")
    @Immutable
    @Subselect("""
            select sku, sum(quantity_delta) as available_units
            from warehouse_stock_movement
            group by sku
            """)
    @Synchronize("warehouse_stock_movement")
    public static final class Availability {
        @Id
        private String sku;

        @Column(name = "available_units")
        private long availableUnits;

        public Availability() {
        }
    }
}
