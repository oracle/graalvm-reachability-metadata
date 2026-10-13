/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.sql.Driver;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.engine.jdbc.connections.internal.ConnectionCreator;
import org.hibernate.engine.jdbc.connections.internal.ConnectionCreatorFactory;
import org.hibernate.engine.jdbc.connections.internal.ConnectionCreatorFactoryImpl;
import org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProvider;
import org.hibernate.service.ServiceRegistry;
import org_hibernate_orm.hibernate_core.entity.Person;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DriverManagerConnectionProviderTest {

    private static final AtomicReference<String> CREATED_FOR = new AtomicReference<>();

    @Test
    void configuredConnectionCreatorFactoryOpensThePooledConnections() {
        CREATED_FOR.set(null);
        Configuration configuration = SessionFactoryTestSupport.h2Configuration(Person.class);
        configuration.setProperty(
                DriverManagerConnectionProvider.CONNECTION_CREATOR_FACTORY,
                RecordingConnectionCreatorFactory.class.getName());

        try (SessionFactory sessionFactory = configuration.buildSessionFactory()) {
            Person.persistAliceAndBob(sessionFactory);
            Long count = sessionFactory.fromTransaction(session -> session.createSelectionQuery(
                    "select count(p) from Person p", Long.class).getSingleResult());

            assertThat(count).isEqualTo(2L);
        }
        assertThat(CREATED_FOR.get()).startsWith("jdbc:h2:mem:");
    }

    public static class RecordingConnectionCreatorFactory implements ConnectionCreatorFactory {

        @Override
        public ConnectionCreator create(
                Driver driver,
                ServiceRegistry serviceRegistry,
                String url,
                Properties connectionProps,
                Boolean autocommit,
                Integer isolation,
                String initSql,
                Map<String, Object> configurationValues) {
            CREATED_FOR.set(url);
            return ConnectionCreatorFactoryImpl.INSTANCE.create(
                    driver, serviceRegistry, url, connectionProps, autocommit, isolation, initSql, configurationValues);
        }
    }
}
