/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.stat.internal.StatisticsImpl;
import org.hibernate.stat.internal.StatisticsInitiator;
import org.hibernate.stat.spi.StatisticsFactory;
import org.hibernate.stat.spi.StatisticsImplementor;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class StatisticsInitiatorTest {

    @Test
    public void instantiatesTheConfiguredStatisticsFactory() {
        RecordingStatisticsFactory.instances.set(0);
        Configuration configuration = new Configuration()
                .setProperty(AvailableSettings.URL, "jdbc:h2:mem:statistics-initiator")
                .setProperty(AvailableSettings.DRIVER, "org.h2.Driver")
                .setProperty(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .setProperty(AvailableSettings.GENERATE_STATISTICS, "true")
                .setProperty(
                        StatisticsInitiator.STATS_BUILDER,
                        RecordingStatisticsFactory.class.getName()
                )
                .setProperty(AvailableSettings.JAKARTA_VALIDATION_MODE, "none");

        try (SessionFactory factory = configuration.buildSessionFactory()) {
            assertThat(factory.getStatistics().isStatisticsEnabled()).isTrue();
            assertThat(RecordingStatisticsFactory.instances).hasValue(1);
        }
    }

    public static class RecordingStatisticsFactory implements StatisticsFactory {
        private static final AtomicInteger instances = new AtomicInteger();

        public RecordingStatisticsFactory() {
            instances.incrementAndGet();
        }

        @Override
        public StatisticsImplementor buildStatistics(SessionFactoryImplementor sessionFactory) {
            return new StatisticsImpl(sessionFactory);
        }
    }
}
