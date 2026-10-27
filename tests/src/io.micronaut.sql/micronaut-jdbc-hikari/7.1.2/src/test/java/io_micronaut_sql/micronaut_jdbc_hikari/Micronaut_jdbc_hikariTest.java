/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_sql.micronaut_jdbc_hikari;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.configuration.jdbc.hikari.DatasourceConfiguration;
import io.micronaut.configuration.jdbc.hikari.HikariUrlDataSource;
import io.micronaut.context.ApplicationContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class Micronaut_jdbc_hikariTest {
    private static final String JDBC_URL =
            "jdbc:h2:mem:default;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

    @Test
    @Timeout(50)
    void wiresMicrometerRegistryAndRecordsPoolUsage() throws SQLException {
        Map<String, Object> properties = new HashMap<>();
        properties.put("datasources.default.url", "jdbc:h2:mem:metrics;DB_CLOSE_DELAY=-1");
        properties.put("datasources.default.driver-class-name", "org.h2.Driver");
        properties.put("datasources.default.username", "sa");
        properties.put("datasources.default.password", "");
        properties.put("datasources.default.pool-name", "metrics-pool");
        properties.put("datasources.default.maximum-pool-size", 1);
        properties.put("datasources.default.minimum-idle", 1);
        properties.put("datasources.default.connection-timeout", 10_000);
        properties.put("datasources.default.initialization-fail-timeout", 10_000);

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        try {
            try (ApplicationContext context = ApplicationContext.builder()
                    .properties(properties)
                    .singletons(meterRegistry)
                    .start()) {
                HikariUrlDataSource dataSource =
                        (HikariUrlDataSource) context.getBean(DataSource.class);

                assertThat(dataSource.getMetricRegistry()).isSameAs(meterRegistry);
                Timer usageTimer = meterRegistry.find("hikaricp.connections.usage")
                        .tag("pool", "metrics-pool")
                        .timer();
                assertThat(usageTimer).isNotNull();

                try (Connection connection = dataSource.getConnection()) {
                    assertThat(meterRegistry.find("hikaricp.connections.active")
                            .tag("pool", "metrics-pool")
                            .gauge())
                            .isNotNull();
                }

                assertThat(usageTimer.count()).isEqualTo(1);
            }
        } finally {
            meterRegistry.close();
        }
    }

    @Test
    @Timeout(50)
    void createsConfiguredPoolExecutesStatementsAndClosesPoolWithContext() throws SQLException {
        Map<String, Object> properties = new HashMap<>();
        properties.put("datasources.default.url", JDBC_URL);
        properties.put("datasources.default.driver-class-name", "org.h2.Driver");
        properties.put("datasources.default.username", "sa");
        properties.put("datasources.default.password", "");
        properties.put("datasources.default.pool-name", "micronaut-h2-pool");
        properties.put("datasources.default.maximum-pool-size", 2);
        properties.put("datasources.default.minimum-idle", 1);
        properties.put("datasources.default.auto-commit", false);
        properties.put("datasources.default.connection-timeout", 10_000);
        properties.put("datasources.default.validation-timeout", 10_000);
        properties.put("datasources.default.initialization-fail-timeout", 10_000);
        properties.put("datasources.default.connection-test-query", "SELECT 1");

        HikariUrlDataSource hikariDataSource;
        try (ApplicationContext context = ApplicationContext.run(properties)) {
            DatasourceConfiguration configuration = context.getBean(DatasourceConfiguration.class);
            DataSource dataSource = context.getBean(DataSource.class);

            assertThat(configuration.getName()).isEqualTo("default");
            assertThat(configuration.getUrl()).isEqualTo(JDBC_URL);
            assertThat(configuration.getDriverClassName()).isEqualTo("org.h2.Driver");
            assertThat(configuration.getUsername()).isEqualTo("sa");
            assertThat(configuration.getPassword()).isEmpty();
            assertThat(configuration.getMaximumPoolSize()).isEqualTo(2);
            assertThat(configuration.getMinimumIdle()).isEqualTo(1);
            assertThat(configuration.isAutoCommit()).isFalse();
            assertThat(configuration.getConnectionTimeout()).isEqualTo(10_000);
            assertThat(configuration.getValidationTimeout()).isEqualTo(10_000);
            assertThat(configuration.getConnectionTestQuery()).isEqualTo("SELECT 1");

            assertThat(dataSource).isInstanceOf(HikariUrlDataSource.class);
            hikariDataSource = (HikariUrlDataSource) dataSource;
            assertThat(hikariDataSource.getUrl()).isEqualTo(JDBC_URL);
            assertThat(hikariDataSource.getPoolName()).isEqualTo("micronaut-h2-pool");
            assertThat(hikariDataSource.isRunning()).isTrue();

            createAndPopulateTable(hikariDataSource);
            verifyRowsThroughSecondPooledConnection(hikariDataSource);

            HikariPoolMXBean pool = hikariDataSource.getHikariPoolMXBean();
            assertThat(pool).isNotNull();
            assertThat(pool.getTotalConnections()).isBetween(1, 2);
            assertThat(pool.getActiveConnections()).isZero();
        }

        assertThat(hikariDataSource.isClosed()).isTrue();
    }

    private static void createAndPopulateTable(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            assertThat(connection.isValid(10)).isTrue();
            assertThat(connection.getAutoCommit()).isFalse();
            statement.executeUpdate("CREATE TABLE books (id INTEGER PRIMARY KEY, title VARCHAR(100))");

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO books (id, title) VALUES (?, ?)")) {
                insert.setInt(1, 1);
                insert.setString(2, "Native Java");
                assertThat(insert.executeUpdate()).isEqualTo(1);

                insert.setInt(1, 2);
                insert.setString(2, "Micronaut SQL");
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
            connection.commit();
        }
    }

    private static void verifyRowsThroughSecondPooledConnection(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement query = connection.prepareStatement(
                     "SELECT title FROM books ORDER BY id");
             ResultSet rows = query.executeQuery()) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString("title")).isEqualTo("Native Java");
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString("title")).isEqualTo("Micronaut SQL");
            assertThat(rows.next()).isFalse();
        }
    }
}
