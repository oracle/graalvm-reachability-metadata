/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_flywaydb.flyway_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import org.flywaydb.core.api.CoreMigrationType;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Schema history table configuration: giving each module its own history table with
 * `table`, recording the deploying identity with `installedBy`, preparing every Flyway
 * connection with `initSql`, and reading back the recorded rank, script, checksum, and
 * installer through `info` and the history table itself.
 *
 * A modular monolith keeps its catalog and order modules in one database schema, and each
 * module ships its own versioned scripts starting at V1. Every module migrates against a
 * history table of its own so the two version lines never collide, the order module joins
 * the schema the catalog already populated by baselining its own table at version 0 and
 * tags the rows it writes with the deployment region set up by `initSql`, and a deployment that
 * forgets the module's table name is refused instead of re-running the module's scripts.
 */
public class ModularMonolithSchemaHistoryTest {

    private static final String USER = "sa";

    @TempDir
    Path workspace;

    @Test
    void keepsSeparateHistoryPerModule() throws Exception {
        String url = "jdbc:h2:mem:monolith_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        Path catalogScripts = writeCatalogModule();
        Path orderScripts = writeOrderModule();

        Flyway catalog = Flyway.configure()
                .dataSource(url, USER, "")
                .locations("filesystem:" + catalogScripts.toAbsolutePath())
                .table("catalog_schema_history")
                .installedBy("catalog-pipeline")
                .load();
        MigrateResult catalogResult = catalog.migrate();
        assertTrue(catalogResult.success);
        assertEquals(2, catalogResult.migrationsExecuted);
        assertEquals("2", catalogResult.targetSchemaVersion);

        Flyway orders = Flyway.configure()
                .dataSource(url, USER, "")
                .locations("filesystem:" + orderScripts.toAbsolutePath())
                .table("order_schema_history")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .baselineDescription("Shared monolith schema")
                .initSql("SET @deployment_region = 'eu-west-1'")
                .load();
        MigrateResult ordersResult = orders.migrate();
        assertTrue(ordersResult.success);
        assertEquals(CoreMigrationType.BASELINE, orders.info().applied()[0].getType());
        assertEquals("0", ordersResult.initialSchemaVersion);
        assertEquals(3, ordersResult.migrationsExecuted);
        assertEquals("3", ordersResult.targetSchemaVersion);

        assertEquals(List.of("catalog_schema_history", "order_schema_history"),
                historyTables(url));
        assertEquals(List.of("-1:TABLE:catalog-pipeline", "1:SQL:catalog-pipeline",
                "2:SQL:catalog-pipeline"), historyRows(url, "catalog_schema_history"));
        assertEquals(List.of("-1:TABLE:SA", "1:BASELINE:SA", "2:SQL:SA", "3:SQL:SA",
                "4:SQL:SA"),
                historyRows(url, "order_schema_history"));
        assertEquals(List.of("ORD-1:eu-west-1:2", "ORD-2:eu-west-1:1"),
                queryStrings(url, """
                        SELECT o.code || ':' || o.region || ':' || COUNT(l.sku)
                        FROM customer_order o JOIN order_line l ON l.order_code = o.code
                        GROUP BY o.code, o.region ORDER BY o.code
                        """));

        MigrationInfoService catalogInfo = catalog.info();
        assertEquals("2", catalogInfo.current().getVersion().getVersion());
        assertEquals(0, catalogInfo.pending().length);
        assertEquals(List.of("1:SQL:create products:catalog-pipeline:1",
                "2:SQL:seed launch range:catalog-pipeline:2"), applied(catalogInfo));
        assertChecksumsRecorded(url, "catalog_schema_history", catalogInfo);

        MigrationInfoService ordersInfo = orders.info();
        assertEquals("3", ordersInfo.current().getVersion().getVersion());
        assertEquals(List.of("0:BASELINE:Shared monolith schema:SA:1",
                "1:SQL:create orders:SA:2", "2:SQL:create order lines:SA:3",
                "3:SQL:import open orders:SA:4"), applied(ordersInfo));
        assertChecksumsRecorded(url, "order_schema_history", ordersInfo);
        assertTrue(orders.validateWithResult().validationSuccessful);
        assertTrue(catalog.validateWithResult().validationSuccessful);

        writeScript(catalogScripts, "V3__add_product_weight.sql", """
                ALTER TABLE product ADD COLUMN weight_grams INT DEFAULT 0 NOT NULL;
                UPDATE product SET weight_grams = 250 WHERE sku = 'MUG-01';
                """);
        MigrateResult catalogUpgrade = catalog.migrate();
        assertEquals("2", catalogUpgrade.initialSchemaVersion);
        assertEquals(1, catalogUpgrade.migrationsExecuted);
        assertEquals(0, orders.migrate().migrationsExecuted);
        assertEquals(5, historyRows(url, "order_schema_history").size());
        assertEquals(List.of("MUG-01:250", "TEE-01:0"), queryStrings(url,
                "SELECT sku || ':' || weight_grams FROM product ORDER BY sku"));

        Flyway forgotTable = Flyway.configure()
                .dataSource(url, USER, "")
                .locations("filesystem:" + orderScripts.toAbsolutePath())
                .load();
        assertEquals(0, forgotTable.info().applied().length);
        assertEquals(3, forgotTable.info().pending().length);
        FlywayException refused = assertThrows(FlywayException.class, forgotTable::migrate);
        assertTrue(refused.getMessage().contains("non-empty schema"), refused.getMessage());
        assertEquals(List.of("catalog_schema_history", "order_schema_history"),
                historyTables(url));
        assertEquals(List.of("ORD-1", "ORD-2"),
                queryStrings(url, "SELECT code FROM customer_order ORDER BY code"));
    }

    private Path writeCatalogModule() throws IOException {
        Path scripts = Files.createDirectories(workspace.resolve("catalog"));
        writeScript(scripts, "V1__create_products.sql", """
                CREATE TABLE product (
                    sku VARCHAR(20) PRIMARY KEY,
                    title VARCHAR(100) NOT NULL
                );
                """);
        writeScript(scripts, "V2__seed_launch_range.sql", """
                INSERT INTO product (sku, title) VALUES ('MUG-01', 'Enamel mug');
                INSERT INTO product (sku, title) VALUES ('TEE-01', 'Logo tee');
                """);
        return scripts;
    }

    private Path writeOrderModule() throws IOException {
        Path scripts = Files.createDirectories(workspace.resolve("orders"));
        writeScript(scripts, "V1__create_orders.sql", """
                CREATE TABLE customer_order (
                    code VARCHAR(20) PRIMARY KEY,
                    region VARCHAR(20) NOT NULL
                );
                """);
        writeScript(scripts, "V2__create_order_lines.sql", """
                CREATE TABLE order_line (
                    order_code VARCHAR(20) NOT NULL REFERENCES customer_order (code),
                    sku VARCHAR(20) NOT NULL REFERENCES product (sku),
                    PRIMARY KEY (order_code, sku)
                );
                """);
        writeScript(scripts, "V3__import_open_orders.sql", """
                INSERT INTO customer_order (code, region) VALUES ('ORD-1', @deployment_region);
                INSERT INTO customer_order (code, region) VALUES ('ORD-2', @deployment_region);
                INSERT INTO order_line (order_code, sku) VALUES ('ORD-1', 'MUG-01');
                INSERT INTO order_line (order_code, sku) VALUES ('ORD-1', 'TEE-01');
                INSERT INTO order_line (order_code, sku) VALUES ('ORD-2', 'MUG-01');
                """);
        return scripts;
    }

    private static List<String> applied(MigrationInfoService info) {
        List<String> rows = new ArrayList<>();
        for (MigrationInfo migration : info.applied()) {
            assertNotNull(migration.getInstalledOn());
            assertTrue(migration.getExecutionTime() >= 0);
            rows.add(migration.getVersion() + ":" + migration.getType() + ":"
                    + migration.getDescription() + ":"
                    + migration.getInstalledBy() + ":" + migration.getInstalledRank());
        }
        return rows;
    }

    private static void assertChecksumsRecorded(String url, String table, MigrationInfoService info)
            throws SQLException {
        List<String> expected = new ArrayList<>();
        for (MigrationInfo migration : info.applied()) {
            if (migration.getType() != CoreMigrationType.SQL) {
                continue;
            }
            assertNotNull(migration.getChecksum());
            expected.add(migration.getScript() + "=" + migration.getChecksum());
        }
        assertEquals(expected, queryStrings(url, """
                SELECT "script" || '=' || "checksum" FROM "%s"
                WHERE "type" = 'SQL' AND "success" ORDER BY "installed_rank"
                """.formatted(table)));
    }

    private static List<String> historyTables(String url) throws SQLException {
        return queryStrings(url, """
                SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME LIKE '%schema_history'
                ORDER BY TABLE_NAME
                """);
    }

    private static List<String> historyRows(String url, String table) throws SQLException {
        return queryStrings(url, """
                SELECT "installed_rank" || ':' || "type" || ':' || "installed_by" FROM "%s"
                ORDER BY "installed_rank"
                """.formatted(table));
    }

    private static List<String> queryStrings(String url, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(url, USER, "");
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
        }
        return values;
    }

    private static void writeScript(Path directory, String fileName, String sql)
            throws IOException {
        Files.writeString(directory.resolve(fileName), sql, StandardCharsets.UTF_8);
    }
}
