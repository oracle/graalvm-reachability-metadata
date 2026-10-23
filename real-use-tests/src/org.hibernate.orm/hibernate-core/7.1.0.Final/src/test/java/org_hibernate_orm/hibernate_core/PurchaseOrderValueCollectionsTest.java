/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises Hibernate embeddables and ordered element collections while editing a purchase order.
@Timeout(60)
public class PurchaseOrderValueCollectionsTest {

    @Test
    public void persistsDeliveryDetailsAndEditsOrderedLineItems() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(PurchaseOrder.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:purchase_order_values;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.cache.use_second_level_cache", "false")
                .setProperty("jakarta.persistence.validation.mode", "none");

        try (SessionFactory factory = configuration.buildSessionFactory()) {
            factory.inTransaction(session -> {
                PurchaseOrder order = new PurchaseOrder(501L, new DeliveryAddress("8 Oak Road", "Oxford"));
                order.lines.add(new OrderLine("TENT", 1));
                order.lines.add(new OrderLine("PEG", 8));
                session.persist(order);
            });

            factory.inTransaction(session -> {
                PurchaseOrder order = session.find(PurchaseOrder.class, 501L);
                assertNotNull(order);
                assertEquals("8 Oak Road", order.delivery.street);
                assertEquals("Oxford", order.delivery.city);
                assertEquals(List.of("TENT", "PEG"), order.lines.stream().map(line -> line.sku).toList());
                assertEquals(List.of(1, 8), order.lines.stream().map(line -> line.quantity).toList());

                order.delivery = new DeliveryAddress("24 River Lane", "York");
                order.lines.remove(0);
                order.lines.get(0).quantity = 12;
                order.lines.add(new OrderLine("LANTERN", 2));
            });

            factory.inTransaction(session -> {
                PurchaseOrder order = session.find(PurchaseOrder.class, 501L);
                assertNotNull(order);
                assertEquals("24 River Lane", order.delivery.street);
                assertEquals("York", order.delivery.city);
                assertEquals(List.of("PEG", "LANTERN"), order.lines.stream().map(line -> line.sku).toList());
                assertEquals(List.of(12, 2), order.lines.stream().map(line -> line.quantity).toList());
                session.remove(order);
            });

            try (Session verification = factory.openSession()) {
                assertEquals(0L, verification.createQuery("select count(o) from ValuePurchaseOrder o", Long.class)
                        .getSingleResult());
                assertEquals(0L, verification.createNativeQuery("select count(*) from value_order_line", Long.class)
                        .getSingleResult());
            }
        }
    }

    @Entity(name = "ValuePurchaseOrder")
    @Table(name = "value_purchase_order")
    public static final class PurchaseOrder {
        @Id
        private Long id;

        @Embedded
        private DeliveryAddress delivery;

        @ElementCollection
        @CollectionTable(name = "value_order_line", joinColumns = @JoinColumn(name = "purchase_order_id"))
        @OrderColumn(name = "line_position")
        private List<OrderLine> lines = new ArrayList<>();

        public PurchaseOrder() {
        }

        public PurchaseOrder(Long id, DeliveryAddress delivery) {
            this.id = id;
            this.delivery = delivery;
        }
    }

    @Embeddable
    public static final class DeliveryAddress {
        @Column(name = "delivery_street")
        private String street;

        @Column(name = "delivery_city")
        private String city;

        public DeliveryAddress() {
        }

        public DeliveryAddress(String street, String city) {
            this.street = street;
            this.city = city;
        }
    }

    @Embeddable
    public static final class OrderLine {
        private String sku;

        private int quantity;

        public OrderLine() {
        }

        public OrderLine(String sku, int quantity) {
            this.sku = sku;
            this.quantity = quantity;
        }
    }
}
