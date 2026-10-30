/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_sql;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptStatementFailedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(120)
public class AbstractScriptDatabaseInitializerTest {

    @TempDir
    Path directory;

    @Test
    void embeddedDatabaseRunsSortedSchemaScriptsBeforeData() throws Exception {
        Files.writeString(directory.resolve("schema-20.sql"), "INSERT INTO items VALUES (1, 'schema');");
        Files.writeString(directory.resolve("schema-10.sql"),
                "CREATE TABLE items (id INTEGER PRIMARY KEY, name VARCHAR(40));");
        Path data = Files.writeString(directory.resolve("data.sql"), "INSERT INTO items VALUES (2, 'data');");
        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setSchemaLocations(List.of(directory.toUri() + "schema-*.sql"));
        settings.setDataLocations(List.of(data.toUri().toString()));
        JdbcDataSource dataSource = dataSource("ordered");

        try (Connection connection = dataSource.getConnection()) {
            DataSourceScriptDatabaseInitializer initializer = initializer(dataSource, settings);
            assertThat(initializer.initializeDatabase()).isTrue();
            assertThat(new JdbcTemplate(dataSource).queryForList("SELECT name FROM items ORDER BY id", String.class))
                    .containsExactly("schema", "data");
        }
    }

    @Test
    void neverModeLeavesDatabaseUnchanged() throws Exception {
        Path schema = Files.writeString(directory.resolve("schema.sql"), "CREATE TABLE disabled_items (id INTEGER);");
        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setSchemaLocations(List.of(schema.toUri().toString()));
        settings.setMode(DatabaseInitializationMode.NEVER);
        JdbcDataSource dataSource = dataSource("disabled");

        try (Connection connection = dataSource.getConnection()) {
            assertThat(initializer(dataSource, settings).initializeDatabase()).isFalse();
            assertThat(new JdbcTemplate(dataSource).queryForObject("""
                    SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                    WHERE TABLE_NAME = 'DISABLED_ITEMS'
                    """, Integer.class)).isZero();
        }
    }

    @Test
    void missingOptionalScriptsDoNotInitializeDatabase() {
        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setSchemaLocations(List.of("optional:" + directory.resolve("absent-schema.sql").toUri()));
        settings.setDataLocations(List.of("optional:" + directory.resolve("absent-data.sql").toUri()));

        assertThat(initializer(dataSource("optional"), settings).initializeDatabase()).isFalse();
    }

    @Test
    void missingRequiredSchemaReportsItsLocation() {
        String location = directory.resolve("required.sql").toUri().toString();
        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setSchemaLocations(List.of(location));

        assertThatThrownBy(() -> initializer(dataSource("required"), settings).initializeDatabase())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No schema scripts found")
                .hasMessageContaining(location);
    }

    @Test
    void continueOnErrorExecutesStatementsFollowingAnInvalidStatement() throws Exception {
        DatabaseInitializationSettings settings = errorHandlingSettings();
        settings.setContinueOnError(true);
        JdbcDataSource dataSource = dataSource("continue");

        try (Connection connection = dataSource.getConnection()) {
            assertThat(initializer(dataSource, settings).initializeDatabase()).isTrue();
            assertThat(new JdbcTemplate(dataSource).queryForList("SELECT id FROM entries", Integer.class))
                    .containsExactly(7);
        }
    }

    @Test
    void defaultErrorHandlingStopsAtTheInvalidStatement() throws Exception {
        DatabaseInitializationSettings settings = errorHandlingSettings();
        JdbcDataSource dataSource = dataSource("stop");

        try (Connection connection = dataSource.getConnection()) {
            assertThatThrownBy(() -> initializer(dataSource, settings).initializeDatabase())
                    .isInstanceOf(ScriptStatementFailedException.class);
            assertThat(new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM entries", Integer.class))
                    .isZero();
        }
    }

    private DatabaseInitializationSettings errorHandlingSettings() throws Exception {
        Path schema = Files.writeString(directory.resolve("schema.sql"), "CREATE TABLE entries (id INTEGER);");
        Path data = Files.writeString(directory.resolve("data.sql"), """
                INSERT INTO nonexistent_table VALUES (1);
                INSERT INTO entries VALUES (7);
                """);
        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setMode(DatabaseInitializationMode.ALWAYS);
        settings.setSchemaLocations(List.of(schema.toUri().toString()));
        settings.setDataLocations(List.of(data.toUri().toString()));
        return settings;
    }

    private static JdbcDataSource dataSource(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + name);
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private static DataSourceScriptDatabaseInitializer initializer(JdbcDataSource dataSource,
            DatabaseInitializationSettings settings) {
        DataSourceScriptDatabaseInitializer initializer = new DataSourceScriptDatabaseInitializer(dataSource, settings);
        initializer.setResourceLoader(new DefaultResourceLoader());
        return initializer;
    }
}
