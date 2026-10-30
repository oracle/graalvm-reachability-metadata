/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_sql;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import org.h2.Driver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.autoconfigure.init.ApplicationScriptDatabaseInitializer;
import org.springframework.boot.sql.autoconfigure.init.SqlInitializationProperties;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class ApplicationScriptDatabaseInitializerTest {

    @Test
    void platformSelectsSchemaAndDataFromTheClasspath() throws Exception {
        SqlInitializationProperties properties = new SqlInitializationProperties();
        properties.setPlatform("forge");
        DatabaseInitializationSettings settings = ApplicationScriptDatabaseInitializer.getSettings(properties);
        assertThat(settings.getSchemaLocations()).containsExactly(
                "optional:classpath*:schema-forge.sql", "optional:classpath*:schema.sql");
        assertThat(settings.getDataLocations()).containsExactly(
                "optional:classpath*:data-forge.sql", "optional:classpath*:data.sql");
        assertThat(settings.getMode()).isEqualTo(DatabaseInitializationMode.EMBEDDED);
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource(new Driver(),
                "jdbc:h2:mem:platform_settings", "sa", "");

        try (Connection connection = dataSource.getConnection()) {
            DataSourceScriptDatabaseInitializer initializer = new DataSourceScriptDatabaseInitializer(dataSource, settings);
            initializer.setResourceLoader(new DefaultResourceLoader());
            initializer.afterPropertiesSet();

            assertThat(new JdbcTemplate(dataSource).queryForList(
                    "SELECT description FROM platform_items ORDER BY id", String.class))
                    .containsExactly("loaded from platform scripts");
        }
    }

    @Test
    void explicitPropertiesControlScriptLocationsEncodingSeparatorAndErrorHandling(@TempDir Path directory)
            throws Exception {
        Path schema = Files.writeString(directory.resolve("custom-schema.sql"), """
                CREATE TABLE configured_items (id INTEGER PRIMARY KEY, label VARCHAR(80))@@
                """, StandardCharsets.UTF_8);
        Path data = Files.writeString(directory.resolve("custom-data.sql"), """
                INSERT INTO missing_items VALUES (1)@@
                INSERT INTO configured_items VALUES (5, 'caf\u00e9 harvest')@@
                INSERT INTO configured_items VALUES (6, 'orchard')@@
                """, StandardCharsets.UTF_8);
        SqlInitializationProperties properties = new SqlInitializationProperties();
        properties.setSchemaLocations(List.of(schema.toUri().toString()));
        properties.setDataLocations(List.of(data.toUri().toString()));
        properties.setMode(DatabaseInitializationMode.ALWAYS);
        properties.setEncoding(StandardCharsets.UTF_8);
        properties.setSeparator("@@");
        properties.setContinueOnError(true);
        DatabaseInitializationSettings settings = ApplicationScriptDatabaseInitializer.getSettings(properties);
        assertThat(settings.getMode()).isEqualTo(DatabaseInitializationMode.ALWAYS);
        assertThat(settings.getEncoding()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(settings.getSeparator()).isEqualTo("@@");
        assertThat(settings.isContinueOnError()).isTrue();
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource(new Driver(),
                "jdbc:h2:mem:explicit_settings", "sa", "");

        try (Connection connection = dataSource.getConnection()) {
            DataSourceScriptDatabaseInitializer initializer = new DataSourceScriptDatabaseInitializer(dataSource, settings);
            initializer.setResourceLoader(new DefaultResourceLoader());
            assertThat(initializer.initializeDatabase()).isTrue();

            assertThat(new JdbcTemplate(dataSource).queryForList(
                    "SELECT label FROM configured_items ORDER BY id", String.class))
                    .containsExactly("caf\u00e9 harvest", "orchard");
        }
    }
}
