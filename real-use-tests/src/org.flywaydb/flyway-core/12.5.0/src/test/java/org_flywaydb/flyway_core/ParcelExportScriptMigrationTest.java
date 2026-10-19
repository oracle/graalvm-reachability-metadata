/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_flywaydb.flyway_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.CoreErrorCode;
import org.flywaydb.core.api.CoreMigrationType;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.output.RepairResult;
import org.flywaydb.core.api.output.ValidateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Script migrations: versioned `.sh` shell scripts discovered next to SQL migrations in a
 * filesystem location and run by Flyway as child processes in version order, receiving the
 * connection settings as `FLYWAY_URL` and `FLYWAY_USER` and the configured and built-in
 * placeholders as `FP__name__` environment variables, recorded as `SCRIPT` migrations with a
 * checksum, a non-zero exit status failing the migration, and a checksum mismatch after the
 * applied script is edited.
 *
 * A parcel service keeps its database schema in SQL scripts, but one release step also has to
 * prepare the outbound export area a carrier integration reads: it creates the directory tree
 * and writes a manifest naming the database it belongs to. The team ships that step as a shell
 * script migration so it runs exactly once per environment, in order with the schema changes.
 */
public class ParcelExportScriptMigrationTest {

    private static final String USER = "parcel_admin";

    private static final String PASSWORD = "s3cret";

    @TempDir
    Path workspace;

    private Path migrations;

    private Path exportRoot;

    private String jdbcUrl;

    @BeforeEach
    void prepareDirectories() throws IOException {
        migrations = Files.createDirectories(workspace.resolve("migrations"));
        exportRoot = Files.createDirectories(workspace.resolve("exports"));
        jdbcUrl = "jdbc:h2:mem:parcel_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
    }

    @Test
    void runsShellScriptMigrationInVersionOrder() throws Exception {
        writeFile("V1__create_parcels.sql", """
                CREATE TABLE parcel (
                    id INT PRIMARY KEY,
                    carrier VARCHAR(40) NOT NULL
                );
                INSERT INTO parcel (id, carrier) VALUES (1, 'DHL');
                """);
        writeFile("V2__prepare_export_area.sh", """
                #!/bin/sh
                set -e
                mkdir -p "$FP__exportRoot__/outbound/$FP__carrier__"
                {
                    echo "url=$FLYWAY_URL"
                    echo "user=$FLYWAY_USER"
                    echo "carrier=$FP__carrier__"
                    echo "script=$FP__flyway_filename__"
                } > "$FP__exportRoot__/outbound/manifest.properties"
                echo "export area prepared"
                """);
        writeFile("V3__add_export_flag.sql", """
                ALTER TABLE parcel ADD COLUMN exported BOOLEAN DEFAULT FALSE NOT NULL;
                """);

        Flyway flyway = configure();

        MigrationInfo[] pending = flyway.info().pending();
        assertEquals(3, pending.length);
        MigrationInfo pendingScript = pending[1];
        assertEquals("2", pendingScript.getVersion().getVersion());
        assertEquals(CoreMigrationType.SCRIPT, pendingScript.getType());
        assertEquals("prepare export area", pendingScript.getDescription());
        assertEquals("V2__prepare_export_area.sh", pendingScript.getScript());
        assertEquals(MigrationState.PENDING, pendingScript.getState());

        MigrateResult result = flyway.migrate();
        assertTrue(result.success);
        assertEquals(3, result.migrationsExecuted);
        assertEquals("3", result.targetSchemaVersion);
        assertEquals(List.of("SQL", "SCRIPT", "SQL"), migratedTypes(result));

        Path manifest = exportRoot.resolve("outbound/manifest.properties");
        assertTrue(Files.isDirectory(exportRoot.resolve("outbound/dhl")));
        assertEquals(List.of(
                "url=" + jdbcUrl,
                "user=" + USER,
                "carrier=dhl",
                "script=V2__prepare_export_area.sh"), Files.readAllLines(manifest));

        MigrationInfo appliedScript = flyway.info().applied()[1];
        assertEquals(CoreMigrationType.SCRIPT, appliedScript.getType());
        assertEquals(MigrationState.SUCCESS, appliedScript.getState());
        // H2 reports the connected user in upper case.
        assertEquals("PARCEL_ADMIN", appliedScript.getInstalledBy());
        assertNotNull(appliedScript.getChecksum());
        assertEquals(List.of("V1__create_parcels.sql:SQL", "V2__prepare_export_area.sh:SCRIPT",
                "V3__add_export_flag.sql:SQL"), history());
        assertEquals(List.of("1"),
                queryStrings("SELECT COUNT(*) FROM parcel WHERE NOT exported"));

        Files.delete(manifest);
        MigrateResult idleRun = configure().migrate();
        assertEquals(0, idleRun.migrationsExecuted);
        assertFalse(Files.exists(manifest));

        writeFile("V2__prepare_export_area.sh", """
                #!/bin/sh
                mkdir -p "$FP__exportRoot__/outbound/archive"
                """);
        ValidateResult validation = configure().validateWithResult();
        assertFalse(validation.validationSuccessful);
        assertEquals(1, validation.invalidMigrations.size());
        assertEquals("2", validation.invalidMigrations.get(0).version);
        assertEquals(CoreErrorCode.CHECKSUM_MISMATCH,
                validation.invalidMigrations.get(0).errorDetails.errorCode);
    }

    @Test
    void failingShellScriptStopsTheRelease() throws Exception {
        writeFile("V1__create_parcels.sql", """
                CREATE TABLE parcel (id INT PRIMARY KEY);
                """);
        writeFile("V2__register_carrier_feed.sh", """
                #!/bin/sh
                echo "carrier feed endpoint unreachable"
                exit 3
                """);
        writeFile("V3__add_label_column.sql", """
                ALTER TABLE parcel ADD COLUMN label VARCHAR(20);
                """);

        FlywayException failure = assertThrows(FlywayException.class, () -> configure().migrate());
        assertTrue(failure.getMessage().contains("Migration failed"), failure.getMessage());

        MigrationInfo[] all = configure().info().all();
        assertEquals(MigrationState.SUCCESS, all[0].getState());
        assertEquals(MigrationState.FAILED, all[1].getState());
        assertEquals(CoreMigrationType.SCRIPT, all[1].getType());
        assertEquals(MigrationState.PENDING, all[2].getState());
        assertEquals(List.of("0"),
                queryStrings("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS"
                        + " WHERE TABLE_NAME = 'PARCEL' AND COLUMN_NAME = 'LABEL'"));

        writeFile("V2__register_carrier_feed.sh", """
                #!/bin/sh
                set -e
                echo "feed=$FP__carrier__" > "$FP__exportRoot__/carrier-feed.properties"
                """);
        assertThrows(FlywayException.class, () -> configure().migrate());

        RepairResult repair = configure().repair();
        assertEquals(1, repair.migrationsRemoved.size());
        assertEquals("2", repair.migrationsRemoved.get(0).version);
        MigrateResult retry = configure().migrate();
        assertTrue(retry.success);
        assertEquals(2, retry.migrationsExecuted);
        assertEquals(List.of("feed=dhl"),
                Files.readAllLines(exportRoot.resolve("carrier-feed.properties")));
        assertEquals("3", configure().info().current().getVersion().getVersion());
    }

    private Flyway configure() {
        return Flyway.configure()
                .dataSource(jdbcUrl, USER, PASSWORD)
                .locations("filesystem:" + migrations.toAbsolutePath())
                .placeholders(Map.of("exportRoot", exportRoot.toAbsolutePath().toString(),
                        "carrier", "dhl"))
                .load();
    }

    private void writeFile(String fileName, String content) throws IOException {
        Files.writeString(migrations.resolve(fileName), content, StandardCharsets.UTF_8);
    }

    private static List<String> migratedTypes(MigrateResult result) {
        List<String> types = new ArrayList<>();
        result.migrations.forEach(migration -> types.add(migration.type));
        return types;
    }

    private List<String> history() throws SQLException {
        return queryStrings("""
                SELECT "script" || ':' || "type" FROM "flyway_schema_history"
                WHERE "version" IS NOT NULL
                ORDER BY "installed_rank"
                """);
    }

    private List<String> queryStrings(String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, USER, PASSWORD);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                values.add(String.valueOf(resultSet.getString(1)));
            }
        }
        return values;
    }
}
