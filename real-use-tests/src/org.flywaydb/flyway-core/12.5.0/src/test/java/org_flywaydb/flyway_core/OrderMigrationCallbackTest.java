/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_flywaydb.flyway_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.callback.BaseCallback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.output.OperationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Migration lifecycle callbacks: Java `BaseCallback` hooks registered through `callbacks`
 * and SQL callback scripts such as `beforeMigrate.sql` and `afterEachMigrate.sql` discovered
 * next to the migrations, firing around migrate and validate, sharing the migration
 * connection, seeing the migration being applied and the operation result, and reporting
 * a failed migration through the error events.
 *
 * An order service audits every schema change: SQL callbacks shipped with the scripts keep an
 * audit table and a statistics table up to date, and a Java callback records which migration
 * ran, writes it to the audit table on the same connection, and hands the operation result to
 * the deployment log. A later release ships a broken script, and the callbacks observe the
 * failure instead of the success events.
 */
public class OrderMigrationCallbackTest {

    @TempDir
    Path scripts;

    private String jdbcUrl;

    @BeforeEach
    void createDatabaseName() {
        jdbcUrl = "jdbc:h2:mem:orders_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
    }

    @Test
    void callbacksAuditMigrationsAndObserveFailure() throws Exception {
        writeScript("beforeMigrate.sql", """
                CREATE TABLE IF NOT EXISTS migration_audit (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    source VARCHAR(20) NOT NULL,
                    note VARCHAR(100) NOT NULL
                );
                """);
        writeScript("afterEachMigrate.sql", """
                INSERT INTO migration_audit (source, note) VALUES ('sql', '${auditTag}');
                """);
        writeScript("afterMigrate.sql", """
                CREATE TABLE IF NOT EXISTS order_stats (total INT NOT NULL);
                DELETE FROM order_stats;
                INSERT INTO order_stats (total) SELECT COUNT(*) FROM customer_order;
                """);
        writeScript("V1__create_orders.sql", """
                CREATE TABLE customer_order (
                    id INT PRIMARY KEY,
                    customer VARCHAR(50) NOT NULL
                );
                INSERT INTO customer_order (id, customer) VALUES (1, 'Ada');
                """);
        writeScript("V2__add_amount.sql", """
                ALTER TABLE customer_order ADD COLUMN amount INT DEFAULT 0 NOT NULL;
                INSERT INTO customer_order (id, customer, amount) VALUES (2, 'Grace', 40);
                """);

        DeploymentLog log = new DeploymentLog();
        Flyway flyway = configure(log);

        MigrateResult firstRun = flyway.migrate();
        assertTrue(firstRun.success);
        assertEquals(2, firstRun.migrationsExecuted);
        assertEquals(List.of(
                "beforeValidate",
                "afterValidate",
                "beforeMigrate",
                "beforeEachMigrate 1",
                "afterEachMigrate 1",
                "beforeEachMigrate 2",
                "afterEachMigrate 2",
                "afterMigrate",
                "afterMigrateOperationFinish executed=2"), log.events());
        assertInstanceOf(MigrateResult.class, log.lastResult());

        assertEquals(List.of("create orders", "add amount"), queryStrings("""
                SELECT note FROM migration_audit WHERE source = 'java' ORDER BY id
                """));
        assertEquals(List.of("release-7", "release-7"), queryStrings("""
                SELECT note FROM migration_audit WHERE source = 'sql' ORDER BY id
                """));
        assertEquals(List.of("2"), queryStrings("SELECT total FROM order_stats"));

        log.clear();
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(List.of("beforeValidate", "afterValidate"), log.events());

        log.clear();
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals("afterMigrateOperationFinish executed=0",
                log.events().get(log.events().size() - 1));
        assertEquals(List.of("2"), queryStrings("SELECT total FROM order_stats"));

        writeScript("V3__archive_orders.sql", """
                INSERT INTO order_archive (id) SELECT id FROM customer_order;
                """);
        DeploymentLog brokenLog = new DeploymentLog();
        FlywayException failure = assertThrows(FlywayException.class,
                () -> configure(brokenLog).migrate());
        assertTrue(failure.getMessage().contains("ORDER_ARCHIVE"), failure.getMessage());
        assertTrue(brokenLog.events().contains("beforeEachMigrate 3"),
                brokenLog.events().toString());
        assertTrue(brokenLog.events().contains("afterEachMigrateError 3"),
                brokenLog.events().toString());
        assertTrue(brokenLog.events().contains("afterMigrateError"), brokenLog.events().toString());
        assertFalse(brokenLog.events().contains("afterMigrate"), brokenLog.events().toString());

        MigrationInfo current = configure(new DeploymentLog()).info().current();
        assertEquals("3", current.getVersion().getVersion());
        assertEquals(2, queryStrings("""
                SELECT note FROM migration_audit WHERE source = 'java'
                """).size());
    }

    private Flyway configure(DeploymentLog log) {
        return Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .locations("filesystem:" + scripts.toAbsolutePath())
                .placeholders(Map.of("auditTag", "release-7"))
                .callbacks(log)
                .load();
    }

    private void writeScript(String fileName, String sql) throws IOException {
        Files.writeString(scripts.resolve(fileName), sql, StandardCharsets.UTF_8);
    }

    private List<String> queryStrings(String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
        }
        return values;
    }

    /** Records lifecycle events and audits each applied migration on its own connection. */
    public static class DeploymentLog extends BaseCallback {
        private static final Set<Event> HANDLED = EnumSet.of(
                Event.BEFORE_MIGRATE,
                Event.BEFORE_EACH_MIGRATE,
                Event.AFTER_EACH_MIGRATE,
                Event.AFTER_EACH_MIGRATE_ERROR,
                Event.AFTER_MIGRATE,
                Event.AFTER_MIGRATE_ERROR,
                Event.AFTER_MIGRATE_OPERATION_FINISH,
                Event.BEFORE_VALIDATE,
                Event.AFTER_VALIDATE);

        private final List<String> events = new ArrayList<>();
        private OperationResult lastResult;

        @Override
        public boolean supports(Event event, Context context) {
            return HANDLED.contains(event);
        }

        @Override
        public void handle(Event event, Context context) {
            switch (event) {
                case BEFORE_EACH_MIGRATE, AFTER_EACH_MIGRATE_ERROR ->
                        events.add(event.getId() + " " + context.getMigrationInfo().getVersion());
                case AFTER_EACH_MIGRATE -> {
                    MigrationInfo migration = context.getMigrationInfo();
                    events.add(event.getId() + " " + migration.getVersion());
                    audit(context.getConnection(), migration.getDescription());
                }
                case AFTER_MIGRATE_OPERATION_FINISH -> {
                    lastResult = context.getOperationResult();
                    MigrateResult result = (MigrateResult) lastResult;
                    events.add(event.getId() + " executed=" + result.migrationsExecuted);
                }
                default -> events.add(event.getId());
            }
        }

        private static void audit(Connection connection, String description) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO migration_audit (source, note) VALUES ('java', ?)")) {
                insert.setString(1, description);
                insert.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Cannot audit migration " + description, e);
            }
        }

        List<String> events() {
            return events;
        }

        OperationResult lastResult() {
            return lastResult;
        }

        void clear() {
            events.clear();
        }
    }
}
