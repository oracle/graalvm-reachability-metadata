/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.StatisticsSettings;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.stat.internal.StatisticsImpl;
import org.hibernate.stat.spi.StatisticsFactory;
import org.hibernate.stat.spi.StatisticsImplementor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StatisticsInitiatorTest {

    private static final AtomicInteger BUILT = new AtomicInteger();

    @Test
    void configuredStatisticsFactoryCollectsSessionFactoryStatistics() {
        BUILT.set(0);
        Configuration configuration = SessionFactoryTestSupport.h2Configuration(Coin.class);
        configuration.setProperty(StatisticsSettings.GENERATE_STATISTICS, true);
        configuration.setProperty(StatisticsSettings.STATS_BUILDER, CountingStatisticsFactory.class.getName());

        try (SessionFactory sessionFactory = configuration.buildSessionFactory()) {
            sessionFactory.inTransaction(session -> {
                session.persist(new Coin(1L, "first"));
                session.persist(new Coin(2L, "second"));
            });
            sessionFactory.inTransaction(session -> session.find(Coin.class, 1L));

            assertThat(sessionFactory.getStatistics().getEntityInsertCount()).isEqualTo(2L);
            assertThat(sessionFactory.getStatistics().getTransactionCount()).isEqualTo(2L);
        }
        assertThat(BUILT).hasValue(1);
    }

    public static class CountingStatisticsFactory implements StatisticsFactory {

        @Override
        public StatisticsImplementor buildStatistics(SessionFactoryImplementor sessionFactory) {
            BUILT.incrementAndGet();
            return new StatisticsImpl(sessionFactory);
        }
    }

    @Entity(name = "Coin")
    public static class Coin {

        @Id
        private Long id;

        private String label;

        public Coin() {
        }

        public Coin(Long id, String label) {
            this.id = id;
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }
}
