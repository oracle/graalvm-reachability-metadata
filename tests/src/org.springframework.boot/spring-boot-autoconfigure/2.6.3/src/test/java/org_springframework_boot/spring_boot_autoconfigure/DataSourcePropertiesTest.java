/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class DataSourcePropertiesTest {

    @Test
    void configuredDriverCreatesUsableDataSource() throws Exception {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setType(SimpleDriverDataSource.class);
        properties.setDriverClassName("org.h2.Driver");
        properties.setUrl("jdbc:h2:mem:configured_properties;DB_CLOSE_DELAY=0");
        properties.setUsername("sa");
        properties.setPassword("");
        properties.afterPropertiesSet();

        assertThat(properties.determineDriverClassName()).isEqualTo("org.h2.Driver");
        DataSource dataSource = properties.initializeDataSourceBuilder().build();
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE notes (id INTEGER PRIMARY KEY, content VARCHAR(40))");
            statement.executeUpdate("INSERT INTO notes VALUES (1, 'configured')");
            try (ResultSet rows = statement.executeQuery("SELECT content FROM notes WHERE id = 1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("configured");
                assertThat(rows.next()).isFalse();
            }
        }
    }

    @Test
    void detectsEmbeddedDatabaseAndDerivesConnectionDefaults() throws Exception {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setBeanClassLoader(getClass().getClassLoader());
        properties.setGenerateUniqueName(false);
        properties.setName("detected_properties");
        properties.setType(SimpleDriverDataSource.class);
        properties.afterPropertiesSet();

        assertThat(properties.getEmbeddedDatabaseConnection()).isEqualTo(EmbeddedDatabaseConnection.H2);
        assertThat(properties.determineDatabaseName()).isEqualTo("detected_properties");
        assertThat(properties.determineUrl()).startsWith("jdbc:h2:mem:detected_properties;");
        assertThat(properties.determineUsername()).isEqualTo("sa");
        assertThat(properties.determinePassword()).isEmpty();

        DataSource dataSource = properties.initializeDataSourceBuilder().build();
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("SELECT 6 * 7")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(42);
            }
            statement.execute("SHUTDOWN");
        }
    }
}
