/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_flywaydb.flyway_core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Externalized configuration: loading Flyway settings from a `flyway.conf` file in the
 * application's working directory with `loadDefaultConfigurationFiles`, layering
 * deployment-specific `flyway.*` properties on top with `configuration`, and rejecting a
 * misspelled `flyway.*` key.
 *
 * A shipping service is deployed with a `flyway.conf` next to it that names the database, a
 * custom history table and schema, its own script naming convention (`S` prefix, `.ddl`
 * suffix), a Latin-1 script encoding, and custom placeholder delimiters. Each deployment then
 * overrides the region placeholder and the `installedBy` user through properties, the way
 * an operator passes system properties to the service.
 */
public class ShippingConfigurationFileMigrationTest {

    private static final Charset LATIN_1 = StandardCharsets.ISO_8859_1;

    @TempDir
    Path workingDirectory;

    @Test
    void migratesWithSettingsFromConfigurationFileAndOverrides() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:shipping_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        Path scripts = Files.createDirectories(workingDirectory.resolve("db"));
        writeScript(scripts, "S1__create_carriers.ddl", """
                CREATE TABLE carrier (
                    code VARCHAR(10) PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    region VARCHAR(10) NOT NULL
                );
                """);
        writeScript(scripts, "S2__seed_carriers.ddl", """
                INSERT INTO carrier (code, name, region)
                    VALUES ('LP', 'La Poste Exp\u00e9dition', '%{region}');
                INSERT INTO carrier (code, name, region)
                    VALUES ('DH', '%{partner}', '%{region}');
                """);
        writeScript(scripts, "V3__ignored_default_prefix.sql", """
                CREATE TABLE should_not_exist (id INT);
                """);
        Files.writeString(workingDirectory.resolve("flyway.conf"), """
                # Shipping service database settings
                flyway.url=%s
                flyway.user=sa
                flyway.password=
                flyway.locations=filesystem:%s
                flyway.schemas=SHIPPING
                flyway.createSchemas=true
                flyway.table=shipping_schema_history
                flyway.sqlMigrationPrefix=S
                flyway.sqlMigrationSuffixes=.ddl
                flyway.encoding=ISO-8859-1
                flyway.placeholderPrefix=%%{
                flyway.placeholderSuffix=}
                flyway.placeholders.region=EU
                flyway.placeholders.partner=DHL Parcel
                flyway.installedBy=packaged-default
                """.formatted(jdbcUrl, scripts.toAbsolutePath()), StandardCharsets.UTF_8);

        Properties deployment = new Properties();
        deployment.setProperty("flyway.placeholders.region", "NA");
        deployment.setProperty("flyway.installedBy", "release-bot");

        Flyway flyway = Flyway.configure()
                .workingDirectory(workingDirectory.toString())
                .loadDefaultConfigurationFiles("UTF-8")
                .configuration(deployment)
                .load();

        Configuration configuration = flyway.getConfiguration();
        assertEquals("shipping_schema_history", configuration.getTable());
        assertEquals("S", configuration.getSqlMigrationPrefix());
        assertArrayEquals(new String[] {".ddl"}, configuration.getSqlMigrationSuffixes());
        assertEquals(LATIN_1, configuration.getEncoding());
        assertEquals("%{", configuration.getPlaceholderPrefix());
        assertEquals("NA", configuration.getPlaceholders().get("region"));
        assertEquals("DHL Parcel", configuration.getPlaceholders().get("partner"));
        assertEquals("release-bot", configuration.getInstalledBy());
        assertArrayEquals(new String[] {"SHIPPING"}, configuration.getSchemas());

        MigrateResult result = flyway.migrate();
        assertTrue(result.success);
        assertEquals(2, result.migrationsExecuted);
        assertEquals("2", result.targetSchemaVersion);
        assertEquals("SHIPPING", result.schemaName);

        List<String> descriptions = new ArrayList<>();
        for (MigrationInfo info : flyway.info().applied()) {
            String version = info.getVersion() == null ? "-" : info.getVersion().getVersion();
            descriptions.add(version + " " + info.getDescription());
        }
        assertEquals(List.of(
                "- << Flyway Schema Creation >>",
                "1 create carriers",
                "2 seed carriers"), descriptions);
        assertTrue(flyway.validateWithResult().validationSuccessful);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            assertEquals(List.of(
                    "DH|DHL Parcel|NA",
                    "LP|La Poste Exp\u00e9dition|NA"), queryRows(connection, """
                    SELECT code, name, region FROM SHIPPING.carrier ORDER BY code
                    """));
            assertEquals(List.of(
                    "release-bot|1|TRUE",
                    "release-bot|2|TRUE"), queryRows(connection, """
                    SELECT "installed_by", "version", "success"
                    FROM SHIPPING."shipping_schema_history"
                    WHERE "version" IS NOT NULL ORDER BY "installed_rank"
                    """));
            assertEquals(List.of("0"), queryRows(connection, """
                    SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                    WHERE TABLE_NAME IN ('SHOULD_NOT_EXIST', 'flyway_schema_history')
                    """));
        }
    }

    @Test
    void rejectsMisspelledConfigurationProperty() {
        Map<String, String> settings = Map.of(
                "flyway.url", "jdbc:h2:mem:shipping_typo_" + System.nanoTime(),
                "flyway.user", "sa",
                "flyway.baselineOnMigrat", "true");

        FlywayException failure = assertThrows(FlywayException.class,
                () -> Flyway.configure().configuration(settings).load());
        assertTrue(failure.getMessage().contains("flyway.baselineOnMigrat"), failure.getMessage());
    }

    private static void writeScript(Path directory, String name, String body) throws IOException {
        Files.writeString(directory.resolve(name), body, LATIN_1);
    }

    private static List<String> queryRows(Connection connection, String sql) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            int columns = resultSet.getMetaData().getColumnCount();
            while (resultSet.next()) {
                List<String> values = new ArrayList<>();
                for (int column = 1; column <= columns; column++) {
                    values.add(resultSet.getString(column));
                }
                rows.add(String.join("|", values));
            }
        }
        return rows;
    }
}
