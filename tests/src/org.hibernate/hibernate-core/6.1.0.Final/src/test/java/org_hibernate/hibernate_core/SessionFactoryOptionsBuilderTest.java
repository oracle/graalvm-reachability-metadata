/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.Interceptor;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.Dialect;
import org.hibernate.query.spi.DomainQueryExecutionContext;
import org.hibernate.query.sqm.internal.DomainParameterXref;
import org.hibernate.query.sqm.mutation.spi.SqmMultiTableInsertStrategy;
import org.hibernate.query.sqm.mutation.spi.SqmMultiTableMutationStrategy;
import org.hibernate.query.sqm.tree.delete.SqmDeleteStatement;
import org.hibernate.query.sqm.tree.insert.SqmInsertStatement;
import org.hibernate.query.sqm.tree.update.SqmUpdateStatement;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionFactoryOptionsBuilderTest {

    private static final AtomicInteger dialectMutationStrategyCreations = new AtomicInteger();
    private static final AtomicInteger emptyMutationStrategyCreations = new AtomicInteger();
    private static final AtomicInteger dialectInsertStrategyCreations = new AtomicInteger();
    private static final AtomicInteger emptyInsertStrategyCreations = new AtomicInteger();
    private static final AtomicInteger configuredInterceptorCreations = new AtomicInteger();
    private static final AtomicInteger builderAppliedInterceptorCreations = new AtomicInteger();

    @Test
    public void shouldInstantiateConfiguredSqmStrategiesUsingDialectConstructors() {
        dialectMutationStrategyCreations.set(0);
        dialectInsertStrategyCreations.set(0);

        try (SessionFactory sessionFactory = buildSessionFactory(settings(
                Map.<String, Object>of(
                        AvailableSettings.QUERY_MULTI_TABLE_MUTATION_STRATEGY,
                        DialectConstructorMutationStrategy.class.getName(),
                        AvailableSettings.QUERY_MULTI_TABLE_INSERT_STRATEGY,
                        DialectConstructorInsertStrategy.class.getName()
                )
        ))) {
            assertThat(sessionFactory).isNotNull();
        }

        assertThat(dialectMutationStrategyCreations.get()).isEqualTo(1);
        assertThat(dialectInsertStrategyCreations.get()).isEqualTo(1);
    }

    @Test
    public void shouldInstantiateConfiguredSqmStrategiesUsingEmptyConstructors() {
        emptyMutationStrategyCreations.set(0);
        emptyInsertStrategyCreations.set(0);

        try (SessionFactory sessionFactory = buildSessionFactory(settings(
                Map.<String, Object>of(
                        AvailableSettings.QUERY_MULTI_TABLE_MUTATION_STRATEGY,
                        EmptyConstructorMutationStrategy.class.getName(),
                        AvailableSettings.QUERY_MULTI_TABLE_INSERT_STRATEGY,
                        EmptyConstructorInsertStrategy.class.getName()
                )
        ))) {
            assertThat(sessionFactory).isNotNull();
        }

        assertThat(emptyMutationStrategyCreations.get()).isEqualTo(1);
        assertThat(emptyInsertStrategyCreations.get()).isEqualTo(1);
    }

    @Test
    public void shouldCreateSessionScopedInterceptorConfiguredAsClass() {
        configuredInterceptorCreations.set(0);

        try (SessionFactory sessionFactory = buildSessionFactory(settings(
                Map.<String, Object>of(
                        AvailableSettings.SESSION_SCOPED_INTERCEPTOR,
                        ConfiguredSessionScopedInterceptor.class
                )
        ));
                Session session = sessionFactory.openSession()) {
            assertThat(session.isOpen()).isTrue();
        }

        assertThat(configuredInterceptorCreations.get()).isEqualTo(1);
    }

    @Test
    public void shouldCreateStatelessInterceptorAppliedThroughSessionFactoryBuilder() {
        builderAppliedInterceptorCreations.set(0);
        StandardServiceRegistry serviceRegistry = createServiceRegistry(settings(Map.<String, Object>of()));

        try {
            Metadata metadata = new MetadataSources(serviceRegistry).buildMetadata();
            try (SessionFactory sessionFactory = metadata.getSessionFactoryBuilder()
                    .applyStatelessInterceptor(BuilderAppliedStatelessInterceptor.class)
                    .build();
                    Session session = sessionFactory.openSession()) {
                assertThat(session.isOpen()).isTrue();
            }
        }
        finally {
            StandardServiceRegistryBuilder.destroy(serviceRegistry);
        }

        assertThat(builderAppliedInterceptorCreations.get()).isEqualTo(1);
    }

    private static SessionFactory buildSessionFactory(Map<String, Object> settings) {
        StandardServiceRegistry serviceRegistry = createServiceRegistry(settings);
        try {
            return new MetadataSources(serviceRegistry).buildMetadata().buildSessionFactory();
        }
        catch (RuntimeException | Error e) {
            StandardServiceRegistryBuilder.destroy(serviceRegistry);
            throw e;
        }
    }

    private static StandardServiceRegistry createServiceRegistry(Map<String, Object> settings) {
        return new StandardServiceRegistryBuilder()
                .applySettings(settings)
                .build();
    }

    private static Map<String, Object> settings(Map<String, Object> customSettings) {
        Map<String, Object> settings = new HashMap<>();
        settings.put(AvailableSettings.DRIVER, "org.h2.Driver");
        settings.put(AvailableSettings.URL, "jdbc:h2:mem:session-factory-options-builder;DB_CLOSE_DELAY=-1");
        settings.put(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect");
        settings.put(AvailableSettings.HBM2DDL_AUTO, "none");
        settings.putAll(customSettings);
        return settings;
    }

    public static class ConfiguredSessionScopedInterceptor implements Interceptor {
        public ConfiguredSessionScopedInterceptor() {
            configuredInterceptorCreations.incrementAndGet();
        }
    }

    public static class BuilderAppliedStatelessInterceptor implements Interceptor {
        public BuilderAppliedStatelessInterceptor() {
            builderAppliedInterceptorCreations.incrementAndGet();
        }
    }

    public static class DialectConstructorMutationStrategy extends BaseMutationStrategy {
        public DialectConstructorMutationStrategy(Dialect dialect) {
            assertThat(dialect).isNotNull();
            dialectMutationStrategyCreations.incrementAndGet();
        }
    }

    public static class EmptyConstructorMutationStrategy extends BaseMutationStrategy {
        public EmptyConstructorMutationStrategy() {
            emptyMutationStrategyCreations.incrementAndGet();
        }
    }

    public static class DialectConstructorInsertStrategy extends BaseInsertStrategy {
        public DialectConstructorInsertStrategy(Dialect dialect) {
            assertThat(dialect).isNotNull();
            dialectInsertStrategyCreations.incrementAndGet();
        }
    }

    public static class EmptyConstructorInsertStrategy extends BaseInsertStrategy {
        public EmptyConstructorInsertStrategy() {
            emptyInsertStrategyCreations.incrementAndGet();
        }
    }

    public abstract static class BaseMutationStrategy implements SqmMultiTableMutationStrategy {
        @Override
        public int executeUpdate(
                SqmUpdateStatement<?> sqmUpdateStatement,
                DomainParameterXref domainParameterXref,
                DomainQueryExecutionContext context) {
            return 0;
        }

        @Override
        public int executeDelete(
                SqmDeleteStatement<?> sqmDeleteStatement,
                DomainParameterXref domainParameterXref,
                DomainQueryExecutionContext context) {
            return 0;
        }
    }

    public abstract static class BaseInsertStrategy implements SqmMultiTableInsertStrategy {
        @Override
        public int executeInsert(
                SqmInsertStatement<?> sqmInsertStatement,
                DomainParameterXref domainParameterXref,
                DomainQueryExecutionContext context) {
            return 0;
        }
    }
}
