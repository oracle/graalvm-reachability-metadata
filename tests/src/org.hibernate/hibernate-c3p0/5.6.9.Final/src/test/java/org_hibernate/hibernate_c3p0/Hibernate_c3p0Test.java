/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_c3p0;

import com.mchange.v2.c3p0.PooledDataSource;
import org.hibernate.c3p0.internal.C3P0ConnectionProvider;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

public class Hibernate_c3p0Test {

    private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();

    @Test
    @Timeout(60)
    public void configuresAndUsesConnectionProviderDirectly() throws Exception {
        disableMisspelledConfigurationWarnings();
        C3P0ConnectionProvider provider = new C3P0ConnectionProvider();
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
        }
    }

    private static void disableMisspelledConfigurationWarnings() {
        Logger.getLogger("com.mchange.v2.c3p0.cfg.C3P0ConfigUtils").setLevel(Level.SEVERE);
        Logger.getLogger("com.mchange.v2.c3p0.cfg.C3P0ConfigXmlUtils").setLevel(Level.SEVERE);
    }

    private static Map<String, Object> connectionSettings(String databaseName) {
        Map<String, Object> settings = new HashMap<>();
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
}
