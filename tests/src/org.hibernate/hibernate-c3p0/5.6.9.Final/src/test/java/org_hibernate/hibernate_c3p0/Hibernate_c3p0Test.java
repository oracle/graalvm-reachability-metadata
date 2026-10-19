/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_c3p0;

import com.mchange.v2.c3p0.PooledDataSource;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.Proxy;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.c3p0.internal.C3P0ConnectionProvider;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class Hibernate_c3p0Test {

    private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();

    @Test
    @Timeout(60)
    public void persistsAndQueriesThroughHibernateConnectionPool() {
        Map<String, Object> settings = connectionSettings(uniqueDatabaseName("hibernate"));
        settings.put("hibernate.connection.provider_class", C3P0ConnectionProvider.class.getName());
        settings.put("hibernate.hbm2ddl.auto", "create-drop");
        settings.put("hibernate.show_sql", "false");

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySettings(settings)
                .build();
        try {
            SessionFactory sessionFactory = new MetadataSources(registry)
                    .addAnnotatedClass(PooledRecord.class)
                    .buildMetadata()
                    .buildSessionFactory();
            try {
                ConnectionProvider connectionProvider = registry.getService(ConnectionProvider.class);
                assertThat(connectionProvider).isInstanceOf(C3P0ConnectionProvider.class);

                try (Session session = sessionFactory.openSession()) {
                    session.beginTransaction();
                    session.persist(new PooledRecord(1L, "stored through c3p0"));
                    session.getTransaction().commit();
                }

                try (Session session = sessionFactory.openSession()) {
                    List<PooledRecord> records = session.createQuery(
                                    "from PooledRecord where payload = :payload",
                                    PooledRecord.class)
                            .setParameter("payload", "stored through c3p0")
                            .getResultList();
                    assertThat(records)
                            .singleElement()
                            .extracting(PooledRecord::getId, PooledRecord::getPayload)
                            .containsExactly(1L, "stored through c3p0");
                }
            } finally {
                sessionFactory.close();
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    @Timeout(60)
    public void automaticallySelectsC3p0ProviderFromPoolSettings() throws Exception {
        Map<String, Object> settings = connectionSettings(uniqueDatabaseName("automatic-provider"));
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySettings(settings)
                .build();
        try {
            ConnectionProvider connectionProvider = registry.getService(ConnectionProvider.class);
            assertThat(connectionProvider).isInstanceOf(C3P0ConnectionProvider.class);

            try (Connection connection = connectionProvider.getConnection();
                    PreparedStatement query = connection.prepareStatement("select 1");
                    ResultSet resultSet = query.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isOne();
                assertThat(resultSet.next()).isFalse();
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    @Timeout(60)
    public void configuresAndUsesConnectionProviderDirectly() throws Exception {
        ServiceRegistryImplementor registry = (ServiceRegistryImplementor) new StandardServiceRegistryBuilder().build();
        C3P0ConnectionProvider provider = new C3P0ConnectionProvider();
        provider.injectServices(registry);
        provider.configure(connectionSettings(uniqueDatabaseName("provider")));

        try {
            assertThat(provider.supportsAggressiveRelease()).isFalse();
            assertThat(provider.isUnwrappableAs(ConnectionProvider.class)).isTrue();
            assertThat(provider.isUnwrappableAs(C3P0ConnectionProvider.class)).isTrue();
            assertThat(provider.isUnwrappableAs(DataSource.class)).isTrue();
            assertThat(provider.unwrap(ConnectionProvider.class)).isSameAs(provider);
            assertThat(provider.unwrap(C3P0ConnectionProvider.class)).isSameAs(provider);

            DataSource dataSource = provider.unwrap(DataSource.class);
            assertThat(dataSource).isInstanceOf(PooledDataSource.class);

            Connection connection = provider.getConnection();
            try {
                assertThat(connection.getAutoCommit()).isFalse();
                try (PreparedStatement create = connection.prepareStatement(
                                "create table pooled_message (id bigint primary key, payload varchar(100))")) {
                    assertThat(create.executeUpdate()).isZero();
                }
                try (PreparedStatement insert = connection.prepareStatement(
                                "insert into pooled_message (id, payload) values (?, ?)")) {
                    insert.setLong(1, 7L);
                    insert.setString(2, "checked out");
                    assertThat(insert.executeUpdate()).isOne();
                }
                connection.commit();
            } finally {
                provider.closeConnection(connection);
            }
            assertThat(connection.isClosed()).isTrue();

            try (Connection pooledConnection = dataSource.getConnection();
                    PreparedStatement select = pooledConnection.prepareStatement(
                            "select payload from pooled_message where id = ?")) {
                select.setLong(1, 7L);
                try (ResultSet resultSet = select.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getString("payload")).isEqualTo("checked out");
                    assertThat(resultSet.next()).isFalse();
                }
            }
        } finally {
            provider.stop();
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private static Map<String, Object> connectionSettings(String databaseName) {
        Map<String, Object> settings = new HashMap<>();
        settings.put("hibernate.connection.driver_class", "org.h2.Driver");
        settings.put("hibernate.connection.url", "jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1");
        settings.put("hibernate.connection.username", "sa");
        settings.put("hibernate.connection.password", "");
        settings.put("hibernate.connection.autocommit", "false");
        settings.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        settings.put("hibernate.c3p0.min_size", "1");
        settings.put("hibernate.c3p0.max_size", "3");
        settings.put("hibernate.c3p0.timeout", "30");
        settings.put("hibernate.c3p0.max_statements", "20");
        settings.put("hibernate.c3p0.idle_test_period", "10");
        settings.put("hibernate.c3p0.acquire_increment", "1");
        settings.put("hibernate.c3p0.testConnectionOnCheckout", "true");
        return settings;
    }

    private static String uniqueDatabaseName(String prefix) {
        return prefix + "_" + DATABASE_SEQUENCE.incrementAndGet();
    }

    @Entity(name = "PooledRecord")
    @Table(name = "pooled_record")
    @Proxy(lazy = false)
    public static class PooledRecord {

        @Id
        private Long id;

        private String payload;

        public PooledRecord() {
        }

        public PooledRecord(Long id, String payload) {
            this.id = id;
            this.payload = payload;
        }

        public Long getId() {
            return id;
        }

        public String getPayload() {
            return payload;
        }
    }
}
