/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_sql;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.sql.autoconfigure.init.ApplicationScriptDatabaseInitializer;
import org.springframework.boot.sql.autoconfigure.init.SqlInitializationProperties;
import org.springframework.boot.sql.init.AbstractScriptDatabaseInitializer;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptStatementFailedException;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AbstractScriptDatabaseInitializerTest {
    @TempDir
    Path directory;

    @Test
    void initializesSchemaBeforeDataThroughLifecycleCallback() throws Exception {
        SqlInitializationProperties properties = new SqlInitializationProperties();
        properties.setSchemaLocations(List.of(script("schema.sql", "CREATE TABLE messages (body VARCHAR(100));")));
        properties.setDataLocations(List.of(script("data.sql", "INSERT INTO messages VALUES ('initialized');")));
        properties.setMode(DatabaseInitializationMode.ALWAYS);
        DatabaseInitializationSettings settings = ApplicationScriptDatabaseInitializer.getSettings(properties);

        try (Connection connection = openDatabase()) {
            JdbcInitializer initializer = new JdbcInitializer(connection, settings);
            initializer.afterPropertiesSet();

            assertEquals("initialized", query(connection, "SELECT body FROM messages"));
            assertEquals(2, initializer.executedBatches);
        }
    }

    @Test
    void embeddedModeInitializesAnEmbeddedDatabase() throws Exception {
        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setSchemaLocations(List.of(script("schema.sql", "CREATE TABLE messages (body VARCHAR(100));")));
        settings.setDataLocations(List.of(script("data.sql", "INSERT INTO messages VALUES ('embedded');")));

        try (Connection connection = openDatabase()) {
            JdbcInitializer initializer = new JdbcInitializer(connection, settings);

            assertTrue(initializer.initializeDatabase());
            assertEquals("embedded", query(connection, "SELECT body FROM messages"));
            assertEquals(2, initializer.executedBatches);
        }
    }

    @Test
    void neverModeDoesNotExecuteExistingScripts() throws Exception {
        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setMode(DatabaseInitializationMode.NEVER);
        settings.setSchemaLocations(List.of(script("schema.sql", "CREATE TABLE messages (body VARCHAR(100));")));
        settings.setDataLocations(List.of(script("data.sql", "INSERT INTO messages VALUES ('disabled');")));

        try (Connection connection = openDatabase()) {
            JdbcInitializer initializer = new JdbcInitializer(connection, settings);

            assertFalse(initializer.initializeDatabase());
            assertEquals(0, initializer.executedBatches);
            assertEquals("0", query(connection,
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'MESSAGES'"));
        }
    }

    @Test
    void appliesWildcardScriptsInFilenameOrder() throws Exception {
        script("02-data.sql", "INSERT INTO messages VALUES ('ordered');");
        script("01-schema.sql", "CREATE TABLE messages (body VARCHAR(100));");
        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setSchemaLocations(List.of(directory.toUri() + "*.sql"));

        try (Connection connection = openDatabase()) {
            JdbcInitializer initializer = new JdbcInitializer(connection, settings);

            assertTrue(initializer.initializeDatabase());
            assertEquals("ordered", query(connection, "SELECT body FROM messages"));
            assertEquals(1, initializer.executedBatches);
        }
    }

    @Test
    void optionalMissingLocationsDoNotPreventDataInitialization() throws Exception {
        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setSchemaLocations(List.of("optional:" + directory.resolve("missing.sql").toUri()));
        settings.setDataLocations(List.of(script("data.sql", "CREATE TABLE messages (body VARCHAR(100));\n"
                + "INSERT INTO messages VALUES ('data only');")));

        try (Connection connection = openDatabase()) {
            JdbcInitializer initializer = new JdbcInitializer(connection, settings);

            assertTrue(initializer.initializeDatabase());
            assertEquals("data only", query(connection, "SELECT body FROM messages"));
            assertEquals(1, initializer.executedBatches);
        }
    }

    @Test
    void noConfiguredScriptsLeavesDatabaseUntouched() throws Exception {
        try (Connection connection = openDatabase()) {
            JdbcInitializer initializer = new JdbcInitializer(connection, new DatabaseInitializationSettings());

            assertFalse(initializer.initializeDatabase());
            assertEquals(0, initializer.executedBatches);
        }
    }

    @Test
    void rejectsMissingRequiredScript() throws Exception {
        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        String missingLocation = directory.resolve("missing.sql").toUri().toString();
        settings.setSchemaLocations(List.of(missingLocation));

        try (Connection connection = openDatabase()) {
            JdbcInitializer initializer = new JdbcInitializer(connection, settings);

            IllegalStateException exception = assertThrows(IllegalStateException.class, initializer::initializeDatabase);
            assertTrue(exception.getMessage().contains("No schema scripts found"));
            assertTrue(exception.getMessage().contains(missingLocation));
            assertEquals(0, initializer.executedBatches);
        }
    }

    @Test
    void honorsEncodingSeparatorAndContinueOnError() throws Exception {
        String location = script("custom.sql", """
                CREATE TABLE messages (body VARCHAR(100))@@
                INSERT INTO absent_table VALUES (1)@@
                INSERT INTO messages VALUES ('caf\u00e9')@@
                """, StandardCharsets.UTF_16LE);
        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setSchemaLocations(List.of(location));
        settings.setEncoding(StandardCharsets.UTF_16LE);
        settings.setSeparator("@@");
        settings.setContinueOnError(true);

        try (Connection connection = openDatabase()) {
            JdbcInitializer initializer = new JdbcInitializer(connection, settings);

            assertTrue(initializer.initializeDatabase());
            assertEquals("caf\u00e9", query(connection, "SELECT body FROM messages"));
        }
    }

    @Test
    void stopsExecutingStatementsOnErrorByDefault() throws Exception {
        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setSchemaLocations(List.of(script("schema.sql", """
                CREATE TABLE messages (body VARCHAR(100));
                INSERT INTO absent_table VALUES (1);
                INSERT INTO messages VALUES ('not inserted');
                """)));

        try (Connection connection = openDatabase()) {
            JdbcInitializer initializer = new JdbcInitializer(connection, settings);

            assertThrows(ScriptStatementFailedException.class, initializer::initializeDatabase);
            assertEquals("0", query(connection, "SELECT COUNT(*) FROM messages"));
        }
    }

    private String script(String name, String content) throws IOException {
        return script(name, content, StandardCharsets.UTF_8);
    }

    private String script(String name, String content, Charset encoding) throws IOException {
        Path path = directory.resolve(name);
        Files.writeString(path, content, encoding);
        return path.toUri().toString();
    }

    private static Connection openDatabase() throws SQLException {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        return dataSource.getConnection();
    }

    private static String query(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            String value = result.getString(1);
            assertFalse(result.next());
            return value;
        }
    }

    private static final class JdbcInitializer extends AbstractScriptDatabaseInitializer {
        private final SingleConnectionDataSource dataSource;
        private int executedBatches;

        private JdbcInitializer(Connection connection, DatabaseInitializationSettings settings) {
            super(settings);
            this.dataSource = new SingleConnectionDataSource(connection, true);
            setResourceLoader(new DefaultResourceLoader());
        }

        @Override
        protected boolean isEmbeddedDatabase() {
            return true;
        }

        @Override
        protected void runScripts(Scripts scripts) {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.setContinueOnError(scripts.isContinueOnError());
            populator.setSeparator(scripts.getSeparator());
            if (scripts.getEncoding() != null) {
                populator.setSqlScriptEncoding(scripts.getEncoding().name());
            }
            for (Resource resource : scripts) {
                populator.addScript(resource);
            }
            populator.execute(this.dataSource);
            this.executedBatches++;
        }
    }
}
