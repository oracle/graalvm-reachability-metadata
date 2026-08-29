/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.EmptyInterceptor;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.dialect.Dialect;
import org.hibernate.query.spi.DomainQueryExecutionContext;
import org.hibernate.query.sqm.internal.DomainParameterXref;
import org.hibernate.query.sqm.mutation.spi.SqmMultiTableInsertStrategy;
import org.hibernate.query.sqm.mutation.spi.SqmMultiTableMutationStrategy;
import org.hibernate.query.sqm.tree.delete.SqmDeleteStatement;
import org.hibernate.query.sqm.tree.insert.SqmInsertStatement;
import org.hibernate.query.sqm.tree.update.SqmUpdateStatement;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionFactoryOptionsBuilderTest {

    @Test
    public void createsAConfiguredSessionScopedInterceptor() {
        RecordingInterceptor.instances.set(0);
        Configuration configuration = new Configuration()
                .setProperty(AvailableSettings.URL, "jdbc:h2:mem:session-options")
                .setProperty(AvailableSettings.DRIVER, "org.h2.Driver")
                .setProperty(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .setProperty(
                        AvailableSettings.SESSION_SCOPED_INTERCEPTOR,
                        RecordingInterceptor.class.getName()
                );

        try (SessionFactory factory = configuration.buildSessionFactory();
                Session session = factory.openSession()) {
            assertThat(session.isOpen()).isTrue();
            assertThat(RecordingInterceptor.instances).hasValue(1);
        }
    }

    @Test
    public void createsConfiguredMultiTableStrategiesThroughSupportedConstructors() {
        EmptyMutationStrategy.instances.set(0);
        EmptyInsertStrategy.instances.set(0);
        DialectMutationStrategy.instances.set(0);
        DialectInsertStrategy.instances.set(0);

        try (SessionFactory ignored = strategyFactory(
                EmptyMutationStrategy.class,
                EmptyInsertStrategy.class,
                "empty-strategies")) {
            assertThat(EmptyMutationStrategy.instances).hasValue(1);
            assertThat(EmptyInsertStrategy.instances).hasValue(1);
        }
        try (SessionFactory ignored = strategyFactory(
                DialectMutationStrategy.class,
                DialectInsertStrategy.class,
                "dialect-strategies")) {
            assertThat(DialectMutationStrategy.instances).hasValue(1);
            assertThat(DialectInsertStrategy.instances).hasValue(1);
        }
    }

    @Test
    public void createsAnInterceptorAppliedThroughTheSessionFactoryBuilder() {
        BuilderInterceptor.instances.set(0);
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.URL, "jdbc:h2:mem:builder-interceptor")
                .applySetting(AvailableSettings.DRIVER, "org.h2.Driver")
                .applySetting(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .build();
        try {
            Metadata metadata = new MetadataSources(registry).buildMetadata();
            try (SessionFactory factory = metadata.getSessionFactoryBuilder()
                    .applyStatelessInterceptor(BuilderInterceptor.class)
                    .build();
                    Session session = factory.openSession()) {
                assertThat(session.isOpen()).isTrue();
                assertThat(BuilderInterceptor.instances).hasValue(1);
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private static SessionFactory strategyFactory(
            Class<? extends SqmMultiTableMutationStrategy> mutationStrategy,
            Class<? extends SqmMultiTableInsertStrategy> insertStrategy,
            String databaseName) {
        return new Configuration()
                .setProperty(AvailableSettings.URL, "jdbc:h2:mem:" + databaseName)
                .setProperty(AvailableSettings.DRIVER, "org.h2.Driver")
                .setProperty(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .setProperty(
                        AvailableSettings.QUERY_MULTI_TABLE_MUTATION_STRATEGY,
                        mutationStrategy.getName()
                )
                .setProperty(
                        AvailableSettings.QUERY_MULTI_TABLE_INSERT_STRATEGY,
                        insertStrategy.getName()
                )
                .buildSessionFactory();
    }

    public static class RecordingInterceptor extends EmptyInterceptor {
        private static final long serialVersionUID = 1L;
        private static final AtomicInteger instances = new AtomicInteger();

        public RecordingInterceptor() {
            instances.incrementAndGet();
        }
    }

    public static class BuilderInterceptor extends EmptyInterceptor {
        private static final long serialVersionUID = 1L;
        private static final AtomicInteger instances = new AtomicInteger();

        public BuilderInterceptor() {
            instances.incrementAndGet();
        }
    }

    public static class EmptyMutationStrategy implements SqmMultiTableMutationStrategy {
        private static final AtomicInteger instances = new AtomicInteger();

        public EmptyMutationStrategy() {
            instances.incrementAndGet();
        }

        @Override
        public int executeUpdate(
                SqmUpdateStatement<?> statement,
                DomainParameterXref parameters,
                DomainQueryExecutionContext context) {
            return 0;
        }

        @Override
        public int executeDelete(
                SqmDeleteStatement<?> statement,
                DomainParameterXref parameters,
                DomainQueryExecutionContext context) {
            return 0;
        }
    }

    public static class DialectMutationStrategy extends EmptyMutationStrategy {
        private static final AtomicInteger instances = new AtomicInteger();

        public DialectMutationStrategy(Dialect dialect) {
            instances.incrementAndGet();
        }
    }

    public static class EmptyInsertStrategy implements SqmMultiTableInsertStrategy {
        private static final AtomicInteger instances = new AtomicInteger();

        public EmptyInsertStrategy() {
            instances.incrementAndGet();
        }

        @Override
        public int executeInsert(
                SqmInsertStatement<?> statement,
                DomainParameterXref parameters,
                DomainQueryExecutionContext context) {
            return 0;
        }
    }

    public static class DialectInsertStrategy extends EmptyInsertStrategy {
        private static final AtomicInteger instances = new AtomicInteger();

        public DialectInsertStrategy(Dialect dialect) {
            instances.incrementAndGet();
        }
    }
}
