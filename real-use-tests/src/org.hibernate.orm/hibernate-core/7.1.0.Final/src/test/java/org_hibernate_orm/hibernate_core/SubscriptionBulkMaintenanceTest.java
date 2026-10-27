/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.time.LocalDate;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/// Exercises Hibernate HQL bulk mutations to expire trial subscriptions and purge expired retention records.
@Timeout(60)
public class SubscriptionBulkMaintenanceTest {

    @Test
    public void expiresOnlyElapsedTrialsAndPurgesOnlyExpiredSubscriptionsOutsideRetention() {
        LocalDate processingDate = LocalDate.of(2025, 6, 15);
        LocalDate retentionCutoff = processingDate.minusDays(90);
        Configuration configuration = new Configuration()
                .addAnnotatedClass(Subscription.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:subscription_bulk_maintenance;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.cache.use_second_level_cache", "false")
                .setProperty("jakarta.persistence.validation.mode", "none");

        try (SessionFactory factory = configuration.buildSessionFactory()) {
            factory.inTransaction(session -> {
                session.persist(new Subscription(1L, "Ada", Status.TRIAL, processingDate.minusDays(1)));
                session.persist(new Subscription(2L, "Ben", Status.TRIAL, processingDate.minusDays(7)));
                session.persist(new Subscription(3L, "Cora", Status.TRIAL, processingDate));
                session.persist(new Subscription(4L, "Dan", Status.TRIAL, processingDate.plusDays(1)));
                session.persist(new Subscription(5L, "Eve", Status.PAID, retentionCutoff.minusDays(1)));
                session.persist(new Subscription(6L, "Finn", Status.EXPIRED, retentionCutoff.minusDays(1)));
                session.persist(new Subscription(7L, "Gia", Status.EXPIRED, retentionCutoff));
            });

            // Maintenance uses its own transaction, with no managed subscription instances to become stale.
            factory.inTransaction(session -> {
                assertEquals(2, expireTrials(session, processingDate));
                assertEquals(1, purgeExpired(session, retentionCutoff));
            });

            factory.inTransaction(session -> {
                assertEquals(List.of("Ada", "Ben", "Cora", "Dan", "Eve", "Gia"), session.createQuery(
                        "select s.customer from MaintenanceSubscription s order by s.id", String.class)
                        .getResultList());
                assertEquals(List.of(Status.EXPIRED, Status.EXPIRED, Status.TRIAL,
                        Status.TRIAL, Status.PAID, Status.EXPIRED), session.createQuery(
                                "select s.status from MaintenanceSubscription s order by s.id", Status.class)
                        .getResultList());
                assertNull(session.find(Subscription.class, 6L));

                Subscription expired = session.find(Subscription.class, 1L);
                assertNotNull(expired);
                assertEquals(processingDate.minusDays(1), expired.trialEndsOn);
                Subscription onBoundary = session.find(Subscription.class, 3L);
                assertNotNull(onBoundary);
                assertEquals(processingDate, onBoundary.trialEndsOn);
                Subscription retained = session.find(Subscription.class, 7L);
                assertNotNull(retained);
                assertEquals(retentionCutoff, retained.trialEndsOn);
            });

            // Re-running the job for the same business date must not expire or delete any additional rows.
            factory.inTransaction(session -> {
                assertEquals(0, expireTrials(session, processingDate));
                assertEquals(0, purgeExpired(session, retentionCutoff));
            });
            factory.inTransaction(session -> assertEquals(6L, session.createQuery(
                    "select count(s) from MaintenanceSubscription s", Long.class).getSingleResult()));
        }
    }

    private static int expireTrials(Session session, LocalDate processingDate) {
        return session.createMutationQuery("""
                update MaintenanceSubscription s
                set s.status = :expired
                where s.status = :trial and s.trialEndsOn < :processingDate
                """)
                .setParameter("expired", Status.EXPIRED)
                .setParameter("trial", Status.TRIAL)
                .setParameter("processingDate", processingDate)
                .executeUpdate();
    }

    private static int purgeExpired(Session session, LocalDate retentionCutoff) {
        return session.createMutationQuery("""
                delete from MaintenanceSubscription s
                where s.status = :expired and s.trialEndsOn < :retentionCutoff
                """)
                .setParameter("expired", Status.EXPIRED)
                .setParameter("retentionCutoff", retentionCutoff)
                .executeUpdate();
    }

    public enum Status {
        TRIAL,
        PAID,
        EXPIRED
    }

    @Entity(name = "MaintenanceSubscription")
    @Table(name = "maintenance_subscription")
    public static final class Subscription {
        @Id
        private Long id;

        private String customer;

        @Enumerated(EnumType.STRING)
        private Status status;

        private LocalDate trialEndsOn;

        public Subscription() {
        }

        public Subscription(Long id, String customer, Status status, LocalDate trialEndsOn) {
            this.id = id;
            this.customer = customer;
            this.status = status;
            this.trialEndsOn = trialEndsOn;
        }
    }
}
