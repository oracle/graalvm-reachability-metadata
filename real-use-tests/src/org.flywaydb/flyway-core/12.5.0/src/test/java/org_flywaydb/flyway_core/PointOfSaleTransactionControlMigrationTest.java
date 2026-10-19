/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_flywaydb.flyway_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.output.RepairResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Migration transaction control: running a whole release as one transaction with `group`,
 * the default per-migration transactions that keep earlier migrations of a failed release,
 * rejecting a migration that mixes transactional and non-transactional statements unless
 * `mixed` is enabled, and a per-script `.sql.conf` file switching `executeInTransaction` off
 * so a failed migration keeps its partial work and is recorded as `FAILED` until `repair`.
 *
 * A point-of-sale terminal keeps its catalog and sales in a local SQLite file, which supports
 * transactional DDL. Store terminals run with `group` so a broken release leaves the file
 * untouched; back-office terminals migrate one script at a time. Later releases enable
 * foreign keys, which SQLite only accepts outside a transaction, and backfill prices in a
 * script that must not run in a transaction.
 */
public class PointOfSaleTransactionControlMigrationTest {

    @TempDir
    Path migrations;

    @TempDir
    Path terminals;

    @Test
    void controlsTransactionsAcrossReleasesOnStoreAndBackOfficeTerminals() throws Exception {
        writeScript("V1__create_catalog.sql", """
                CREATE TABLE product (
                    id INTEGER PRIMARY KEY,
                    sku TEXT NOT NULL UNIQUE,
                    price_cents INTEGER
                );
                INSERT INTO product (id, sku, price_cents) VALUES (1, 'COFFEE', 250);
                INSERT INTO product (id, sku, price_cents) VALUES (2, 'BAGEL', NULL);
                """);
        writeScript("V2__create_sales.sql", """
                CREATE TABLE sale (
                    id INTEGER PRIMARY KEY,
                    product_id INTEGER NOT NULL REFERENCES product (id),
                    quantity INTEGER NOT NULL
                );
                """);
        writeScript("V3__seed_opening_sale.sql", """
                INSERT INTO sale_line (sale_id, product_id) VALUES (1, 1);
                """);

        String store = url("store.db");
        String backOffice = url("back-office.db");

        FlywayException storeFailure = assertThrows(FlywayException.class,
                configure(store, true, false)::migrate);
        assertTrue(storeFailure.getMessage().contains("sale_line"), storeFailure.getMessage());
        assertEquals(List.of(), tables(store));
        assertEquals(List.of("1:PENDING", "2:PENDING", "3:PENDING"), states(store));

        FlywayException backOfficeFailure = assertThrows(FlywayException.class,
                configure(backOffice, false, false)::migrate);
        assertTrue(backOfficeFailure.getMessage().contains("sale_line"),
                backOfficeFailure.getMessage());
        assertEquals(List.of("product", "sale"), tables(backOffice));
        assertEquals(List.of("1:SUCCESS", "2:SUCCESS", "3:PENDING"), states(backOffice));

        writeScript("V3__seed_opening_sale.sql", """
                INSERT INTO sale (id, product_id, quantity) VALUES (1, 1, 2);
                """);
        MigrateResult storeRelease = configure(store, true, false).migrate();
        assertTrue(storeRelease.success);
        assertEquals(3, storeRelease.migrationsExecuted);
        MigrateResult backOfficeRelease = configure(backOffice, false, false).migrate();
        assertTrue(backOfficeRelease.success);
        assertEquals(1, backOfficeRelease.migrationsExecuted);
        assertEquals("2", backOfficeRelease.initialSchemaVersion);
        assertEquals(List.of("1:2"), strings(backOffice,
                "SELECT product_id || ':' || quantity FROM sale"));

        writeScript("V4__track_refunds.sql", """
                CREATE TABLE refund (
                    id INTEGER PRIMARY KEY,
                    sale_id INTEGER NOT NULL REFERENCES sale (id)
                );
                PRAGMA foreign_keys = ON;
                """);
        FlywayException unmixed = assertThrows(FlywayException.class,
                configure(backOffice, false, false)::migrate);
        assertTrue(unmixed.getMessage().contains("mixed"), unmixed.getMessage());
        assertFalse(tables(backOffice).contains("refund"));
        assertEquals(List.of("1:SUCCESS", "2:SUCCESS", "3:SUCCESS", "4:PENDING"),
                states(backOffice));

        MigrateResult refunds = configure(backOffice, false, true).migrate();
        assertTrue(refunds.success);
        assertEquals("4", refunds.targetSchemaVersion);
        assertTrue(tables(backOffice).contains("refund"));

        writeScript("V5__backfill_prices.sql", """
                UPDATE product SET price_cents = 199 WHERE price_cents IS NULL;
                INSERT INTO price_audit (sku, note) VALUES ('BAGEL', 'backfilled');
                """);
        writeScript("V5__backfill_prices.sql.conf", """
                executeInTransaction=false
                """);
        FlywayException backfillFailure = assertThrows(FlywayException.class,
                configure(backOffice, false, true)::migrate);
        assertTrue(backfillFailure.getMessage().contains("price_audit"),
                backfillFailure.getMessage());
        assertEquals(List.of("BAGEL:199", "COFFEE:250"), prices(backOffice));
        assertEquals(List.of("1:SUCCESS", "2:SUCCESS", "3:SUCCESS", "4:SUCCESS", "5:FAILED"),
                states(backOffice));

        FlywayException blocked = assertThrows(FlywayException.class,
                configure(backOffice, false, true)::migrate);
        assertTrue(blocked.getMessage().contains("failed migration"), blocked.getMessage());

        writeScript("V5__backfill_prices.sql", """
                CREATE TABLE IF NOT EXISTS price_audit (sku TEXT NOT NULL, note TEXT NOT NULL);
                UPDATE product SET price_cents = 199 WHERE price_cents IS NULL;
                INSERT INTO price_audit (sku, note) VALUES ('BAGEL', 'backfilled');
                """);
        RepairResult repaired = configure(backOffice, false, true).repair();
        assertEquals(List.of("5"), repaired.migrationsRemoved.stream()
                .map(removed -> removed.version).toList());
        MigrateResult backfill = configure(backOffice, false, true).migrate();
        assertTrue(backfill.success);
        assertEquals(1, backfill.migrationsExecuted);
        assertEquals(List.of("BAGEL:backfilled"), strings(backOffice,
                "SELECT sku || ':' || note FROM price_audit"));
        assertEquals(List.of("1:SUCCESS", "2:SUCCESS", "3:SUCCESS", "4:SUCCESS", "5:SUCCESS"),
                states(backOffice));
    }

    private Flyway configure(String url, boolean group, boolean mixed) {
        return Flyway.configure()
                .dataSource(url, null, null)
                .locations("filesystem:" + migrations.toAbsolutePath())
                .group(group)
                .mixed(mixed)
                .load();
    }

    private String url(String fileName) {
        return "jdbc:sqlite:" + terminals.resolve(fileName).toAbsolutePath();
    }

    private List<String> states(String url) {
        List<String> states = new ArrayList<>();
        for (MigrationInfo migration : configure(url, false, true).info().all()) {
            states.add(migration.getVersion() + ":" + migration.getState().name());
        }
        return states;
    }

    private static List<String> tables(String url) throws SQLException {
        return strings(url, """
                SELECT name FROM sqlite_master
                WHERE type = 'table' AND name NOT LIKE 'sqlite_%'
                    AND name <> 'flyway_schema_history'
                ORDER BY name
                """);
    }

    private static List<String> prices(String url) throws SQLException {
        return strings(url, "SELECT sku || ':' || price_cents FROM product ORDER BY sku");
    }

    private static List<String> strings(String url, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
        }
        return values;
    }

    private void writeScript(String fileName, String content) throws IOException {
        Files.writeString(migrations.resolve(fileName), content, StandardCharsets.UTF_8);
    }
}
