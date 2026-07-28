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
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.SessionFactoryBuilder;
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
import org_hibernate.hibernate_core.entity.DialectOverrideEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionFactoryOptionsBuilderTest {

    @Test
    public void resolvesMutationStrategyWithDialectConstructor() {
        Map<String, Object> settings = settings();
        settings.put(AvailableSettings.QUERY_MULTI_TABLE_MUTATION_STRATEGY,
                DialectMutationStrategy.class.getName());

        try (SessionFactory sessionFactory = buildSessionFactory(settings)) {
            assertThat(sessionFactory).isNotNull();
        }
    }

    @Test
    public void resolvesMutationStrategyWithNoArgumentConstructor() {
        Map<String, Object> settings = settings();
        settings.put(AvailableSettings.QUERY_MULTI_TABLE_MUTATION_STRATEGY,
                NoArgumentMutationStrategy.class.getName());

        try (SessionFactory sessionFactory = buildSessionFactory(settings)) {
            assertThat(sessionFactory).isNotNull();
        }
    }

    @Test
    public void resolvesInsertStrategyWithDialectConstructor() {
        Map<String, Object> settings = settings();
        settings.put(AvailableSettings.QUERY_MULTI_TABLE_INSERT_STRATEGY,
                DialectInsertStrategy.class.getName());

        try (SessionFactory sessionFactory = buildSessionFactory(settings)) {
            assertThat(sessionFactory).isNotNull();
        }
    }

    @Test
    public void resolvesInsertStrategyWithNoArgumentConstructor() {
        Map<String, Object> settings = settings();
        settings.put(AvailableSettings.QUERY_MULTI_TABLE_INSERT_STRATEGY,
                NoArgumentInsertStrategy.class.getName());

        try (SessionFactory sessionFactory = buildSessionFactory(settings)) {
            assertThat(sessionFactory).isNotNull();
        }
    }

    @Test
    public void createsSessionScopedInterceptorFromConfiguredClass() {
        CountingInterceptor.reset();
        Map<String, Object> settings = settings();
        settings.put(AvailableSettings.SESSION_SCOPED_INTERCEPTOR, CountingInterceptor.class);

        try (SessionFactory sessionFactory = buildSessionFactory(settings);
                Session session = sessionFactory.openSession()) {
            assertThat(session).isNotNull();
            assertThat(CountingInterceptor.instances()).isPositive();
        }
    }

    @Test
    public void createsStatelessInterceptorFromSessionFactoryBuilder() {
        CountingInterceptor.reset();
        Map<String, Object> settings = settings();

        try (SessionFactory sessionFactory = buildSessionFactory(settings, CountingInterceptor.class);
                Session session = sessionFactory.openSession()) {
            assertThat(session).isNotNull();
            assertThat(CountingInterceptor.instances()).isPositive();
        }
    }

    private Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put(AvailableSettings.URL,
                "jdbc:h2:mem:session-factory-options;DB_CLOSE_DELAY=-1");
        settings.put(AvailableSettings.DRIVER, "org.h2.Driver");
        settings.put(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect");
        settings.put(AvailableSettings.HBM2DDL_AUTO, "none");
        settings.put(AvailableSettings.BYTECODE_PROVIDER, "none");
        settings.put("hibernate.temp.use_jdbc_metadata_defaults", "false");
        return settings;
    }

    private SessionFactory buildSessionFactory(Map<String, Object> settings) {
        return buildSessionFactory(settings, null);
    }

    private SessionFactory buildSessionFactory(
            Map<String, Object> settings, Class<? extends Interceptor> statelessInterceptorClass) {
        StandardServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
                .applySettings(settings)
                .build();
        try {
            SessionFactoryBuilder sessionFactoryBuilder = new MetadataSources(serviceRegistry)
                    .addAnnotatedClass(DialectOverrideEntity.class)
                    .buildMetadata()
                    .getSessionFactoryBuilder();
            if (statelessInterceptorClass != null) {
                sessionFactoryBuilder.applyStatelessInterceptor(statelessInterceptorClass);
            }
            return sessionFactoryBuilder.build();
        } catch (RuntimeException exception) {
            StandardServiceRegistryBuilder.destroy(serviceRegistry);
            throw exception;
        }
    }

    public static class DialectMutationStrategy implements SqmMultiTableMutationStrategy {

        public DialectMutationStrategy(Dialect dialect) {
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

    public static class NoArgumentMutationStrategy implements SqmMultiTableMutationStrategy {

        public NoArgumentMutationStrategy() {
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

    public static class DialectInsertStrategy implements SqmMultiTableInsertStrategy {

        public DialectInsertStrategy(Dialect dialect) {
        }

        @Override
        public int executeInsert(
                SqmInsertStatement<?> sqmInsertStatement,
                DomainParameterXref domainParameterXref,
                DomainQueryExecutionContext context) {
            return 0;
        }
    }

    public static class NoArgumentInsertStrategy implements SqmMultiTableInsertStrategy {

        public NoArgumentInsertStrategy() {
        }

        @Override
        public int executeInsert(
                SqmInsertStatement<?> sqmInsertStatement,
                DomainParameterXref domainParameterXref,
                DomainQueryExecutionContext context) {
            return 0;
        }
    }

    public static class CountingInterceptor implements Interceptor {

        private static final AtomicInteger CONSTRUCTION_COUNT = new AtomicInteger();

        public CountingInterceptor() {
            CONSTRUCTION_COUNT.incrementAndGet();
        }

        static void reset() {
            CONSTRUCTION_COUNT.set(0);
        }

        static int instances() {
            return CONSTRUCTION_COUNT.get();
        }
    }
}
