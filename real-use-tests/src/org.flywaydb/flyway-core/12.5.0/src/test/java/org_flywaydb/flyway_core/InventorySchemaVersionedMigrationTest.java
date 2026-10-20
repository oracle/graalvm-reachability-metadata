/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_flywaydb.flyway_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
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
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.CoreMigrationType;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.output.ValidateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Versioned and repeatable SQL migrations: discovering scripts in a filesystem location,
 * applying them in version order with placeholder substitution, recording them in the schema
 * history table, and reporting pending, applied, and outdated migrations through `info` and
 * `validate`.
 *
 * An inventory service ships its schema as SQL scripts next to the application. The first
 * release creates the product table and seeds it, a later release adds a stock column, and a
 * repeatable script keeps a reporting view in sync. The service migrates an H2 database on
 * startup, then a new release drops another script into the folder and the view is redefined.
 */
public class InventorySchemaVersionedMigrationTest {

    @TempDir
    Path migrations;

    private String jdbcUrl;

    @BeforeEach
    void createDatabaseName() {
        jdbcUrl = "jdbc:h2:mem:inventory_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
    }

    @Test
    void migratesReportsAndEvolvesInventorySchema() throws Exception {
        writeScript("V1__create_products.sql", """
                CREATE TABLE ${tablePrefix}product (
                    id INT PRIMARY KEY,
                    name VARCHAR(100) NOT NULL
                );
                """);
        writeScript("V1_1__seed_products.sql", """
                INSERT INTO ${tablePrefix}product (id, name) VALUES (1, 'Anvil');
                INSERT INTO ${tablePrefix}product (id, name) VALUES (2, 'Rocket Skates');
                """);
        writeScript("R__product_report.sql", """
                CREATE OR REPLACE VIEW product_report AS
                    SELECT id, UPPER(name) AS label FROM ${tablePrefix}product;
                """);

        Flyway flyway = configure();

        MigrationInfoService pendingInfo = flyway.info();
        assertNull(pendingInfo.current());
        assertEquals(List.of("1", "1.1", "null"), versions(pendingInfo.pending()));
        assertFalse(flyway.validateWithResult().validationSuccessful);

        MigrateResult firstRun = flyway.migrate();
        assertTrue(firstRun.success);
        assertEquals(3, firstRun.migrationsExecuted);
        assertEquals("1.1", firstRun.targetSchemaVersion);
        assertNull(firstRun.initialSchemaVersion);

        MigrationInfoService appliedInfo = flyway.info();
        assertEquals("1.1", appliedInfo.current().getVersion().getVersion());
        assertEquals(0, appliedInfo.pending().length);
        MigrationInfo[] applied = appliedInfo.applied();
        assertEquals(3, applied.length);
        assertEquals("create products", applied[0].getDescription());
        assertEquals(CoreMigrationType.SQL, applied[0].getType());
        assertEquals("V1__create_products.sql", applied[0].getScript());
        assertEquals(MigrationState.SUCCESS, applied[1].getState());
        assertNull(applied[2].getVersion());
        assertEquals("product report", applied[2].getDescription());
        assertEquals(List.of("ANVIL", "ROCKET SKATES"), queryLabels());

        ValidateResult validation = flyway.validateWithResult();
        assertTrue(validation.validationSuccessful);
        assertTrue(validation.invalidMigrations.isEmpty());

        MigrateResult idleRun = flyway.migrate();
        assertEquals(0, idleRun.migrationsExecuted);

        writeScript("V2__add_stock.sql", """
                ALTER TABLE ${tablePrefix}product ADD COLUMN stock INT DEFAULT 0 NOT NULL;
                UPDATE ${tablePrefix}product SET stock = 7 WHERE id = 1;
                """);
        writeScript("R__product_report.sql", """
                CREATE OR REPLACE VIEW product_report AS
                    SELECT id, UPPER(name) || ' x' || stock AS label FROM ${tablePrefix}product;
                """);

        MigrationInfoService upgradeInfo = configure().info();
        assertEquals(List.of("2", "null"), versions(upgradeInfo.pending()));
        long outdated = countState(upgradeInfo.all(), MigrationState.OUTDATED);
        assertEquals(1, outdated);

        MigrateResult upgrade = configure().migrate();
        assertTrue(upgrade.success);
        assertEquals("1.1", upgrade.initialSchemaVersion);
        assertEquals("2", upgrade.targetSchemaVersion);
        assertEquals(2, upgrade.migrationsExecuted);
        assertEquals(List.of("ANVIL x7", "ROCKET SKATES x0"), queryLabels());

        assertEquals(List.of(
                "V1__create_products.sql",
                "V1_1__seed_products.sql",
                "R__product_report.sql",
                "V2__add_stock.sql",
                "R__product_report.sql"), historyScripts());
        assertEquals(1, countState(configure().info().all(), MigrationState.SUPERSEDED));
    }

    private Flyway configure() {
        return Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .locations("filesystem:" + migrations.toAbsolutePath())
                .placeholders(Map.of("tablePrefix", "inv_"))
                .load();
    }

    private void writeScript(String fileName, String sql) throws IOException {
        Files.writeString(migrations.resolve(fileName), sql, StandardCharsets.UTF_8);
    }

    private static List<String> versions(MigrationInfo[] infos) {
        List<String> versions = new ArrayList<>();
        for (MigrationInfo info : infos) {
            versions.add(String.valueOf(info.getVersion()));
        }
        return versions;
    }

    private static long countState(MigrationInfo[] infos, MigrationState state) {
        long count = 0;
        for (MigrationInfo info : infos) {
            if (info.getState() == state) {
                count++;
            }
        }
        return count;
    }

    private List<String> queryLabels() throws SQLException {
        return queryStrings("SELECT label FROM product_report ORDER BY id");
    }

    private List<String> historyScripts() throws SQLException {
        return queryStrings("""
                SELECT "script" FROM "flyway_schema_history"
                WHERE "type" = 'SQL' AND "success"
                ORDER BY "installed_rank"
                """);
    }

    private List<String> queryStrings(String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                values.add(String.valueOf(resultSet.getString(1)));
            }
        }
        return values;
    }
}
