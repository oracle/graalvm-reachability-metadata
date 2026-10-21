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
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.logging.Log;
import org.flywaydb.core.api.logging.LogCreator;
import org.flywaydb.core.api.logging.LogFactory;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Logging integration: routing Flyway's own log output into an application-provided
 * `LogCreator` named in the `loggers` setting next to the built-in `slf4j` logger, with the
 * info and error messages of migrate, the configuration-scoped logger selection and
 * the rejection of a logger class that cannot be loaded.
 *
 * A fulfillment service keeps a deployment audit trail of every schema change. Besides its
 * regular SLF4J console output it names its own audit `LogCreator` in the Flyway `loggers`
 * setting, so each startup migration records which database it touched, which versions it
 * applied, that a retired script folder is skipped, and which version failed when a
 * broken release script is deployed. A maintenance tool that configures Flyway with SLF4J only
 * inspects the failed version without writing to the audit trail, and a typo in the logger
 * class name stops startup.
 */
public class FulfillmentAuditLoggingMigrationTest {

    @TempDir
    Path deployment;

    private Path migrations;

    private Path retiredMigrations;

    private String jdbcUrl;

    @BeforeEach
    void prepareDeployment() throws IOException {
        migrations = Files.createDirectory(deployment.resolve("sql"));
        retiredMigrations = deployment.resolve("retired-sql");
        jdbcUrl = "jdbc:h2:mem:fulfillment_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        AuditTrailLogCreator.drain();
    }

    /**
     * Flyway's logger selection is process-wide and a new `Flyway` logs before it installs its own
     * configuration, so the rejected logger name is replaced before the next startup.
     */
    @AfterEach
    void restoreLoggerSelection() {
        LogFactory.setConfiguration(Flyway.configure().loggers("auto"));
    }

    @Test
    void recordsSchemaChangesInTheDeploymentAuditTrail() throws Exception {
        writeScript("V1__create_shipments.sql", """
                CREATE TABLE shipment (
                    id INT PRIMARY KEY,
                    carrier VARCHAR(40) NOT NULL
                );
                """);
        writeScript("V2__seed_carriers.sql", """
                INSERT INTO shipment (id, carrier) VALUES (1, 'DHL');
                INSERT INTO shipment (id, carrier) VALUES (2, 'UPS');
                """);

        MigrateResult firstStartup = auditedFlyway().migrate();
        assertEquals(2, firstStartup.migrationsExecuted);
        List<AuditEntry> firstTrail = AuditTrailLogCreator.drain();

        assertTrue(contains(firstTrail, "INFO", "Database: " + jdbcUrl.substring(0, jdbcUrl.indexOf(';'))),
                () -> "database line missing: " + firstTrail);
        assertTrue(contains(firstTrail, "INFO",
                "Creating Schema History table \"PUBLIC\".\"flyway_schema_history\""));
        assertEquals(List.of(
                "Migrating schema \"PUBLIC\" to version \"1 - create shipments\"",
                "Migrating schema \"PUBLIC\" to version \"2 - seed carriers\""),
                messagesStartingWith(firstTrail, "INFO", "Migrating schema"));
        assertTrue(contains(firstTrail, "INFO",
                "Successfully applied 2 migrations to schema \"PUBLIC\", now at version v2"));
        String retiredSkipped = "Skipping filesystem location: "
                + retiredMigrations.toAbsolutePath() + " (not found)";
        assertEquals(List.of(retiredSkipped), messagesAt(firstTrail, "ERROR"));
        for (AuditEntry entry : firstTrail) {
            assertTrue(entry.source().startsWith("org.flywaydb."), () -> "unexpected source " + entry);
        }
        assertEquals(List.of("DHL", "UPS"), carriers());

        MigrateResult secondStartup = auditedFlyway().migrate();
        assertEquals(0, secondStartup.migrationsExecuted);
        List<AuditEntry> secondTrail = AuditTrailLogCreator.drain();
        assertTrue(contains(secondTrail, "INFO", "Current version of schema \"PUBLIC\": 2"));
        assertTrue(contains(secondTrail, "INFO", "Schema \"PUBLIC\" is up to date. No migration necessary."));
        assertEquals(List.of(), messagesStartingWith(secondTrail, "INFO", "Migrating schema"));

        writeScript("V3__import_legacy_carriers.sql", """
                INSERT INTO shipment (id, carrier) VALUES (3, 'FedEx');
                INSERT INTO shipment (id, carrier) VALUES (1, 'Royal Mail');
                """);
        FlywayException failure = assertThrows(FlywayException.class, () -> auditedFlyway().migrate());
        assertTrue(failure.getMessage().contains("V3__import_legacy_carriers.sql"), failure::getMessage);
        List<AuditEntry> failedTrail = AuditTrailLogCreator.drain();
        assertTrue(contains(failedTrail, "INFO",
                "Migrating schema \"PUBLIC\" to version \"3 - import legacy carriers\""));
        List<String> errors = messagesAt(failedTrail, "ERROR");
        assertEquals(2, errors.size(), () -> "errors: " + errors);
        assertEquals(retiredSkipped, errors.get(0));
        assertTrue(errors.get(1).startsWith(
                "Migration of schema \"PUBLIC\" to version \"3 - import legacy carriers\" failed!"), errors::toString);
        assertEquals(List.of("DHL", "UPS"), carriers());

        MigrationInfoService maintenanceView = Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .locations("filesystem:" + migrations.toAbsolutePath())
                .loggers("slf4j")
                .load()
                .info();
        assertEquals("3", maintenanceView.current().getVersion().getVersion());
        assertEquals(MigrationState.FAILED, maintenanceView.current().getState());
        assertEquals(List.of(), AuditTrailLogCreator.drain());

        String misspelled = AuditTrailLogCreator.class.getName() + "s";
        FlywayException typo = assertThrows(FlywayException.class, () -> Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .locations("filesystem:" + migrations.toAbsolutePath())
                .loggers("slf4j", misspelled)
                .load()
                .info());
        assertTrue(typo.getMessage().contains(misspelled), typo::getMessage);
    }

    private Flyway auditedFlyway() {
        return Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .locations("filesystem:" + migrations.toAbsolutePath(),
                        "filesystem:" + retiredMigrations.toAbsolutePath())
                .loggers("slf4j", AuditTrailLogCreator.class.getName())
                .load();
    }

    private void writeScript(String fileName, String sql) throws IOException {
        Files.writeString(migrations.resolve(fileName), sql, StandardCharsets.UTF_8);
    }

    private List<String> carriers() throws SQLException {
        List<String> carriers = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT carrier FROM shipment ORDER BY id")) {
            while (resultSet.next()) {
                carriers.add(resultSet.getString(1));
            }
        }
        return carriers;
    }

    private static boolean contains(List<AuditEntry> trail, String level, String prefix) {
        return !messagesStartingWith(trail, level, prefix).isEmpty();
    }

    private static List<String> messagesStartingWith(List<AuditEntry> trail, String level, String prefix) {
        List<String> messages = new ArrayList<>();
        for (String message : messagesAt(trail, level)) {
            if (message.startsWith(prefix)) {
                messages.add(message);
            }
        }
        return messages;
    }

    private static List<String> messagesAt(List<AuditEntry> trail, String level) {
        List<String> messages = new ArrayList<>();
        for (AuditEntry entry : trail) {
            if (entry.level().equals(level)) {
                messages.add(entry.message());
            }
        }
        return messages;
    }

    /** One line of the deployment audit trail. */
    record AuditEntry(String level, String source, String message) {
    }

    /**
     * The audit sink Flyway instantiates by class name from the `loggers` setting; it keeps
     * info, warning and error messages and leaves debug output to the console logger.
     */
    public static class AuditTrailLogCreator implements LogCreator {

        private static final List<AuditEntry> TRAIL = new ArrayList<>();

        static synchronized List<AuditEntry> drain() {
            List<AuditEntry> entries = new ArrayList<>(TRAIL);
            TRAIL.clear();
            return entries;
        }

        private static synchronized void append(String level, Class<?> source, String message) {
            TRAIL.add(new AuditEntry(level, source.getName(), message));
        }

        @Override
        public Log createLogger(Class<?> source) {
            return new Log() {
                @Override
                public boolean isDebugEnabled() {
                    return false;
                }

                @Override
                public void debug(String message) {
                }

                @Override
                public void info(String message) {
                    append("INFO", source, message);
                }

                @Override
                public void warn(String message) {
                    append("WARN", source, message);
                }

                @Override
                public void error(String message) {
                    append("ERROR", source, message);
                }

                @Override
                public void error(String message, Exception exception) {
                    append("ERROR", source, message);
                }

                @Override
                public void notice(String message) {
                    append("NOTICE", source, message);
                }
            };
        }
    }
}
