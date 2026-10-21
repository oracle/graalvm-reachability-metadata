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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.output.ValidateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PostgreSQL database support: managing several schemas created by Flyway with `schemas` and
 * `createSchemas`, keeping the schema history in the default schema, and parsing
 * PostgreSQL-specific script syntax such as dollar-quoted PL/pgSQL bodies, `COPY ... FROM STDIN`
 * data blocks, and non-transactional `CREATE INDEX CONCURRENTLY` under the session-level
 * advisory lock selected through the `postgresql.transactional.lock` plugin setting.
 *
 * A billing service owns two schemas on a real PostgreSQL server: `billing` for its tables and
 * `audit` for an append-only log filled by a trigger. On first startup Flyway creates both
 * schemas, installs the trigger function, bulk-loads the currency reference data and builds an
 * index without locking writers. A second instance of the service then starts against the same
 * database and finds nothing left to do.
 */
public class PostgresTenantSchemasMigrationTest {

    private static final String IMAGE = "postgres:18-alpine";

    private static final String USER = "billing_app";

    private static final String PASSWORD = "billing-secret";

    private static final String CONTAINER = "flyway-pg-" + UUID.randomUUID();

    private static String jdbcUrl;

    @TempDir
    static Path migrations;

    @BeforeAll
    static void startPostgres() throws Exception {
        docker("run", "-d", "--name", CONTAINER,
                "-e", "POSTGRES_USER=" + USER,
                "-e", "POSTGRES_PASSWORD=" + PASSWORD,
                "-e", "POSTGRES_DB=billing_db",
                "-p", "127.0.0.1::5432", IMAGE);
        String mapping = docker("port", CONTAINER, "5432/tcp").lines().findFirst().orElseThrow();
        int port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1).trim());
        jdbcUrl = "jdbc:postgresql://127.0.0.1:" + port + "/billing_db";
        awaitDatabase();
        writeMigrations();
    }

    @AfterAll
    static void removePostgres() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @Test
    void createsSchemasAndAppliesPostgresSpecificScripts() throws SQLException {
        MigrateResult result = configureFlyway().migrate();

        assertTrue(result.success);
        assertEquals(4, result.migrationsExecuted);
        assertEquals("4", result.targetSchemaVersion);
        assertEquals("billing, audit", result.schemaName);
        assertEquals("billing_db", result.database);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, USER, PASSWORD)) {
            assertEquals(List.of("audit", "billing"), queryStrings(connection, """
                    SELECT schema_name FROM information_schema.schemata
                    WHERE schema_name IN ('billing', 'audit') ORDER BY schema_name
                    """));
            assertEquals(List.of("billing"), queryStrings(connection, """
                    SELECT table_schema FROM information_schema.tables
                    WHERE table_name = 'flyway_schema_history'
                    """));
            assertEquals(List.of("billing"), queryStrings(connection, """
                    SELECT table_schema FROM information_schema.tables
                    WHERE table_name = 'invoice'
                    """));
            assertEquals(List.of("SCHEMA:\"billing\",\"audit\""), queryStrings(connection, """
                    SELECT type || ':' || script FROM billing.flyway_schema_history
                    WHERE type = 'SCHEMA'
                    """));

            assertEquals(List.of("CHF|Swiss franc", "EUR|Euro", "USD|US dollar"),
                    queryStrings(connection, """
                            SELECT code || '|' || name FROM billing.currency ORDER BY code
                            """));
            assertEquals(List.of("true"), queryStrings(connection, """
                    SELECT i.indisvalid::text FROM pg_index i
                    JOIN pg_class c ON c.oid = i.indexrelid
                    JOIN pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = 'billing' AND c.relname = 'invoice_customer_idx'
                    """));

            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO billing.invoice (customer, amount_cents, currency) VALUES (?, ?, ?)
                    """)) {
                insert.setString(1, "acme");
                insert.setLong(2, 12_500L);
                insert.setString(3, "EUR");
                insert.executeUpdate();
                insert.setString(1, "globex");
                insert.setLong(2, 990L);
                insert.setString(3, "CHF");
                insert.executeUpdate();
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        UPDATE billing.invoice SET amount_cents = 13000 WHERE customer = 'acme'
                        """);
            }
            assertEquals(List.of("INSERT acme 12500", "INSERT globex 990", "UPDATE acme 13000"),
                    queryStrings(connection, """
                            SELECT action || ' ' || customer || ' ' || amount_cents
                            FROM audit.invoice_log ORDER BY id
                            """));
        }

        Flyway secondInstance = configureFlyway();
        MigrateResult secondRun = secondInstance.migrate();
        assertTrue(secondRun.success);
        assertEquals(0, secondRun.migrationsExecuted);
        assertEquals("4", secondRun.initialSchemaVersion);

        ValidateResult validation = secondInstance.validateWithResult();
        assertTrue(validation.validationSuccessful, String.valueOf(validation.invalidMigrations));

        List<String> applied = new ArrayList<>();
        for (MigrationInfo info : secondInstance.info().applied()) {
            if (info.getVersion() != null) {
                assertEquals(MigrationState.SUCCESS, info.getState());
                applied.add(info.getVersion() + " " + info.getDescription());
            }
        }
        assertEquals(List.of(
                "1 create invoices",
                "2 audit trigger",
                "3 load currencies",
                "4 index invoice customer"), applied);
        assertEquals("4", secondInstance.info().current().getVersion().getVersion());
    }

    private static Flyway configureFlyway() {
        return Flyway.configure()
                .dataSource(jdbcUrl, USER, PASSWORD)
                .schemas("billing", "audit")
                .createSchemas(true)
                .locations("filesystem:" + migrations.toAbsolutePath())
                // A transaction-scoped advisory lock would make CREATE INDEX CONCURRENTLY wait
                // on Flyway's own lock transaction, so the session-level lock is used instead.
                .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
                .load();
    }

    private static void writeMigrations() throws IOException {
        writeScript("V1__create_invoices.sql", """
                CREATE TABLE currency (
                    code CHAR(3) PRIMARY KEY,
                    name TEXT NOT NULL
                );
                CREATE TABLE invoice (
                    id BIGSERIAL PRIMARY KEY,
                    customer TEXT NOT NULL,
                    amount_cents BIGINT NOT NULL,
                    currency CHAR(3) NOT NULL REFERENCES currency (code)
                );
                CREATE TABLE audit.invoice_log (
                    id BIGSERIAL PRIMARY KEY,
                    action TEXT NOT NULL,
                    customer TEXT NOT NULL,
                    amount_cents BIGINT NOT NULL
                );
                """);
        writeScript("V2__audit_trigger.sql", """
                CREATE FUNCTION audit.log_invoice() RETURNS trigger AS $body$
                BEGIN
                    INSERT INTO audit.invoice_log (action, customer, amount_cents)
                    VALUES (TG_OP, NEW.customer, NEW.amount_cents);
                    RETURN NEW;
                END;
                $body$ LANGUAGE plpgsql;

                CREATE TRIGGER invoice_audit
                    AFTER INSERT OR UPDATE ON ${flyway:defaultSchema}.invoice
                    FOR EACH ROW EXECUTE FUNCTION audit.log_invoice();
                """);
        writeScript("V3__load_currencies.sql", """
                COPY currency (code, name) FROM STDIN;
                EUR\tEuro
                USD\tUS dollar
                CHF\tSwiss franc
                \\.
                """);
        writeScript("V4__index_invoice_customer.sql", """
                CREATE INDEX CONCURRENTLY invoice_customer_idx ON invoice (customer);
                """);
    }

    private static void writeScript(String name, String sql) throws IOException {
        Files.writeString(migrations.resolve(name), sql, StandardCharsets.UTF_8);
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
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        SQLException lastFailure = null;
        while (System.nanoTime() < deadline) {
            try (Connection connection = DriverManager.getConnection(jdbcUrl, USER, PASSWORD);
                    Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
                return;
            } catch (SQLException e) {
                lastFailure = e;
                Thread.sleep(500);
            }
        }
        throw new IllegalStateException("PostgreSQL did not accept connections", lastFailure);
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
