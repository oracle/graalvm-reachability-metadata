/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_flywaydb.flyway_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.CoreMigrationType;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.output.BaselineResult;
import org.flywaydb.core.api.output.CleanResult;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Baselining and cleaning: adopting Flyway on a non-empty schema with `baseline` and
 * `baselineOnMigrate`, skipping the migrations at or below the baseline version, and wiping
 * the schema with `clean` once `cleanDisabled` is lifted.
 *
 * A ledger service has run for years on hand-maintained tables. The team now ships the
 * schema as Flyway scripts whose first three versions recreate what production already has.
 * Production is baselined at version 3 so only the new script runs, a second legacy replica
 * is adopted by `baselineOnMigrate` during its regular startup migration, and a throwaway
 * test database is cleaned and rebuilt from scratch through a pooled `DataSource`.
 */
public class LegacyLedgerBaselineCleanTest {

    @TempDir
    Path migrations;

    @BeforeEach
    void writeMigrations() throws IOException {
        writeScript("V1__create_accounts.sql", """
                CREATE TABLE account (
                    id INT PRIMARY KEY,
                    owner VARCHAR(100) NOT NULL
                );
                """);
        writeScript("V2__create_entries.sql", """
                CREATE TABLE ledger_entry (
                    id INT PRIMARY KEY,
                    account_id INT NOT NULL REFERENCES account (id),
                    amount_cents BIGINT NOT NULL
                );
                """);
        writeScript("V3__seed_house_account.sql", """
                INSERT INTO account (id, owner) VALUES (1, 'house');
                """);
        writeScript("V4__create_balance_view.sql", """
                CREATE VIEW account_balance AS
                    SELECT a.owner, COALESCE(SUM(e.amount_cents), 0) AS balance
                    FROM account a LEFT JOIN ledger_entry e ON e.account_id = a.id
                    GROUP BY a.owner;
                """);
    }

    @Test
    void baselinesLegacyDatabasesAndCleansTestDatabase() throws Exception {
        try (HikariDataSource production = pool("ledger_prod")) {
            createLegacySchema(production);

            FlywayException refused = assertThrows(FlywayException.class,
                    () -> configure(production).migrate());
            assertTrue(refused.getMessage().contains("baseline"), refused.getMessage());
            assertEquals(List.of("alice:2500", "house:0"), balances(production, false));

            Flyway flyway = Flyway.configure()
                    .dataSource(production)
                    .locations("filesystem:" + migrations.toAbsolutePath())
                    .baselineVersion("3")
                    .baselineDescription("Legacy ledger schema")
                    .load();
            BaselineResult baseline = flyway.baseline();
            assertTrue(baseline.successfullyBaselined);
            assertEquals("3", baseline.baselineVersion);

            MigrationInfoService beforeUpgrade = flyway.info();
            assertEquals("3", beforeUpgrade.current().getVersion().getVersion());
            assertEquals(CoreMigrationType.BASELINE, beforeUpgrade.current().getType());
            assertEquals("Legacy ledger schema", beforeUpgrade.current().getDescription());
            assertEquals(List.of("1:BELOW_BASELINE", "2:BELOW_BASELINE", "3:BASELINE_IGNORED",
                    "3:BASELINE", "4:PENDING"), states(beforeUpgrade.all()));

            MigrateResult upgrade = flyway.migrate();
            assertTrue(upgrade.success);
            assertEquals("3", upgrade.initialSchemaVersion);
            assertEquals("4", upgrade.targetSchemaVersion);
            assertEquals(1, upgrade.migrationsExecuted);
            assertEquals(List.of("alice:2500", "house:0"), balances(production, true));
            assertTrue(flyway.validateWithResult().validationSuccessful);

            BaselineResult repeated = flyway.baseline();
            assertTrue(repeated.successfullyBaselined);
            assertEquals(List.of("TABLE:<< Flyway Schema History table created >>",
                    "BASELINE:Legacy ledger schema", "SQL:create balance view"), history(production));
        }

        try (HikariDataSource replica = pool("ledger_replica")) {
            createLegacySchema(replica);

            MigrateResult adoption = Flyway.configure()
                    .dataSource(replica)
                    .locations("filesystem:" + migrations.toAbsolutePath())
                    .baselineOnMigrate(true)
                    .baselineVersion("3")
                    .load()
                    .migrate();
            assertTrue(adoption.success);
            assertEquals(1, adoption.migrationsExecuted);
            assertEquals("4", adoption.targetSchemaVersion);
            assertEquals(List.of("TABLE:<< Flyway Schema History table created >>",
                    "BASELINE:<< Flyway Baseline >>", "SQL:create balance view"), history(replica));
        }

        try (HikariDataSource scratch = pool("ledger_scratch")) {
            Flyway guarded = configure(scratch);
            assertEquals(4, guarded.migrate().migrationsExecuted);
            insertEntry(scratch, 10, 1, 999);
            assertEquals(List.of("house:999"), balances(scratch, true));

            assertThrows(FlywayException.class, guarded::clean);
            assertEquals(List.of("house:999"), balances(scratch, true));

            Flyway disposable = Flyway.configure()
                    .dataSource(scratch)
                    .locations("filesystem:" + migrations.toAbsolutePath())
                    .cleanDisabled(false)
                    .load();
            CleanResult clean = disposable.clean();
            assertEquals(List.of("PUBLIC"), clean.schemasCleaned);
            assertEquals(0, disposable.info().applied().length);
            assertEquals(4, disposable.info().pending().length);
            assertEquals(0, countTables(scratch));

            MigrateResult rebuild = disposable.migrate();
            assertEquals(4, rebuild.migrationsExecuted);
            assertEquals(List.of("house:0"), balances(scratch, true));
        }
    }

    private Flyway configure(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrations.toAbsolutePath())
                .load();
    }

    private static HikariDataSource pool(String name) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + name + "_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setPoolName(name);
        return new HikariDataSource(config);
    }

    private static void createLegacySchema(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE account (id INT PRIMARY KEY, owner VARCHAR(100) NOT NULL)");
            statement.execute("""
                    CREATE TABLE ledger_entry (
                        id INT PRIMARY KEY,
                        account_id INT NOT NULL REFERENCES account (id),
                        amount_cents BIGINT NOT NULL
                    )
                    """);
            statement.execute("INSERT INTO account (id, owner) VALUES (1, 'house'), (2, 'alice')");
            statement.execute("INSERT INTO ledger_entry (id, account_id, amount_cents) VALUES (1, 2, 2500)");
        }
    }

    private static void insertEntry(DataSource dataSource, int id, int accountId, long cents)
            throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO ledger_entry (id, account_id, amount_cents) VALUES ("
                    + id + ", " + accountId + ", " + cents + ")");
        }
    }

    private static List<String> balances(DataSource dataSource, boolean fromView) throws SQLException {
        String sql = fromView
                ? "SELECT owner || ':' || balance FROM account_balance ORDER BY owner"
                : """
                        SELECT a.owner || ':' || COALESCE(SUM(e.amount_cents), 0)
                        FROM account a LEFT JOIN ledger_entry e ON e.account_id = a.id
                        GROUP BY a.owner ORDER BY a.owner
                        """;
        return queryStrings(dataSource, sql);
    }

    private static List<String> history(DataSource dataSource) throws SQLException {
        return queryStrings(dataSource, """
                SELECT "type" || ':' || "description" FROM "flyway_schema_history"
                ORDER BY "installed_rank"
                """);
    }

    private static int countTables(DataSource dataSource) throws SQLException {
        return Integer.parseInt(queryStrings(dataSource, """
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'
                """).get(0));
    }

    private static List<String> states(MigrationInfo[] infos) {
        List<String> states = new ArrayList<>();
        for (MigrationInfo info : infos) {
            states.add(info.getVersion() + ":" + info.getState().name());
        }
        return states;
    }

    private static List<String> queryStrings(DataSource dataSource, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
        }
        return values;
    }

    private void writeScript(String fileName, String sql) throws IOException {
        Files.writeString(migrations.resolve(fileName), sql, StandardCharsets.UTF_8);
    }
}
