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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.CoreErrorCode;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.output.ValidateOutput;
import org.flywaydb.core.api.output.ValidateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Migration ordering and validation controls: staged rollout up to a `target` version, the
 * `ABOVE_TARGET`, `IGNORED`, `OUT_OF_ORDER`, `FUTURE_SUCCESS`, and `MISSING_SUCCESS` states,
 * applying a late hotfix with `outOfOrder`, and relaxing validation with
 * `ignoreMigrationPatterns`.
 *
 * A catalog service rolls its schema out release by release. Production first stops at version
 * 2 while version 3 waits, then takes version 3. A hotfix numbered 2.1 lands afterwards: Flyway
 * refuses it until the deployment opts into out-of-order migrations. An older instance still
 * running only the first two scripts keeps working against the newer database, and once the
 * hotfix script is retired from the release the team accepts the missing entry explicitly.
 */
public class CatalogReleaseOrderingMigrationTest {

    @TempDir
    Path workspace;

    private Path currentRelease;

    private Path previousRelease;

    private String jdbcUrl;

    @BeforeEach
    void prepareReleases() throws IOException {
        jdbcUrl = "jdbc:h2:mem:catalog_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        currentRelease = Files.createDirectory(workspace.resolve("current"));
        previousRelease = Files.createDirectory(workspace.resolve("previous"));
        for (Path release : List.of(currentRelease, previousRelease)) {
            writeScript(release, "V1__create_catalog.sql", """
                    CREATE TABLE catalog_item (
                        id INT PRIMARY KEY,
                        sku VARCHAR(20) NOT NULL
                    );
                    INSERT INTO catalog_item (id, sku) VALUES (1, 'ANV-001');
                    INSERT INTO catalog_item (id, sku) VALUES (2, 'RKT-007');
                    """);
            writeScript(release, "V2__add_price.sql", """
                    ALTER TABLE catalog_item ADD COLUMN price DECIMAL(10, 2) DEFAULT 0 NOT NULL;
                    UPDATE catalog_item SET price = 19.99 WHERE id = 1;
                    """);
        }
        writeScript(currentRelease, "V3__create_supplier.sql", """
                CREATE TABLE supplier (
                    id INT PRIMARY KEY,
                    name VARCHAR(100) NOT NULL
                );
                INSERT INTO supplier (id, name) VALUES (1, 'Acme');
                """);
    }

    @Test
    void controlsTheOrderInWhichCatalogReleasesReachTheDatabase() throws Exception {
        MigrateResult staged = configure(currentRelease).target("2").load().migrate();
        assertTrue(staged.success);
        assertEquals(2, staged.migrationsExecuted);
        assertEquals("2", staged.targetSchemaVersion);
        assertEquals(MigrationState.ABOVE_TARGET, states(configure(currentRelease).target("2")).get("3"));
        assertFalse(tableExists("SUPPLIER"));
        assertEquals("19.99", queryString("SELECT price FROM catalog_item WHERE id = 1"));

        MigrateResult release3 = configure(currentRelease).target(MigrationVersion.LATEST).load().migrate();
        assertEquals(1, release3.migrationsExecuted);
        assertEquals("3", release3.targetSchemaVersion);
        assertEquals("Acme", queryString("SELECT name FROM supplier WHERE id = 1"));

        writeScript(currentRelease, "V2_1__index_catalog_sku.sql", """
                CREATE INDEX idx_catalog_sku ON catalog_item (sku);
                """);
        Flyway strict = configure(currentRelease).load();
        assertEquals(MigrationState.IGNORED, states(configure(currentRelease)).get("2.1"));
        ValidateResult refused = strict.validateWithResult();
        assertFalse(refused.validationSuccessful);
        assertEquals(CoreErrorCode.VALIDATE_ERROR, refused.errorDetails.errorCode);
        assertEquals(List.of("2.1"), invalidVersions(refused));
        FlywayValidateException rejected = assertThrows(FlywayValidateException.class, strict::migrate);
        assertTrue(rejected.getMessage().contains("2.1"), rejected.getMessage());
        assertFalse(indexExists("IDX_CATALOG_SKU"));

        MigrateResult hotfix = configure(currentRelease).outOfOrder(true).load().migrate();
        assertEquals(1, hotfix.migrationsExecuted);
        assertEquals("2.1", hotfix.targetSchemaVersion);
        assertTrue(indexExists("IDX_CATALOG_SKU"));
        Map<String, MigrationState> afterHotfix = states(configure(currentRelease));
        assertEquals(MigrationState.OUT_OF_ORDER, afterHotfix.get("2.1"));
        assertEquals(MigrationState.SUCCESS, afterHotfix.get("3"));
        assertTrue(strict.validateWithResult().validationSuccessful);
        assertEquals("3", strict.info().current().getVersion().getVersion());

        Flyway oldInstance = configure(previousRelease).load();
        Map<String, MigrationState> seenByOldInstance = states(configure(previousRelease));
        assertEquals(MigrationState.SUCCESS, seenByOldInstance.get("2"));
        assertEquals(MigrationState.FUTURE_SUCCESS, seenByOldInstance.get("2.1"));
        assertEquals(MigrationState.FUTURE_SUCCESS, seenByOldInstance.get("3"));
        assertTrue(oldInstance.validateWithResult().validationSuccessful);
        assertEquals(0, oldInstance.migrate().migrationsExecuted);
        ValidateResult strictOldInstance = configure(previousRelease)
                .ignoreMigrationPatterns("*:missing")
                .load()
                .validateWithResult();
        assertFalse(strictOldInstance.validationSuccessful);
        assertTrue(invalidVersions(strictOldInstance).contains("3"), strictOldInstance.getAllErrorMessages());

        Files.delete(currentRelease.resolve("V2_1__index_catalog_sku.sql"));
        assertEquals(MigrationState.MISSING_SUCCESS, states(configure(currentRelease)).get("2.1"));
        ValidateResult missing = strict.validateWithResult();
        assertFalse(missing.validationSuccessful);
        assertEquals(List.of("2.1"), invalidVersions(missing));
        Flyway tolerant = configure(currentRelease).ignoreMigrationPatterns("*:missing", "*:future").load();
        assertTrue(tolerant.validateWithResult().validationSuccessful);
        assertEquals(0, tolerant.migrate().migrationsExecuted);
        assertEquals(2L, queryLong("SELECT COUNT(*) FROM catalog_item"));
        assertEquals(4L, queryLong("""
                SELECT COUNT(*) FROM "flyway_schema_history" WHERE "type" = 'SQL' AND "success" = TRUE
                """));
    }

    private FluentConfiguration configure(Path release) {
        return Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .locations("filesystem:" + release.toAbsolutePath());
    }

    private static Map<String, MigrationState> states(FluentConfiguration configuration) {
        Map<String, MigrationState> states = new LinkedHashMap<>();
        for (MigrationInfo info : configuration.load().info().all()) {
            states.put(info.getVersion().getVersion(), info.getState());
        }
        return states;
    }

    private static List<String> invalidVersions(ValidateResult result) {
        return result.invalidMigrations.stream().map((ValidateOutput output) -> output.version).toList();
    }

    private static void writeScript(Path release, String name, String sql) throws IOException {
        Files.writeString(release.resolve(name), sql, StandardCharsets.UTF_8);
    }

    private boolean tableExists(String table) throws SQLException {
        return queryLong("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = '" + table + "'") == 1L;
    }

    private boolean indexExists(String index) throws SQLException {
        return queryLong("SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES WHERE INDEX_NAME = '" + index + "'") == 1L;
    }

    private long queryLong(String sql) throws SQLException {
        return Long.parseLong(queryString(sql));
    }

    private String queryString(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next(), sql);
            return resultSet.getString(1);
        }
    }
}
