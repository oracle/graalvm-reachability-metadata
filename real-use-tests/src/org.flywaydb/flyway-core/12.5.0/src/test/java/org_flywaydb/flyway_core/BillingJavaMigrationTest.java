/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_flywaydb.flyway_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.CoreMigrationType;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.migration.JavaMigration;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.output.RepairResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Java-based migrations: `BaseJavaMigration` subclasses registered through `javaMigrations`,
 * with version and description derived from the class name, running JDBC work on the
 * migration `Context` connection, reading configuration placeholders, and rolling back and
 * repairing a failing migration.
 *
 * A billing service evolves its schema in code because one step computes values that plain
 * SQL cannot: it creates the invoice table, backfills a gross total from net amounts and the
 * configured tax rate, and keeps a currency lookup table in sync with a repeatable migration.
 * A later release ships a broken migration; Flyway rolls its changes back, records the failure,
 * and the operator repairs the history before the fixed release migrates.
 */
public class BillingJavaMigrationTest {

    private String jdbcUrl;

    @BeforeEach
    void createDatabaseName() {
        jdbcUrl = "jdbc:h2:mem:billing_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
    }

    @Test
    void appliesJavaMigrationsAndRollsBackFailingOne() throws Exception {
        Flyway flyway = configure(
                new V1__Create_invoices(),
                new V1_1__Backfill_gross_totals(),
                new R__Currencies(List.of("EUR", "USD")));

        MigrationInfo[] pending = flyway.info().pending();
        assertEquals(3, pending.length);
        assertEquals("Create invoices", pending[0].getDescription());
        assertEquals("1.1", pending[1].getVersion().getVersion());
        assertEquals(CoreMigrationType.JDBC, pending[1].getType());
        assertNull(pending[2].getVersion());

        MigrateResult firstRun = flyway.migrate();
        assertTrue(firstRun.success);
        assertEquals(3, firstRun.migrationsExecuted);
        assertEquals("1.1", firstRun.targetSchemaVersion);

        assertEquals(List.of("A-1:100:120", "A-2:250:300"),
                queryStrings("SELECT number || ':' || net || ':' || gross FROM invoice ORDER BY number"));
        assertEquals(List.of("EUR", "USD"), queryStrings("SELECT code FROM currency ORDER BY code"));

        MigrationInfo[] applied = flyway.info().applied();
        assertEquals(3, applied.length);
        assertEquals(BillingJavaMigrationTest.V1__Create_invoices.class.getName(),
                applied[0].getScript());
        assertEquals(MigrationState.SUCCESS, applied[1].getState());
        assertEquals(Integer.valueOf(2), applied[2].getChecksum());
        assertTrue(flyway.validateWithResult().validationSuccessful);

        Flyway brokenRelease = configure(
                new V1__Create_invoices(),
                new V1_1__Backfill_gross_totals(),
                new R__Currencies(List.of("EUR", "USD")),
                new V2__Mark_invoices_paid());
        FlywayException failure = assertThrows(FlywayException.class, brokenRelease::migrate);
        assertTrue(failure.getMessage().contains("MISSING_COLUMN"), failure.getMessage());
        MigrationInfo failed = brokenRelease.info().current();
        assertEquals("2", failed.getVersion().getVersion());
        assertEquals(MigrationState.FAILED, failed.getState());
        assertEquals(List.of("UNPAID", "UNPAID"),
                queryStrings("SELECT status FROM invoice ORDER BY number"));

        Flyway fixedRelease = configure(
                new V1__Create_invoices(),
                new V1_1__Backfill_gross_totals(),
                new R__Currencies(List.of("CHF", "EUR", "USD")),
                new V2__Add_due_days());
        RepairResult repair = fixedRelease.repair();
        assertEquals(1, repair.migrationsRemoved.size());
        assertEquals("1.1", fixedRelease.info().current().getVersion().getVersion());

        MigrateResult upgrade = fixedRelease.migrate();
        assertTrue(upgrade.success);
        assertEquals("1.1", upgrade.initialSchemaVersion);
        assertEquals("2", upgrade.targetSchemaVersion);
        assertEquals(2, upgrade.migrationsExecuted);
        assertEquals(List.of("30", "30"), queryStrings("SELECT due_days FROM invoice ORDER BY number"));
        assertEquals(List.of("CHF", "EUR", "USD"),
                queryStrings("SELECT code FROM currency ORDER BY code"));
        assertEquals(1, countState(fixedRelease.info().all(), MigrationState.SUPERSEDED));
        assertEquals(0, fixedRelease.migrate().migrationsExecuted);
    }

    private Flyway configure(JavaMigration... migrations) {
        return Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .placeholders(Map.of("taxPercent", "20", "defaultDueDays", "30"))
                .javaMigrations(migrations)
                .load();
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

    /** Creates the invoice table and the first two invoices. */
    public static class V1__Create_invoices extends BaseJavaMigration {
        @Override
        public void migrate(Context context) throws Exception {
            try (Statement statement = context.getConnection().createStatement()) {
                statement.execute("""
                        CREATE TABLE invoice (
                            number VARCHAR(20) PRIMARY KEY,
                            net INT NOT NULL,
                            gross INT,
                            status VARCHAR(10) DEFAULT 'UNPAID' NOT NULL
                        )
                        """);
                statement.execute("INSERT INTO invoice (number, net) VALUES ('A-1', 100), ('A-2', 250)");
            }
        }
    }

    /** Computes gross totals in Java from the configured tax rate. */
    public static class V1_1__Backfill_gross_totals extends BaseJavaMigration {
        @Override
        public void migrate(Context context) throws Exception {
            int taxPercent = Integer.parseInt(
                    context.getConfiguration().getPlaceholders().get("taxPercent"));
            Connection connection = context.getConnection();
            Map<String, Integer> netByNumber = new LinkedHashMap<>();
            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("SELECT number, net FROM invoice")) {
                while (rows.next()) {
                    netByNumber.put(rows.getString(1), rows.getInt(2));
                }
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE invoice SET gross = ? WHERE number = ?")) {
                for (Map.Entry<String, Integer> entry : netByNumber.entrySet()) {
                    update.setInt(1, entry.getValue() * (100 + taxPercent) / 100);
                    update.setString(2, entry.getKey());
                    update.addBatch();
                }
                update.executeBatch();
            }
        }
    }

    /** Keeps the currency table in sync; the checksum changes whenever the list does. */
    public static class R__Currencies extends BaseJavaMigration {
        private final List<String> codes;

        R__Currencies(List<String> codes) {
            this.codes = codes;
        }

        @Override
        public Integer getChecksum() {
            return codes.size();
        }

        @Override
        public void migrate(Context context) throws Exception {
            Connection connection = context.getConnection();
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS currency (code CHAR(3) PRIMARY KEY)");
                statement.execute("DELETE FROM currency");
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO currency (code) VALUES (?)")) {
                for (String code : codes) {
                    insert.setString(1, code);
                    insert.executeUpdate();
                }
            }
        }
    }

    /** A broken release: updates rows, then fails, so its transaction must roll back. */
    public static class V2__Mark_invoices_paid extends BaseJavaMigration {
        @Override
        public void migrate(Context context) throws Exception {
            try (Statement statement = context.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE invoice SET status = 'PAID'");
                statement.executeUpdate("UPDATE invoice SET status = 'PAID' WHERE missing_column = 1");
            }
        }
    }

    /** The fixed release: adds a due-days column defaulted from configuration. */
    public static class V2__Add_due_days extends BaseJavaMigration {
        @Override
        public void migrate(Context context) throws Exception {
            String dueDays = context.getConfiguration().getPlaceholders().get("defaultDueDays");
            try (Statement statement = context.getConnection().createStatement()) {
                statement.execute("ALTER TABLE invoice ADD COLUMN due_days INT DEFAULT "
                        + Integer.parseInt(dueDays) + " NOT NULL");
            }
        }
    }
}
