/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_flywaydb.flyway_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
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
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.migration.baseline.BaselineJavaMigration;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Baseline migrations: `B`-prefixed SQL baseline scripts and `BaselineJavaMigration`
 * classes that consolidate a long migration history into one snapshot, applied instead of
 * the versioned scripts below them on a fresh database, ignored on a database that already
 * has history, and recognised under a custom `flyway.baselineMigrationPrefix`.
 *
 * A product catalog service has accumulated three versioned scripts. Its team squashes them
 * into a `B3` baseline so that new installations start from the snapshot, while existing
 * installations keep their incremental history and continue with the scripts above it.
 */
public class CatalogBaselineMigrationTest {

    private static final String V1 = """
            CREATE TABLE product (
                sku VARCHAR(20) PRIMARY KEY,
                name VARCHAR(100) NOT NULL
            );
            INSERT INTO product (sku, name) VALUES ('LAMP-01', 'Desk lamp');
            """;

    private static final String V2 = """
            CREATE TABLE category (
                code VARCHAR(20) PRIMARY KEY,
                title VARCHAR(100) NOT NULL
            );
            INSERT INTO category (code, title) VALUES ('HOME', 'Home and living');
            ALTER TABLE product ADD COLUMN category_code VARCHAR(20) DEFAULT 'HOME' NOT NULL;
            """;

    private static final String V3 = """
            ALTER TABLE product ADD COLUMN price_cents INT DEFAULT 0 NOT NULL;
            UPDATE product SET price_cents = 2999 WHERE sku = 'LAMP-01';
            """;

    private static final String B3 = """
            -- Consolidated catalog schema equivalent to V1 to V3
            CREATE TABLE category (
                code VARCHAR(20) PRIMARY KEY,
                title VARCHAR(100) NOT NULL
            );
            CREATE TABLE product (
                sku VARCHAR(20) PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                category_code VARCHAR(20) DEFAULT 'HOME' NOT NULL,
                price_cents INT DEFAULT 0 NOT NULL
            );
            INSERT INTO category (code, title) VALUES ('HOME', 'Home and living');
            INSERT INTO product (sku, name, category_code, price_cents)
                VALUES ('LAMP-01', 'Desk lamp', 'HOME', 2999);
            """;

    private static final String V4 = """
            CREATE TABLE stock_level (
                sku VARCHAR(20) PRIMARY KEY REFERENCES product (sku),
                on_hand INT NOT NULL
            );
            INSERT INTO stock_level (sku, on_hand) VALUES ('LAMP-01', 12);
            """;

    @TempDir
    Path workingDirectory;

    @Test
    void freshInstallationStartsFromLatestBaselineMigration() throws Exception {
        Path scripts = Files.createDirectories(workingDirectory.resolve("catalog"));
        writeScript(scripts, "V1__create_products.sql", V1);
        writeScript(scripts, "V2__add_categories.sql", V2);
        writeScript(scripts, "V3__add_prices.sql", V3);
        writeScript(scripts, "B3__catalog_baseline.sql", B3);
        writeScript(scripts, "V4__add_stock_levels.sql", V4);
        String jdbcUrl = newDatabaseUrl("catalog_fresh");
        Flyway flyway = catalogFlyway(jdbcUrl, scripts).load();

        MigrateResult result = flyway.migrate();
        assertTrue(result.success);
        assertEquals(2, result.migrationsExecuted);
        assertEquals(null, result.initialSchemaVersion);
        assertEquals("4", result.targetSchemaVersion);

        assertEquals(List.of(
                "1|create products|BELOW_BASELINE",
                "2|add categories|BELOW_BASELINE",
                "3|add prices|BASELINE_IGNORED",
                "3|catalog baseline|BASELINE",
                "4|add stock levels|SUCCESS"), describe(flyway.info().all()));
        assertEquals("4", flyway.info().current().getVersion().getVersion());
        assertTrue(flyway.validateWithResult().validationSuccessful);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            assertEquals(List.of(
                    "3|SQL_BASELINE|B3__catalog_baseline.sql",
                    "4|SQL|V4__add_stock_levels.sql"), queryRows(connection, """
                    SELECT "version", "type", "script" FROM "flyway_schema_history"
                    WHERE "version" IS NOT NULL ORDER BY "installed_rank"
                    """));
            assertEquals(List.of("LAMP-01|Desk lamp|HOME|2999|12"), queryRows(connection, """
                    SELECT p.sku, p.name, p.category_code, p.price_cents, s.on_hand
                    FROM product p JOIN stock_level s ON s.sku = p.sku
                    """));
        }
    }

    @Test
    void existingInstallationKeepsIncrementalHistory() throws Exception {
        Path firstRelease = Files.createDirectories(workingDirectory.resolve("release-1"));
        writeScript(firstRelease, "V1__create_products.sql", V1);
        writeScript(firstRelease, "V2__add_categories.sql", V2);
        String jdbcUrl = newDatabaseUrl("catalog_existing");

        MigrateResult firstDeployment = catalogFlyway(jdbcUrl, firstRelease).load().migrate();
        assertEquals(2, firstDeployment.migrationsExecuted);

        Path secondRelease = Files.createDirectories(workingDirectory.resolve("release-2"));
        writeScript(secondRelease, "V1__create_products.sql", V1);
        writeScript(secondRelease, "V2__add_categories.sql", V2);
        writeScript(secondRelease, "V3__add_prices.sql", V3);
        writeScript(secondRelease, "B3__catalog_baseline.sql", B3);
        writeScript(secondRelease, "V4__add_stock_levels.sql", V4);
        Flyway flyway = catalogFlyway(jdbcUrl, secondRelease).load();

        MigrateResult result = flyway.migrate();
        assertTrue(result.success);
        assertEquals(2, result.migrationsExecuted);
        assertEquals("2", result.initialSchemaVersion);
        assertEquals("4", result.targetSchemaVersion);

        assertEquals(List.of(
                "1|create products|SUCCESS",
                "2|add categories|SUCCESS",
                "3|add prices|SUCCESS",
                "4|add stock levels|SUCCESS",
                "3|catalog baseline|IGNORED"), describe(flyway.info().all()));
        assertTrue(flyway.validateWithResult().validationSuccessful);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            assertEquals(List.of(
                    "1|SQL", "2|SQL", "3|SQL", "4|SQL"), queryRows(connection, """
                    SELECT "version", "type" FROM "flyway_schema_history"
                    WHERE "version" IS NOT NULL ORDER BY "installed_rank"
                    """));
            assertEquals(List.of("LAMP-01|HOME|2999|12"), queryRows(connection, """
                    SELECT p.sku, p.category_code, p.price_cents, s.on_hand
                    FROM product p JOIN stock_level s ON s.sku = p.sku
                    """));
        }
    }

    @Test
    void javaBaselineMigrationSeedsCatalogBeforeLaterScripts() throws Exception {
        Path scripts = Files.createDirectories(workingDirectory.resolve("java-baseline"));
        writeScript(scripts, "V1__create_products.sql", V1);
        writeScript(scripts, "V2__add_categories.sql", V2);
        writeScript(scripts, "V3__add_prices.sql", V3);
        String jdbcUrl = newDatabaseUrl("catalog_java");
        Flyway flyway = catalogFlyway(jdbcUrl, scripts)
                .javaMigrations(new B2__Catalog_snapshot())
                .load();

        MigrateResult result = flyway.migrate();
        assertTrue(result.success);
        assertEquals(2, result.migrationsExecuted);
        assertEquals("3", result.targetSchemaVersion);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            assertEquals(List.of(
                    "2|Catalog snapshot|JDBC_BASELINE",
                    "3|add prices|SQL"), queryRows(connection, """
                    SELECT "version", "description", "type" FROM "flyway_schema_history"
                    WHERE "version" IS NOT NULL ORDER BY "installed_rank"
                    """));
            assertEquals(List.of(
                    "DESK-02|Standing desk|OFFICE|0",
                    "LAMP-01|Desk lamp|HOME|2999"), queryRows(connection, """
                    SELECT sku, name, category_code, price_cents FROM product ORDER BY sku
                    """));
        }
    }

    @Test
    void customBaselineMigrationPrefixSelectsSnapshotScripts() throws Exception {
        Path scripts = Files.createDirectories(workingDirectory.resolve("snapshot-prefix"));
        writeScript(scripts, "V1__create_products.sql", V1);
        writeScript(scripts, "V2__add_categories.sql", V2);
        writeScript(scripts, "V3__add_prices.sql", V3);
        writeScript(scripts, "SNAPSHOT3__catalog_baseline.sql", B3);
        writeScript(scripts, "V4__add_stock_levels.sql", V4);
        String jdbcUrl = newDatabaseUrl("catalog_prefix");
        Flyway flyway = Flyway.configure()
                .configuration(Map.of("flyway.baselineMigrationPrefix", "SNAPSHOT"))
                .dataSource(jdbcUrl, "sa", "")
                .locations("filesystem:" + scripts.toAbsolutePath())
                .load();

        MigrateResult result = flyway.migrate();
        assertEquals(2, result.migrationsExecuted);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            assertEquals(List.of(
                    "3|SQL_BASELINE|SNAPSHOT3__catalog_baseline.sql",
                    "4|SQL|V4__add_stock_levels.sql"), queryRows(connection, """
                    SELECT "version", "type", "script" FROM "flyway_schema_history"
                    WHERE "version" IS NOT NULL ORDER BY "installed_rank"
                    """));
        }
    }

    /// Baseline written in Java: builds the catalog schema of V1 and V2 in one step and seeds it.
    public static class B2__Catalog_snapshot extends BaselineJavaMigration {

        @Override
        public void migrate(Context context) throws SQLException {
            try (Statement statement = context.getConnection().createStatement()) {
                statement.execute("""
                        CREATE TABLE category (
                            code VARCHAR(20) PRIMARY KEY,
                            title VARCHAR(100) NOT NULL
                        )
                        """);
                statement.execute("""
                        CREATE TABLE product (
                            sku VARCHAR(20) PRIMARY KEY,
                            name VARCHAR(100) NOT NULL,
                            category_code VARCHAR(20) DEFAULT 'HOME' NOT NULL
                        )
                        """);
                statement.execute("""
                        INSERT INTO category (code, title)
                            VALUES ('HOME', 'Home and living'), ('OFFICE', 'Office')
                        """);
                statement.execute("""
                        INSERT INTO product (sku, name, category_code)
                            VALUES ('LAMP-01', 'Desk lamp', 'HOME'),
                                   ('DESK-02', 'Standing desk', 'OFFICE')
                        """);
            }
        }
    }

    private static FluentConfiguration catalogFlyway(String jdbcUrl, Path scripts) {
        return Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .locations("filesystem:" + scripts.toAbsolutePath());
    }

    private static String newDatabaseUrl(String name) {
        return "jdbc:h2:mem:" + name + "_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
    }

    private static List<String> describe(MigrationInfo[] migrations) {
        List<String> descriptions = new ArrayList<>();
        for (MigrationInfo info : migrations) {
            descriptions.add(info.getVersion().getVersion() + "|" + info.getDescription()
                    + "|" + info.getState().name());
        }
        return descriptions;
    }

    private static void writeScript(Path directory, String name, String body) throws IOException {
        Files.writeString(directory.resolve(name), body);
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
