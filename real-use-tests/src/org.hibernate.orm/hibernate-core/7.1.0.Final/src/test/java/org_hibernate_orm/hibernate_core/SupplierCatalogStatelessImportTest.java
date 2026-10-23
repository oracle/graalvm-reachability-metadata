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

import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/// Exercises Hibernate stateless sessions to import supplier catalog additions, price changes, and withdrawals.
@Timeout(60)
public class SupplierCatalogStatelessImportTest {

    @Test
    public void appliesSupplierFeedChangesAndPublishesTheUpdatedCatalog() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(SupplierProduct.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:supplier_stateless_import;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.cache.use_second_level_cache", "false")
                .setProperty("jakarta.persistence.validation.mode", "none");

        try (SessionFactory factory = configuration.buildSessionFactory()) {
            try (StatelessSession importer = factory.openStatelessSession()) {
                Transaction transaction = importer.beginTransaction();
                try {
                    List<SupplierProduct> initialFeed = List.of(
                            new SupplierProduct("MUG", "Enamel mug", 1800L),
                            new SupplierProduct("STOVE", "Camp stove", 6500L),
                            new SupplierProduct("LAMP", "Oil lamp", 3200L));
                    for (SupplierProduct product : initialFeed) {
                        importer.insert(product);
                    }
                    transaction.commit();
                } finally {
                    if (transaction.isActive()) {
                        transaction.rollback();
                    }
                }
            }

            try (StatelessSession importer = factory.openStatelessSession()) {
                Transaction transaction = importer.beginTransaction();
                try {
                    SupplierProduct revised = importer.get(SupplierProduct.class, "MUG");
                    assertNotNull(revised);
                    assertEquals("Enamel mug", revised.name);
                    assertEquals(1800L, revised.priceCents);
                    revised.name = "Large enamel mug";
                    revised.priceCents = 2100L;
                    // Stateless imports explicitly write changes instead of relying on dirty checking.
                    importer.update(revised);

                    SupplierProduct withdrawn = importer.get(SupplierProduct.class, "LAMP");
                    assertNotNull(withdrawn);
                    importer.delete(withdrawn);
                    importer.insert(new SupplierProduct("TORCH", "Rechargeable torch", 2700L));
                    transaction.commit();
                } finally {
                    if (transaction.isActive()) {
                        transaction.rollback();
                    }
                }
            }

            factory.inTransaction(session -> {
                List<SupplierProduct> catalog = session.createQuery(
                        "from ImportedSupplierProduct p order by p.sku", SupplierProduct.class).getResultList();
                assertEquals(List.of("MUG", "STOVE", "TORCH"),
                        catalog.stream().map(product -> product.sku).toList());
                assertEquals(List.of("Large enamel mug", "Camp stove", "Rechargeable torch"),
                        catalog.stream().map(product -> product.name).toList());
                assertEquals(List.of(2100L, 6500L, 2700L),
                        catalog.stream().map(product -> product.priceCents).toList());
                assertNull(session.find(SupplierProduct.class, "LAMP"));
            });
        }
    }

    @Entity(name = "ImportedSupplierProduct")
    @Table(name = "imported_supplier_product")
    public static final class SupplierProduct {
        @Id
        private String sku;

        @Column(nullable = false)
        private String name;

        private long priceCents;

        public SupplierProduct() {
        }

        public SupplierProduct(String sku, String name, long priceCents) {
            this.sku = sku;
            this.name = name;
            this.priceCents = priceCents;
        }
    }
}
