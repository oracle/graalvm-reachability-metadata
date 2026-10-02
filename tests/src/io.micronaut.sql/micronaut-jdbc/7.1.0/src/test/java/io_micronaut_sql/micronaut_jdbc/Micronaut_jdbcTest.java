/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_sql.micronaut_jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.configuration.jdbc.hikari.DatasourceConfiguration;
import io.micronaut.context.ApplicationContext;
import io.micronaut.jdbc.BasicJdbcConfiguration;
import io.micronaut.jdbc.CalculatedSettings;
import io.micronaut.jdbc.DataSourceResolver;
import io.micronaut.jdbc.JdbcDatabaseManager;
import io.micronaut.jdbc.metadata.DataSourcePoolMetadataProvider;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class Micronaut_jdbcTest {
    private static final String JDBC_URL =
            "jdbc:h2:mem:micronautJdbc;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

    @Test
    @Timeout(50)
    void configuresDataSourceThroughApplicationContext() throws SQLException {
        try (ApplicationContext context = ApplicationContext.builder()
                .properties(dataSourceProperties())
                .start()) {
            BasicJdbcConfiguration configuration = context.getBean(BasicJdbcConfiguration.class);
            assertThat(configuration.getName()).isEqualTo("default");
            assertThat(configuration.getConfiguredUrl()).isEqualTo(JDBC_URL);
            assertThat(configuration.getUrl()).isEqualTo(JDBC_URL);
            assertThat(configuration.getConfiguredDriverClassName()).isEqualTo("org.h2.Driver");
            assertThat(configuration.getDriverClassName()).isEqualTo("org.h2.Driver");
            assertThat(configuration.getConfiguredUsername()).isEqualTo("sa");
            assertThat(configuration.getUsername()).isEqualTo("sa");
            assertThat(configuration.getConfiguredPassword()).isEmpty();
            assertThat(configuration.getPassword()).isEmpty();
            assertThat(configuration.getValidationQuery()).isEqualTo("SELECT 1");

            DataSource dataSource = context.getBean(DataSource.class);
            DataSourceResolver resolver = context.getBean(DataSourceResolver.class);
            assertThat(resolver.resolve(dataSource)).isSameAs(dataSource);
            assertThat(context.getBeansOfType(DataSourcePoolMetadataProvider.class)).isEmpty();

            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                assertThat(connection.isValid(10)).isTrue();
                statement.executeUpdate(
                        "CREATE TABLE books (id INTEGER PRIMARY KEY, title VARCHAR(100))");
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO books (id, title) VALUES (?, ?)")) {
                    insert.setInt(1, 1);
                    insert.setString(2, "Micronaut JDBC");
                    assertThat(insert.executeUpdate()).isEqualTo(1);
                }
            }

            try (Connection connection = dataSource.getConnection();
                    PreparedStatement query = connection.prepareStatement(
                            "SELECT title FROM books WHERE id = ?")) {
                query.setInt(1, 1);
                try (ResultSet rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("title")).isEqualTo("Micronaut JDBC");
                    assertThat(rows.next()).isFalse();
                }
            }
        }
    }

    @Test
    @Timeout(50)
    void calculatesEmbeddedH2SettingsThroughBasicConfiguration() {
        BasicJdbcConfiguration configuration = new DatasourceConfiguration("embedded");
        CalculatedSettings settings = new CalculatedSettings(configuration);

        assertThat(settings.getDriverClassName()).isEqualTo("org.h2.Driver");
        assertThat(settings.getUrl())
                .isEqualTo("jdbc:h2:mem:embedded;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        assertThat(settings.getUsername()).isEqualTo("sa");
        assertThat(settings.getPassword()).isEmpty();
        assertThat(settings.getValidationQuery()).isEqualTo("SELECT 1");
    }

    @Test
    @Timeout(50)
    void identifiesH2DatabaseAndBuildsNamedEmbeddedUrl() {
        Optional<JdbcDatabaseManager.JdbcDatabase> database =
                JdbcDatabaseManager.findDatabase(JDBC_URL);
        assertThat(database).isPresent();
        assertThat(database.get().getDriverClassName()).isEqualTo("org.h2.Driver");
        assertThat(database.get().getValidationQuery()).isEqualTo("SELECT 1");

        Optional<JdbcDatabaseManager.EmbeddedJdbcDatabase> embeddedDatabase =
                JdbcDatabaseManager.findEmbeddedDatabase("org.h2.Driver");
        assertThat(embeddedDatabase).isPresent();
        assertThat(embeddedDatabase.get().getUrl("orders"))
                .isEqualTo("jdbc:h2:mem:orders;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        assertThat(embeddedDatabase.get().getDefaultUsername()).isEqualTo("sa");
        assertThat(embeddedDatabase.get().getDefaultPassword()).isEmpty();
        assertThat(JdbcDatabaseManager.isEmbedded("org.h2.Driver")).isTrue();
    }

    private static Map<String, Object> dataSourceProperties() {
        return Map.of(
                "datasources.default.url", JDBC_URL,
                "datasources.default.driver-class-name", "org.h2.Driver",
                "datasources.default.username", "sa",
                "datasources.default.password", "",
                "datasources.default.connection-timeout", 10_000,
                "datasources.default.validation-timeout", 10_000);
    }
}
