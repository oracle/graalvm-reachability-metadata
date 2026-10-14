/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.JdbcSettings;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.service.spi.Configurable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectionProviderInitiatorTest {

    private static final List<String> ACQUISITIONS = new CopyOnWriteArrayList<>();

    @BeforeEach
    void clearAcquisitions() {
        ACQUISITIONS.clear();
    }

    @Test
    void explicitConnectionProviderClassSuppliesConnections() {
        Configuration configuration = SessionFactoryTestSupport.h2Configuration(Ticket.class);
        configuration.getProperties().put(JdbcSettings.CONNECTION_PROVIDER, H2ConnectionProvider.class);

        assertTicketRoundTrip(configuration.buildSessionFactory());
    }

    @Test
    void namedConnectionProviderSuppliesConnections() {
        Configuration configuration = SessionFactoryTestSupport.h2Configuration(Ticket.class);
        configuration.setProperty(JdbcSettings.CONNECTION_PROVIDER, H2ConnectionProvider.class.getName());

        assertTicketRoundTrip(configuration.buildSessionFactory());
    }

    @Test
    void singleRegisteredConnectionProviderIsSelectedImplicitly() {
        BootstrapServiceRegistry bootstrapRegistry = new BootstrapServiceRegistryBuilder()
                .applyStrategySelector(ConnectionProvider.class, "test-h2", H2ConnectionProvider.class)
                .build();
        Configuration configuration = SessionFactoryTestSupport.h2Configuration(Ticket.class);
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder(bootstrapRegistry)
                .applySettings(configuration.getProperties())
                .build();

        try {
            assertTicketRoundTrip(configuration.buildSessionFactory(registry));
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private static void assertTicketRoundTrip(SessionFactory sessionFactory) {
        try (sessionFactory) {
            sessionFactory.inTransaction(session -> session.persist(new Ticket(7L, "front row")));
            String seat = sessionFactory.fromTransaction(session -> session.find(Ticket.class, 7L).getSeat());

            assertThat(seat).isEqualTo("front row");
        }
        assertThat(ACQUISITIONS).isNotEmpty();
    }

    public static class H2ConnectionProvider implements ConnectionProvider, Configurable {

        private String url;

        @Override
        public void configure(Map<String, Object> configurationValues) {
            url = configurationValues.get(JdbcSettings.JAKARTA_JDBC_URL).toString();
        }

        @Override
        public Connection getConnection() throws SQLException {
            ACQUISITIONS.add(url);
            return DriverManager.getConnection(url, "sa", "");
        }

        @Override
        public void closeConnection(Connection connection) throws SQLException {
            connection.close();
        }

        @Override
        public boolean supportsAggressiveRelease() {
            return false;
        }

        @Override
        public boolean isUnwrappableAs(Class<?> unwrapType) {
            return unwrapType.isInstance(this);
        }

        @Override
        public <T> T unwrap(Class<T> unwrapType) {
            return unwrapType.cast(this);
        }
    }

    @Entity(name = "Ticket")
    public static class Ticket {

        @Id
        private Long id;

        private String seat;

        public Ticket() {
        }

        public Ticket(Long id, String seat) {
            this.id = id;
            this.seat = seat;
        }

        public String getSeat() {
            return seat;
        }
    }
}
