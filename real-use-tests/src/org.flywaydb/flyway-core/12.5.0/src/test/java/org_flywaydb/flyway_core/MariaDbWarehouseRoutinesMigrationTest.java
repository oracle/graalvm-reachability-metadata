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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.output.CleanResult;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * MariaDB database support: Flyway-created databases as schemas, MySQL-dialect script parsing
 * (`DELIMITER`-delimited stored procedures and triggers, `#` comments, `/*!` versioned
 * executable comments, backtick-quoted identifiers), MariaDB sequences, and `clean` dropping
 * the databases Flyway created together with their tables, views, routines and triggers.
 *
 * A warehouse service keeps stock levels in a `warehouse` database and a movement journal in a
 * `warehouse_journal` database on a real MariaDB server. Its migrations install a sequence for
 * shipment numbers, a trigger that journals every stock change and a stored procedure the
 * application calls to ship goods. A staging environment is later wiped with `clean` and rebuilt
 * from the same scripts.
 */
public class MariaDbWarehouseRoutinesMigrationTest {

    private static final String IMAGE = "mariadb:13.0";

    private static final String ROOT_PASSWORD = "warehouse-secret";

    private static final String CONTAINER = "flyway-mariadb-" + UUID.randomUUID();

    private static String serverUrl;

    @TempDir
    static Path migrations;

    @BeforeAll
    static void startMariaDb() throws Exception {
        docker("run", "-d", "--name", CONTAINER,
                "-e", "MARIADB_ROOT_PASSWORD=" + ROOT_PASSWORD,
                "-p", "127.0.0.1::3306", IMAGE);
        String mapping = docker("port", CONTAINER, "3306/tcp").lines().findFirst().orElseThrow();
        int port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1).trim());
        serverUrl = "jdbc:mariadb://127.0.0.1:" + port + "/";
        awaitDatabase();
        writeMigrations();
    }

    @AfterAll
    static void removeMariaDb() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @Test
    void installsRoutinesAcrossDatabasesAndCleansThemAway() throws SQLException {
        Flyway flyway = configureFlyway();
        MigrateResult result = flyway.migrate();

        assertTrue(result.success);
        assertEquals(4, result.migrationsExecuted);
        assertEquals("3", result.targetSchemaVersion);
        assertEquals("warehouse, warehouse_journal", result.schemaName);

        try (Connection connection = connect()) {
            assertEquals(List.of("warehouse", "warehouse_journal"), queryStrings(connection, """
                    SELECT schema_name FROM information_schema.schemata
                    WHERE schema_name LIKE 'warehouse%' ORDER BY schema_name
                    """));
            assertEquals(List.of("warehouse"), queryStrings(connection, """
                    SELECT table_schema FROM information_schema.tables
                    WHERE table_name = 'flyway_schema_history'
                    """));
            assertEquals(List.of("InnoDB"), queryStrings(connection, """
                    SELECT engine FROM information_schema.tables
                    WHERE table_schema = 'warehouse' AND table_name = 'stock'
                    """));

            assertEquals(1001L, ship(connection, "bolt-m8", 30));
            assertEquals(1002L, ship(connection, "nut-m8", 5));
            assertEquals(1003L, ship(connection, "bolt-m8", 20));

            assertEquals(List.of("bolt-m8|450", "nut-m8|795", "washer-8|1000"),
                    queryStrings(connection, """
                            SELECT CONCAT(sku, '|', quantity) FROM warehouse.`stock` ORDER BY sku
                            """));
            assertEquals(List.of("bolt-m8|-30", "nut-m8|-5", "bolt-m8|-20"),
                    queryStrings(connection, """
                            SELECT CONCAT(sku, '|', delta) FROM warehouse_journal.movement
                            ORDER BY id
                            """));
            assertEquals(List.of("bolt-m8|-50", "nut-m8|-5"), queryStrings(connection, """
                    SELECT CONCAT(sku, '|', net_change) FROM warehouse.stock_movement_summary
                    ORDER BY sku
                    """));
        }

        List<String> applied = new ArrayList<>();
        for (MigrationInfo info : flyway.info().applied()) {
            if (info.getType().isSynthetic()) {
                continue;
            }
            assertEquals(MigrationState.SUCCESS, info.getState());
            applied.add((info.getVersion() == null ? "R" : info.getVersion().getVersion())
                    + " " + info.getDescription());
        }
        assertEquals(List.of(
                "1 stock and journal",
                "2 movement trigger",
                "3 ship procedure",
                "R movement summary view"), applied);

        // Both databases carry Flyway's SCHEMA marker, so clean drops them instead of emptying.
        CleanResult cleaned = flyway.clean();
        assertEquals(List.of("warehouse", "warehouse_journal"), cleaned.schemasDropped.stream()
                .sorted().toList());
        try (Connection connection = connect()) {
            assertEquals(List.of(), queryStrings(connection, """
                    SELECT schema_name FROM information_schema.schemata
                    WHERE schema_name LIKE 'warehouse%'
                    """));
            assertEquals(List.of(), queryStrings(connection, """
                    SELECT CONCAT(table_schema, '.', table_name) FROM information_schema.tables
                    WHERE table_schema LIKE 'warehouse%'
                    """));
            assertEquals(List.of(), queryStrings(connection, """
                    SELECT routine_name FROM information_schema.routines
                    WHERE routine_schema LIKE 'warehouse%'
                    """));
            assertEquals(List.of(), queryStrings(connection, """
                    SELECT trigger_name FROM information_schema.triggers
                    WHERE trigger_schema LIKE 'warehouse%'
                    """));
        }

        MigrateResult rebuilt = configureFlyway().migrate();
        assertTrue(rebuilt.success);
        assertEquals(4, rebuilt.migrationsExecuted);
        try (Connection connection = connect()) {
            assertEquals(1001L, ship(connection, "washer-8", 100));
            assertEquals(List.of("washer-8|-100"), queryStrings(connection, """
                    SELECT CONCAT(sku, '|', net_change) FROM warehouse.stock_movement_summary
                    """));
        }
    }

    private static Flyway configureFlyway() {
        return Flyway.configure()
                .dataSource(serverUrl, "root", ROOT_PASSWORD)
                .schemas("warehouse", "warehouse_journal")
                .createSchemas(true)
                .locations("filesystem:" + migrations.toAbsolutePath())
                .cleanDisabled(false)
                .load();
    }

    private static long ship(Connection connection, String sku, int quantity) throws SQLException {
        try (CallableStatement call =
                connection.prepareCall("{call warehouse.ship_goods(?, ?, ?)}")) {
            call.setString(1, sku);
            call.setInt(2, quantity);
            call.registerOutParameter(3, Types.BIGINT);
            call.execute();
            return call.getLong(3);
        }
    }

    private static void writeMigrations() throws IOException {
        writeScript("V1__stock_and_journal.sql", """
                # Stock levels live in the default schema, the journal in its own database.
                CREATE TABLE `stock` (
                    `sku` VARCHAR(32) NOT NULL PRIMARY KEY,
                    `quantity` INT NOT NULL
                ) /*!40101 ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 */;

                CREATE TABLE warehouse_journal.movement (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    sku VARCHAR(32) NOT NULL,
                    delta INT NOT NULL
                ) ENGINE=InnoDB;

                CREATE SEQUENCE shipment_number START WITH 1001 INCREMENT BY 1;

                INSERT INTO `stock` (`sku`, `quantity`) VALUES
                    ('bolt-m8', 500), ('nut-m8', 800), ('washer-8', 1000);
                """);
        writeScript("V2__movement_trigger.sql", """
                DELIMITER //
                CREATE TRIGGER stock_journal AFTER UPDATE ON `stock`
                FOR EACH ROW
                BEGIN
                    -- Every change in stock is mirrored to the journal database.
                    INSERT INTO warehouse_journal.movement (sku, delta)
                    VALUES (NEW.sku, NEW.quantity - OLD.quantity);
                END//
                DELIMITER ;
                """);
        writeScript("V3__ship_procedure.sql", """
                DELIMITER $$
                CREATE PROCEDURE ship_goods(IN p_sku VARCHAR(32), IN p_quantity INT,
                                            OUT p_shipment BIGINT)
                BEGIN
                    UPDATE `stock` SET quantity = quantity - p_quantity WHERE sku = p_sku;
                    SET p_shipment = NEXT VALUE FOR shipment_number;
                END$$
                DELIMITER ;
                """);
        writeScript("R__movement_summary_view.sql", """
                CREATE OR REPLACE VIEW stock_movement_summary AS
                SELECT sku, SUM(delta) AS net_change
                FROM warehouse_journal.movement
                GROUP BY sku;
                """);
    }

    private static void writeScript(String name, String sql) throws IOException {
        Files.writeString(migrations.resolve(name), sql, StandardCharsets.UTF_8);
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(serverUrl, "root", ROOT_PASSWORD);
    }

    private static List<String> queryStrings(Connection connection, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
        }
        return values;
    }

    private static void awaitDatabase() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        SQLException lastFailure = null;
        while (System.nanoTime() < deadline) {
            try (Connection connection = connect();
                    Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
                return;
            } catch (SQLException e) {
                lastFailure = e;
                Thread.sleep(500);
            }
        }
        throw new IllegalStateException("MariaDB did not accept connections", lastFailure);
    }

    private static String docker(String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(120, TimeUnit.SECONDS), "docker " + arguments[0] + " timed out");
        assertEquals(0, process.exitValue(), output);
        return output;
    }
}
