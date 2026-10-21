/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_flywaydb.flyway_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Reader;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.ClassProvider;
import org.flywaydb.core.api.CoreErrorCode;
import org.flywaydb.core.api.CoreMigrationType;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.migration.JavaMigration;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.output.ValidateOutput;
import org.flywaydb.core.api.output.ValidateResult;
import org.flywaydb.core.api.resource.LoadableResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pluggable migration sources: SQL migrations and SQL callbacks served by an application
 * `ResourceProvider` and Java migrations served by a `ClassProvider`, replacing location
 * scanning, with placeholders, checksums, repeatable re-application and validation computed
 * from the provided content.
 *
 * A reporting module ships its schema as an embedded bundle compiled into the application
 * rather than as files on disk or on the classpath. The application hands the bundle to
 * Flyway, which resolves versioned and repeatable scripts, the `afterMigrate` callback and a
 * Java backfill from it. The next release ships an updated bundle that adds a migration and
 * changes the repeatable view, and a tampered bundle whose applied script was edited is
 * rejected before it can migrate.
 */
public class ReportingEmbeddedBundleMigrationTest {

    private static final String BUNDLE_PATH = "reporting/";

    private static final String CREATE_TABLES = """
            CREATE TABLE report (
                id INT PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                owner VARCHAR(50) DEFAULT '${report_owner}' NOT NULL
            );
            CREATE TABLE report_run (
                report_id INT NOT NULL REFERENCES report (id),
                rows_produced INT NOT NULL
            );
            CREATE TABLE migration_audit (
                id INT AUTO_INCREMENT PRIMARY KEY,
                event VARCHAR(50) NOT NULL
            );
            """;

    private static final String SEED_REPORTS = """
            INSERT INTO report (id, name) VALUES (1, 'daily-sales');
            INSERT INTO report (id, name, owner) VALUES (2, 'weekly-churn', 'growth');
            """;

    private static final String SUMMARY_VIEW = """
            CREATE OR REPLACE VIEW report_summary AS
                SELECT r.name, r.owner, COUNT(rr.report_id) AS runs
                FROM report r LEFT JOIN report_run rr ON rr.report_id = r.id
                GROUP BY r.name, r.owner;
            """;

    private static final String SUMMARY_VIEW_WITH_ROWS = """
            CREATE OR REPLACE VIEW report_summary AS
                SELECT r.name, r.owner, COUNT(rr.report_id) AS runs,
                       COALESCE(SUM(rr.rows_produced), 0) AS total_rows
                FROM report r LEFT JOIN report_run rr ON rr.report_id = r.id
                GROUP BY r.name, r.owner;
            """;

    private static final String AUDIT_CALLBACK = """
            INSERT INTO migration_audit (event) VALUES ('afterMigrate');
            """;

    private static final String ADD_SCHEDULE = """
            ALTER TABLE report ADD COLUMN schedule VARCHAR(20) DEFAULT '${default_schedule}' NOT NULL;
            UPDATE report SET schedule = 'weekly' WHERE name = 'weekly-churn';
            """;

    private String jdbcUrl;

    @BeforeEach
    void createDatabaseName() {
        jdbcUrl = "jdbc:h2:mem:reporting_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
    }

    @Test
    void migratesFromEmbeddedBundleAndRejectsTamperedBundle() throws Exception {
        EmbeddedBundle firstRelease = new EmbeddedBundle()
                .script("V1__Create_report_tables.sql", CREATE_TABLES)
                .script("V2__Seed_reports.sql", SEED_REPORTS)
                .script("R__Report_summary_view.sql", SUMMARY_VIEW)
                .script("afterMigrate.sql", AUDIT_CALLBACK)
                .script("README.txt", "Not a migration.");
        Flyway flyway = configure(firstRelease, V3__Record_initial_runs.class);

        MigrationInfo[] pending = flyway.info().pending();
        assertEquals(4, pending.length);
        assertEquals("Create report tables", pending[0].getDescription());
        assertEquals(BUNDLE_PATH + "V1__Create_report_tables.sql", pending[0].getScript());
        assertEquals("bundle:" + BUNDLE_PATH + "V2__Seed_reports.sql",
                pending[1].getPhysicalLocation());
        assertEquals("3", pending[2].getVersion().getVersion());
        assertEquals(CoreMigrationType.JDBC, pending[2].getType());
        assertEquals("Record initial runs", pending[2].getDescription());
        assertNull(pending[3].getVersion());
        assertEquals(CoreMigrationType.SQL, pending[3].getType());
        assertEquals("Report summary view", pending[3].getDescription());
        assertTrue(firstRelease.requestedPrefixes.contains("V"), firstRelease.requestedPrefixes.toString());
        assertTrue(firstRelease.requestedPrefixes.contains("R"), firstRelease.requestedPrefixes.toString());

        MigrateResult firstRun = flyway.migrate();
        assertTrue(firstRun.success);
        assertEquals(4, firstRun.migrationsExecuted);
        assertEquals("3", firstRun.targetSchemaVersion);
        assertEquals(List.of("daily-sales:analytics:1", "weekly-churn:growth:1"),
                queryStrings("SELECT name || ':' || owner || ':' || runs FROM report_summary ORDER BY name"));
        assertEquals(List.of("afterMigrate"), queryStrings("SELECT event FROM migration_audit ORDER BY id"));

        Integer firstViewChecksum = appliedChecksum(flyway, "R__Report_summary_view.sql");
        assertNotNull(firstViewChecksum);
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals(List.of("afterMigrate", "afterMigrate"),
                queryStrings("SELECT event FROM migration_audit ORDER BY id"));

        EmbeddedBundle secondRelease = new EmbeddedBundle()
                .script("V1__Create_report_tables.sql", CREATE_TABLES)
                .script("V2__Seed_reports.sql", SEED_REPORTS)
                .script("V4__Add_report_schedule.sql", ADD_SCHEDULE)
                .script("R__Report_summary_view.sql", SUMMARY_VIEW_WITH_ROWS)
                .script("afterMigrate.sql", AUDIT_CALLBACK);
        Flyway upgraded = configure(secondRelease, V3__Record_initial_runs.class);

        MigrationInfo[] upgradePending = upgraded.info().pending();
        assertEquals(2, upgradePending.length);
        assertEquals("4", upgradePending[0].getVersion().getVersion());
        assertNull(upgradePending[1].getVersion());
        assertEquals(MigrationState.PENDING, upgradePending[1].getState());
        assertEquals(1, countState(upgraded.info().all(), MigrationState.OUTDATED));

        MigrateResult upgrade = upgraded.migrate();
        assertTrue(upgrade.success);
        assertEquals("3", upgrade.initialSchemaVersion);
        assertEquals("4", upgrade.targetSchemaVersion);
        assertEquals(2, upgrade.migrationsExecuted);
        assertEquals(List.of("daily-sales:nightly:1:120", "weekly-churn:weekly:1:840"),
                queryStrings("""
                        SELECT s.name || ':' || r.schedule || ':' || s.runs || ':' || s.total_rows
                        FROM report_summary s JOIN report r ON r.name = s.name
                        ORDER BY s.name
                        """));
        assertEquals(1, countState(upgraded.info().all(), MigrationState.SUPERSEDED));
        assertNotEquals(firstViewChecksum, appliedChecksum(upgraded, "R__Report_summary_view.sql"));

        EmbeddedBundle tamperedRelease = new EmbeddedBundle()
                .script("V1__Create_report_tables.sql", CREATE_TABLES)
                .script("V2__Seed_reports.sql", SEED_REPORTS
                        + "INSERT INTO report (id, name) VALUES (3, 'hourly-errors');\n")
                .script("V4__Add_report_schedule.sql", ADD_SCHEDULE)
                .script("R__Report_summary_view.sql", SUMMARY_VIEW_WITH_ROWS)
                .script("afterMigrate.sql", AUDIT_CALLBACK);
        Flyway tampered = configure(tamperedRelease, V3__Record_initial_runs.class);

        ValidateResult validation = tampered.validateWithResult();
        assertFalse(validation.validationSuccessful);
        assertEquals(1, validation.invalidMigrations.size());
        ValidateOutput invalid = validation.invalidMigrations.get(0);
        assertEquals("2", invalid.version);
        assertEquals(CoreErrorCode.CHECKSUM_MISMATCH, invalid.errorDetails.errorCode);
        assertThrows(FlywayValidateException.class, tampered::migrate);
        assertEquals(List.of("2"), queryStrings("SELECT COUNT(*) FROM report"));
    }

    private Flyway configure(EmbeddedBundle bundle, Class<? extends JavaMigration> javaMigration) {
        return Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .placeholders(Map.of("report_owner", "analytics", "default_schedule", "nightly"))
                .resourceProvider(bundle)
                .javaMigrationClassProvider(new JavaMigrationClasses(List.of(javaMigration)))
                .load();
    }

    private static Integer appliedChecksum(Flyway flyway, String script) {
        for (MigrationInfo info : flyway.info().applied()) {
            if ((BUNDLE_PATH + script).equals(info.getScript()) && info.getState() == MigrationState.SUCCESS) {
                return info.getChecksum();
            }
        }
        throw new AssertionError("No successful migration for " + script);
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

    /// The reporting module's scripts, compiled into the application and served to Flyway by name.
    static final class EmbeddedBundle implements ResourceProvider {

        private final Map<String, EmbeddedScript> scripts = new LinkedHashMap<>();
        private final List<String> requestedPrefixes = new ArrayList<>();

        EmbeddedBundle script(String filename, String content) {
            scripts.put(filename, new EmbeddedScript(filename, content));
            return this;
        }

        @Override
        public LoadableResource getResource(String name) {
            return scripts.get(name.startsWith(BUNDLE_PATH) ? name.substring(BUNDLE_PATH.length()) : name);
        }

        @Override
        public Collection<LoadableResource> getResources(String prefix, String[] suffixes) {
            requestedPrefixes.add(prefix);
            List<LoadableResource> matches = new ArrayList<>();
            for (EmbeddedScript script : scripts.values()) {
                if (script.getFilename().startsWith(prefix) && hasSuffix(script.getFilename(), suffixes)) {
                    matches.add(script);
                }
            }
            return matches;
        }

        private static boolean hasSuffix(String filename, String[] suffixes) {
            for (String suffix : suffixes) {
                if (filename.endsWith(suffix)) {
                    return true;
                }
            }
            return false;
        }
    }

    /// One script of the bundle, read from memory each time Flyway parses or checksums it.
    static final class EmbeddedScript extends LoadableResource {

        private final String filename;
        private final String content;

        EmbeddedScript(String filename, String content) {
            this.filename = filename;
            this.content = content;
        }

        @Override
        public Reader read() {
            return new StringReader(content);
        }

        @Override
        public String getAbsolutePath() {
            return BUNDLE_PATH + filename;
        }

        @Override
        public String getAbsolutePathOnDisk() {
            return "bundle:" + BUNDLE_PATH + filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }

        @Override
        public String getRelativePath() {
            return BUNDLE_PATH + filename;
        }
    }

    /// The Java migrations the application registers explicitly instead of scanning packages.
    static final class JavaMigrationClasses implements ClassProvider<JavaMigration> {

        private final List<Class<? extends JavaMigration>> classes;

        JavaMigrationClasses(List<Class<? extends JavaMigration>> classes) {
            this.classes = classes;
        }

        @Override
        public Collection<Class<? extends JavaMigration>> getClasses() {
            return classes;
        }
    }

    /// Records a first run for every seeded report, sized by the report's cadence.
    public static class V3__Record_initial_runs extends BaseJavaMigration {

        @Override
        public void migrate(Context context) throws Exception {
            List<Integer> ids = new ArrayList<>();
            List<String> names = new ArrayList<>();
            try (Statement statement = context.getConnection().createStatement();
                    ResultSet reports = statement.executeQuery("SELECT id, name FROM report ORDER BY id")) {
                while (reports.next()) {
                    ids.add(reports.getInt(1));
                    names.add(reports.getString(2));
                }
            }
            try (PreparedStatement insert = context.getConnection().prepareStatement(
                    "INSERT INTO report_run (report_id, rows_produced) VALUES (?, ?)")) {
                for (int i = 0; i < ids.size(); i++) {
                    insert.setInt(1, ids.get(i));
                    insert.setInt(2, names.get(i).startsWith("daily") ? 120 : 840);
                    insert.executeUpdate();
                }
            }
        }
    }
}
