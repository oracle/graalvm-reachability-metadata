/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.time.LocalDate;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.ColumnResult;
import jakarta.persistence.ConstructorResult;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.SqlResultSetMapping;
import jakarta.persistence.Table;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises Hibernate native SQL constructor result mappings to produce regional sales reports.
@Timeout(60)
public class RegionalSalesNativeReportTest {

    @Test
    public void reportsCompletedSalesWithinTheRequestedPeriodAsDetachedSummaries() {
        LocalDate start = LocalDate.of(2025, 6, 1);
        LocalDate end = LocalDate.of(2025, 7, 1);
        Configuration configuration = new Configuration()
                .addAnnotatedClass(Sale.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:regional_sales_report;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.cache.use_second_level_cache", "false")
                .setProperty("jakarta.persistence.validation.mode", "none");

        try (SessionFactory factory = configuration.buildSessionFactory()) {
            factory.inTransaction(session -> {
                session.persist(new Sale(1L, "North", start, 1200L, true));
                session.persist(new Sale(2L, "North", start.plusDays(14), 2800L, true));
                session.persist(new Sale(3L, "South", end.minusDays(1), 3500L, true));
                session.persist(new Sale(4L, "North", start.plusDays(19), 900L, false));
                session.persist(new Sale(5L, "North", start.minusDays(1), 5000L, true));
                session.persist(new Sale(6L, "South", end, 7000L, true));
            });

            // The reporting layer consumes immutable DTOs after the session has closed.
            List<RegionSales> monthly = factory.fromTransaction(session -> report(session, start, end));
            assertEquals(List.of(new RegionSales("North", 2L, 4000L),
                    new RegionSales("South", 1L, 3500L)), monthly);

            List<RegionSales> firstFortnight = factory.fromTransaction(
                    session -> report(session, start, start.plusDays(14)));
            assertEquals(List.of(new RegionSales("North", 1L, 1200L)), firstFortnight);
            assertEquals(List.of(), factory.fromTransaction(
                    session -> report(session, end.plusMonths(1), end.plusMonths(2))));

            factory.inTransaction(session -> {
                Sale refunded = session.find(Sale.class, 2L);
                assertNotNull(refunded);
                refunded.completed = false;
            });

            List<RegionSales> revised = factory.fromTransaction(session -> report(session, start, end));
            assertEquals(List.of(new RegionSales("North", 1L, 1200L),
                    new RegionSales("South", 1L, 3500L)), revised);
            assertEquals(4000L, monthly.get(0).totalCents());
        }
    }

    private static List<RegionSales> report(Session session, LocalDate start, LocalDate end) {
        return session.createNativeQuery("""
                select region as region_name, count(*) as sale_count, sum(amount_cents) as total_cents
                from regional_report_sale
                where completed = true and sold_on >= :start and sold_on < :end
                group by region
                order by region
                """, "RegionalSalesSummary", RegionSales.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
    }

    public record RegionSales(String region, Long saleCount, Long totalCents) {
    }

    @Entity(name = "RegionalReportSale")
    @Table(name = "regional_report_sale")
    @SqlResultSetMapping(name = "RegionalSalesSummary", classes = @ConstructorResult(
            targetClass = RegionSales.class,
            columns = {
                @ColumnResult(name = "region_name", type = String.class),
                @ColumnResult(name = "sale_count", type = Long.class),
                @ColumnResult(name = "total_cents", type = Long.class)
            }))
    public static final class Sale {
        @Id
        private Long id;

        @Column(nullable = false)
        private String region;

        @Column(name = "sold_on", nullable = false)
        private LocalDate soldOn;

        @Column(name = "amount_cents", nullable = false)
        private long amountCents;

        private boolean completed;

        public Sale() {
        }

        public Sale(Long id, String region, LocalDate soldOn, long amountCents, boolean completed) {
            this.id = id;
            this.region = region;
            this.soldOn = soldOn;
            this.amountCents = amountCents;
            this.completed = completed;
        }
    }
}
