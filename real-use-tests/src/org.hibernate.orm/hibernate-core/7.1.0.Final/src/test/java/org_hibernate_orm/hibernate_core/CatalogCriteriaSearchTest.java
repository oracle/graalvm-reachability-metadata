/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Hibernate Criteria API searches with optional catalog filters, pagination, and total counts.
@Timeout(60)
public class CatalogCriteriaSearchTest {

    @Test
    public void searchesAvailableProductsInStablePriceOrderAndCountsAllMatchingRows() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(Product.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:catalog_criteria_search;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.cache.use_second_level_cache", "false")
                .setProperty("jakarta.persistence.validation.mode", "none");

        try (SessionFactory factory = configuration.buildSessionFactory()) {
            factory.inTransaction(session -> {
                session.persist(new Product(1L, "Camp mug", "camping", 2500L, 10));
                session.persist(new Product(2L, "Trail mug", "camping", 2500L, 5));
                session.persist(new Product(3L, "Compact stove", "camping", 4500L, 3));
                session.persist(new Product(4L, "Tent", "camping", 9000L, 2));
                session.persist(new Product(5L, "Gas canister", "camping", 1500L, 0));
                session.persist(new Product(6L, "Hiking socks", "footwear", 2000L, 8));
            });

            factory.inTransaction(session -> {
                SearchPage first = search(session, "camping", 5000L, true, 0, 2);
                assertEquals(List.of("Camp mug", "Trail mug"), first.names());
                assertEquals(3L, first.total());

                SearchPage second = search(session, "camping", 5000L, true, 2, 2);
                assertEquals(List.of("Compact stove"), second.names());
                assertEquals(3L, second.total());

                SearchPage beyondLast = search(session, "camping", 5000L, true, 4, 2);
                assertEquals(List.of(), beyondLast.names());
                assertEquals(3L, beyondLast.total());

                SearchPage includingSoldOut = search(session, "camping", 5000L, false, 0, 10);
                assertEquals(List.of("Gas canister", "Camp mug", "Trail mug", "Compact stove"),
                        includingSoldOut.names());
                assertEquals(4L, includingSoldOut.total());

                SearchPage withoutPriceLimit = search(session, "camping", null, true, 0, 10);
                assertEquals(List.of("Camp mug", "Trail mug", "Compact stove", "Tent"), withoutPriceLimit.names());
                assertEquals(4L, withoutPriceLimit.total());

                SearchPage otherCategory = search(session, "footwear", null, true, 0, 10);
                assertEquals(List.of("Hiking socks"), otherCategory.names());
                assertEquals(1L, otherCategory.total());
            });
        }
    }

    private static SearchPage search(Session session, String category, Long maximumPriceCents,
            boolean availableOnly, int offset, int pageSize) {
        HibernateCriteriaBuilder builder = session.getCriteriaBuilder();
        JpaCriteriaQuery<Product> criteria = builder.createQuery(Product.class);
        Root<Product> product = criteria.from(Product.class);
        List<Predicate> conditions = new ArrayList<>();
        conditions.add(builder.equal(product.get("category"), category));
        if (maximumPriceCents != null) {
            conditions.add(builder.le(product.<Long>get("priceCents"), maximumPriceCents));
        }
        if (availableOnly) {
            conditions.add(builder.gt(product.<Integer>get("available"), 0));
        }
        criteria.select(product)
                .where(conditions.toArray(Predicate[]::new))
                .orderBy(builder.asc(product.get("priceCents")), builder.asc(product.get("id")));

        long total = session.createQuery(criteria.createCountQuery()).getSingleResult();
        List<Product> matches = session.createQuery(criteria)
                .setFirstResult(offset)
                .setMaxResults(pageSize)
                .getResultList();
        return new SearchPage(matches.stream().map(match -> match.name).toList(), total);
    }

    private record SearchPage(List<String> names, long total) {
    }

    @Entity(name = "CriteriaCatalogProduct")
    @Table(name = "criteria_catalog_product")
    public static final class Product {
        @Id
        private Long id;

        private String name;

        private String category;

        private long priceCents;

        private int available;

        public Product() {
        }

        public Product(Long id, String name, String category, long priceCents, int available) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.priceCents = priceCents;
            this.available = available;
        }
    }
}
