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
import org.hibernate.boot.SessionFactoryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.query.spi.DomainQueryExecutionContext;
import org.hibernate.query.sqm.internal.DomainParameterXref;
import org.hibernate.query.sqm.mutation.spi.SqmMultiTableInsertStrategy;
import org.hibernate.query.sqm.mutation.spi.SqmMultiTableMutationStrategy;
import org.hibernate.query.sqm.tree.delete.SqmDeleteStatement;
import org.hibernate.query.sqm.tree.insert.SqmInsertStatement;
import org.hibernate.query.sqm.tree.update.SqmUpdateStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionFactoryOptionsBuilderTest {

    @BeforeEach
    public void resetConstructorCounters() {
        DialectMutationStrategy.constructorCalls = 0;
        EmptyMutationStrategy.constructorCalls = 0;
        DialectInsertStrategy.constructorCalls = 0;
        EmptyInsertStrategy.constructorCalls = 0;
        ConfiguredSessionScopedInterceptor.constructorCalls = 0;
        AppliedStatelessInterceptor.constructorCalls = 0;
    }

    @Test
    public void instantiatesConfiguredSqmStrategiesWithDialectConstructors() {
        try (SessionFactory sessionFactory = buildSessionFactory(Map.of(
                AvailableSettings.QUERY_MULTI_TABLE_MUTATION_STRATEGY, DialectMutationStrategy.class.getName(),
                AvailableSettings.QUERY_MULTI_TABLE_INSERT_STRATEGY, DialectInsertStrategy.class.getName()))) {
            assertThat(sessionFactory).isNotNull();
            assertThat(DialectMutationStrategy.constructorCalls).isEqualTo(1);
            assertThat(DialectInsertStrategy.constructorCalls).isEqualTo(1);
        }
    }

    @Test
    public void instantiatesConfiguredSqmStrategiesWithEmptyConstructors() {
        try (SessionFactory sessionFactory = buildSessionFactory(Map.of(
                AvailableSettings.QUERY_MULTI_TABLE_MUTATION_STRATEGY, EmptyMutationStrategy.class.getName(),
                AvailableSettings.QUERY_MULTI_TABLE_INSERT_STRATEGY, EmptyInsertStrategy.class.getName()))) {
            assertThat(sessionFactory).isNotNull();
            assertThat(EmptyMutationStrategy.constructorCalls).isEqualTo(1);
            assertThat(EmptyInsertStrategy.constructorCalls).isEqualTo(1);
        }
    }

    @Test
    public void createsSessionScopedInterceptorFromConfiguredClass() {
        try (SessionFactory sessionFactory = buildSessionFactory(Map.of(
                AvailableSettings.SESSION_SCOPED_INTERCEPTOR,
                ConfiguredSessionScopedInterceptor.class.getName()))) {
            try (Session session = sessionFactory.openSession()) {
                assertThat(session).isNotNull();
            }
            assertThat(ConfiguredSessionScopedInterceptor.constructorCalls).isPositive();
        }
    }

    @Test
    public void createsStatelessInterceptorFromSessionFactoryBuilderClass() {
        try (SessionFactory sessionFactory = buildSessionFactory(
                Map.of(),
                builder -> builder.applyStatelessInterceptor(AppliedStatelessInterceptor.class))) {
            try (Session session = sessionFactory.openSession()) {
                assertThat(session).isNotNull();
            }
            assertThat(AppliedStatelessInterceptor.constructorCalls).isPositive();
        }
    }

    private SessionFactory buildSessionFactory(Map<String, Object> settings) {
        return buildSessionFactory(settings, null);
    }

    private SessionFactory buildSessionFactory(
            Map<String, Object> settings,
            Consumer<SessionFactoryBuilder> customizer) {
        StandardServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.DIALECT, H2Dialect.class.getName())
                .applySetting(AvailableSettings.HBM2DDL_AUTO, "none")
                .applySetting("hibernate.temp.use_jdbc_metadata_defaults", "false")
                .applySettings(settings)
                .build();
        try {
            Metadata metadata = new MetadataSources(serviceRegistry).buildMetadata();
            SessionFactoryBuilder builder = metadata.getSessionFactoryBuilder();
            if (customizer != null) {
                customizer.accept(builder);
            }
            return builder.build();
        } catch (RuntimeException e) {
            StandardServiceRegistryBuilder.destroy(serviceRegistry);
            throw e;
        }
    }

    public static final class DialectMutationStrategy implements SqmMultiTableMutationStrategy {
        private static int constructorCalls;

        public DialectMutationStrategy(Dialect dialect) {
            assertThat(dialect).isNotNull();
            constructorCalls++;
        }

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

    public static final class EmptyMutationStrategy implements SqmMultiTableMutationStrategy {
        private static int constructorCalls;

        public EmptyMutationStrategy() {
            constructorCalls++;
        }

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

    public static final class DialectInsertStrategy implements SqmMultiTableInsertStrategy {
        private static int constructorCalls;

        public DialectInsertStrategy(Dialect dialect) {
            assertThat(dialect).isNotNull();
            constructorCalls++;
        }

        @Override
        public int executeInsert(
                SqmInsertStatement<?> sqmInsertStatement,
                DomainParameterXref domainParameterXref,
                DomainQueryExecutionContext context) {
            return 0;
        }
    }

    public static final class EmptyInsertStrategy implements SqmMultiTableInsertStrategy {
        private static int constructorCalls;

        public EmptyInsertStrategy() {
            constructorCalls++;
        }

        @Override
        public int executeInsert(
                SqmInsertStatement<?> sqmInsertStatement,
                DomainParameterXref domainParameterXref,
                DomainQueryExecutionContext context) {
            return 0;
        }
    }

    public static final class ConfiguredSessionScopedInterceptor implements Interceptor {
        private static int constructorCalls;

        public ConfiguredSessionScopedInterceptor() {
            constructorCalls++;
        }
    }

    public static final class AppliedStatelessInterceptor implements Interceptor {
        private static int constructorCalls;

        public AppliedStatelessInterceptor() {
            constructorCalls++;
        }
    }
}
