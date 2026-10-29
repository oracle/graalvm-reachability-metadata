/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import org.h2.Driver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.boot.autoconfigure.sql.init.SqlDataSourceScriptDatabaseInitializer;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationProperties;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class SqlDataSourceScriptDatabaseInitializerTest {

    @Test
    void initializationAppliesConfiguredSchemaAndDataResources(@TempDir Path scripts) throws Exception {
        Path schema = Files.writeString(scripts.resolve("schema.sql"), """
                CREATE TABLE script_items (id INTEGER PRIMARY KEY, label VARCHAR(80))@@
                """);
        Path data = Files.writeString(scripts.resolve("data.sql"), """
                INSERT INTO script_items VALUES (1, 'first orchard')@@
                INSERT INTO script_items VALUES (2, 'second orchard')@@
                """);
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource(new Driver(),
                "jdbc:h2:mem:script_initializer;DB_CLOSE_DELAY=0", "sa", "");
        SqlInitializationProperties properties = new SqlInitializationProperties();
        properties.setMode(DatabaseInitializationMode.ALWAYS);
        properties.setEncoding(StandardCharsets.UTF_8);
        properties.setSeparator("@@");
        properties.setSchemaLocations(List.of(schema.toUri().toString()));
        properties.setDataLocations(List.of(data.toUri().toString()));
        SqlDataSourceScriptDatabaseInitializer initializer =
                new SqlDataSourceScriptDatabaseInitializer(dataSource, properties);

        try (Connection connection = dataSource.getConnection()) {
            initializer.afterPropertiesSet();

            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("SELECT label FROM script_items ORDER BY id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("first orchard");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("second orchard");
                assertThat(rows.next()).isFalse();
            }
        }
    }
}
