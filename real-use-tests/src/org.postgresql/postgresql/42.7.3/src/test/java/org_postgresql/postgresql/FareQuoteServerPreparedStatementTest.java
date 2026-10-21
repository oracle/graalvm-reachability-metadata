/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_postgresql.postgresql;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.postgresql.PGConnection;
import org.postgresql.PGStatement;
import org.postgresql.ds.PGSimpleDataSource;
import org.postgresql.jdbc.AutoSave;
import org.postgresql.jdbc.PreferQueryMode;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the driver's server-side prepared statement lifecycle and query execution modes
 * (`prepareThreshold`, `PGStatement.isUseServerPrepare`, the per-connection statement cache,
 * `preferQueryMode` and `autosave` recovery from invalidated cached plans).
 *
 * A fare quoting service looks up airline fares by route over and over. The driver first sends
 * the lookup as an unnamed statement, promotes it to a named server statement once it has run
 * `prepareThreshold` times, reuses that statement when the service prepares the same SQL again,
 * and evicts it when the cache overflows. The service observes each step through the session's
 * `pg_prepared_statements` view. It also runs the lookup with server preparation disabled, in
 * simple query mode with parameters inlined by the driver, and across a schema change that
 * invalidates a cached plan inside a transaction.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FareQuoteServerPreparedStatementTest {

    private static final String POSTGRES_IMAGE = "postgres:18-alpine";

    private static final String DATABASE = "fares";

    private static final String USER = "quoter";

    private static final String PASSWORD = "s3cret";

    private static final String FARE_BY_ROUTE = "SELECT fare FROM route_fares WHERE route = ?";

    private static final String CARRIER_BY_ROUTE = "SELECT carrier FROM route_fares WHERE route = ?";

    private static final String ROUTES_BY_CARRIER = "SELECT route FROM route_fares WHERE carrier = ? ORDER BY route";

    private final String containerName = "real-use-postgresql-" + UUID.randomUUID();

    private int port;

    @BeforeAll
    void startDatabase() throws Exception {
        port = freePort();
        Process run = new ProcessBuilder("docker", "run", "-d", "--rm", "--name", containerName,
                "-p", port + ":5432",
                "-e", "POSTGRES_DB=" + DATABASE,
                "-e", "POSTGRES_USER=" + USER,
                "-e", "POSTGRES_PASSWORD=" + PASSWORD,
                POSTGRES_IMAGE)
                .inheritIO()
                .start();
        assertThat(run.waitFor(120, TimeUnit.SECONDS)).isTrue();
        assertThat(run.exitValue()).isZero();

        awaitDatabase();
        try (Connection connection = source().getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE route_fares (
                        route   text          PRIMARY KEY,
                        carrier text          NOT NULL,
                        fare    numeric(8,2)  NOT NULL
                    )""");
            statement.execute("""
                    INSERT INTO route_fares VALUES
                        ('BEG-VIE', 'Austrian', 129.00),
                        ('BEG-ZRH', 'Swiss', 214.50),
                        ('ORD-JFK', 'United', 189.99),
                        ('O''Hare-LGA', 'American', 99.00)""");
        }
    }

    @AfterAll
    void stopDatabase() throws Exception {
        Process remove = new ProcessBuilder("docker", "rm", "-f", containerName).inheritIO().start();
        remove.waitFor(60, TimeUnit.SECONDS);
    }

    @Test
    void repeatedLookupIsPromotedToNamedServerStatementAtThreshold() throws Exception {
        try (Connection connection = source().getConnection();
                PreparedStatement lookup = connection.prepareStatement(FARE_BY_ROUTE)) {
            PGStatement pgLookup = lookup.unwrap(PGStatement.class);
            assertThat(pgLookup.getPrepareThreshold()).isEqualTo(5);
            assertThat(connection.unwrap(PGConnection.class).getPrepareThreshold()).isEqualTo(5);
            assertThat(pgLookup.isUseServerPrepare()).isFalse();

            for (int i = 0; i < 3; i++) {
                assertThat(fare(lookup, "BEG-VIE")).isEqualByComparingTo("129.00");
            }
            assertThat(serverStatements(connection, "route_fares WHERE route")).isEmpty();

            for (int i = 0; i < 4; i++) {
                assertThat(fare(lookup, "BEG-ZRH")).isEqualByComparingTo("214.50");
            }
            assertThat(pgLookup.isUseServerPrepare()).isTrue();
            assertThat(serverStatements(connection, "route_fares WHERE route"))
                    .containsExactly("SELECT fare FROM route_fares WHERE route = $1");
            assertThat(fare(lookup, "ORD-JFK")).isEqualByComparingTo("189.99");
        }
    }

    @Test
    void statementCacheReusesAndEvictsServerStatements() throws Exception {
        PGSimpleDataSource source = source();
        source.setPrepareThreshold(1);
        source.setPreparedStatementCacheQueries(2);
        try (Connection connection = source.getConnection()) {
            try (PreparedStatement lookup = connection.prepareStatement(FARE_BY_ROUTE)) {
                assertThat(lookup.unwrap(PGStatement.class).isUseServerPrepare()).isTrue();
                assertThat(fare(lookup, "BEG-VIE")).isEqualByComparingTo("129.00");
            }
            assertThat(serverStatements(connection, "route_fares WHERE")).hasSize(1);

            try (PreparedStatement againLookup = connection.prepareStatement(FARE_BY_ROUTE)) {
                assertThat(fare(againLookup, "ORD-JFK")).isEqualByComparingTo("189.99");
            }
            assertThat(serverStatements(connection, "route_fares WHERE"))
                    .containsExactly("SELECT fare FROM route_fares WHERE route = $1");

            try (PreparedStatement carrier = connection.prepareStatement(CARRIER_BY_ROUTE)) {
                carrier.setString(1, "BEG-ZRH");
                try (ResultSet row = carrier.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getString(1)).isEqualTo("Swiss");
                }
            }
            assertThat(serverStatements(connection, "route_fares WHERE")).containsExactly(
                    "SELECT carrier FROM route_fares WHERE route = $1",
                    "SELECT fare FROM route_fares WHERE route = $1");

            try (PreparedStatement routes = connection.prepareStatement(ROUTES_BY_CARRIER)) {
                routes.setString(1, "Swiss");
                assertThat(strings(routes)).containsExactly("BEG-ZRH");
            }
            assertThat(serverStatements(connection, "route_fares WHERE")).containsExactly(
                    "SELECT carrier FROM route_fares WHERE route = $1",
                    "SELECT route FROM route_fares WHERE carrier = $1 ORDER BY route");
        }
    }

    @Test
    void zeroThresholdKeepsEveryExecutionUnnamed() throws Exception {
        PGSimpleDataSource source = source();
        source.setPrepareThreshold(0);
        try (Connection connection = source.getConnection();
                PreparedStatement lookup = connection.prepareStatement(FARE_BY_ROUTE)) {
            PGStatement pgLookup = lookup.unwrap(PGStatement.class);
            for (int i = 0; i < 10; i++) {
                assertThat(fare(lookup, "BEG-VIE")).isEqualByComparingTo("129.00");
            }
            assertThat(pgLookup.isUseServerPrepare()).isFalse();
            assertThat(serverStatements(connection, "route_fares WHERE")).isEmpty();

            pgLookup.setPrepareThreshold(1);
            assertThat(pgLookup.isUseServerPrepare()).isTrue();
            assertThat(fare(lookup, "ORD-JFK")).isEqualByComparingTo("189.99");
            assertThat(serverStatements(connection, "route_fares WHERE")).hasSize(1);
        }
    }

    @Test
    void simpleQueryModeInlinesParametersAndRunsMultiStatementScripts() throws Exception {
        PGSimpleDataSource source = source();
        source.setPreferQueryMode(PreferQueryMode.SIMPLE);
        try (Connection connection = source.getConnection()) {
            assertThat(connection.unwrap(PGConnection.class).getPreferQueryMode()).isEqualTo(PreferQueryMode.SIMPLE);

            try (PreparedStatement lookup = connection.prepareStatement(FARE_BY_ROUTE)) {
                for (int i = 0; i < 8; i++) {
                    assertThat(fare(lookup, "O'Hare-LGA")).isEqualByComparingTo("99.00");
                }
                assertThat(fare(lookup, "'; DROP TABLE route_fares; --")).isNull();
            }
            assertThat(serverStatements(connection, "route_fares WHERE")).isEmpty();

            try (Statement script = connection.createStatement()) {
                assertThat(script.execute("""
                        SELECT count(*) FROM route_fares;
                        SELECT route FROM route_fares WHERE carrier = 'United'""")).isTrue();
                try (ResultSet count = script.getResultSet()) {
                    assertThat(count.next()).isTrue();
                    assertThat(count.getInt(1)).isEqualTo(4);
                }
                assertThat(script.getMoreResults()).isTrue();
                try (ResultSet routes = script.getResultSet()) {
                    assertThat(routes.next()).isTrue();
                    assertThat(routes.getString(1)).isEqualTo("ORD-JFK");
                    assertThat(routes.next()).isFalse();
                }
                assertThat(script.getMoreResults()).isFalse();
                assertThat(script.getUpdateCount()).isEqualTo(-1);
            }
        }
    }

    @Test
    void conservativeAutosaveReplansCachedStatementAfterSchemaChange() throws Exception {
        try (Connection connection = source().getConnection(); Statement ddl = connection.createStatement()) {
            ddl.execute("CREATE TABLE seasonal_fares (route text PRIMARY KEY, fare numeric(8,2) NOT NULL)");
            ddl.execute("INSERT INTO seasonal_fares VALUES ('BEG-VIE', 149.00)");
        }

        PGSimpleDataSource source = source();
        source.setPrepareThreshold(1);
        source.setAutosave(AutoSave.CONSERVATIVE);
        try (Connection connection = source.getConnection()) {
            assertThat(connection.unwrap(PGConnection.class).getAutosave()).isEqualTo(AutoSave.CONSERVATIVE);
            connection.setAutoCommit(false);
            try (PreparedStatement lookup = connection.prepareStatement("SELECT * FROM seasonal_fares WHERE route = ?")) {
                lookup.setString(1, "BEG-VIE");
                try (ResultSet row = lookup.executeQuery()) {
                    assertThat(row.getMetaData().getColumnCount()).isEqualTo(2);
                    assertThat(row.next()).isTrue();
                }

                try (Statement ddl = connection.createStatement()) {
                    ddl.execute("ALTER TABLE seasonal_fares ADD COLUMN currency text NOT NULL DEFAULT 'EUR'");
                }

                try (ResultSet row = lookup.executeQuery()) {
                    assertThat(row.getMetaData().getColumnCount()).isEqualTo(3);
                    assertThat(row.next()).isTrue();
                    assertThat(row.getBigDecimal("fare")).isEqualByComparingTo("149.00");
                    assertThat(row.getString("currency")).isEqualTo("EUR");
                }
            }
            connection.commit();
        }

        try (Connection connection = source().getConnection();
                Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery("SELECT currency FROM seasonal_fares WHERE route = 'BEG-VIE'")) {
            assertThat(row.next()).isTrue();
            assertThat(row.getString(1)).isEqualTo("EUR");
        }
    }

    private static BigDecimal fare(PreparedStatement lookup, String route) throws SQLException {
        lookup.setString(1, route);
        try (ResultSet row = lookup.executeQuery()) {
            return row.next() ? row.getBigDecimal(1) : null;
        }
    }

    private static List<String> strings(PreparedStatement query) throws SQLException {
        List<String> values = new ArrayList<>();
        try (ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                values.add(rows.getString(1));
            }
        }
        return values;
    }

    /// Lists the named statements this session holds on the server, ordered by their text.
    private static List<String> serverStatements(Connection connection, String fragment) throws SQLException {
        List<String> statements = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT statement FROM pg_prepared_statements WHERE NOT from_sql ORDER BY statement")) {
            while (rows.next()) {
                String text = rows.getString(1);
                if (text.contains(fragment)) {
                    statements.add(text);
                }
            }
        }
        return statements;
    }

    private PGSimpleDataSource source() {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setServerNames(new String[] {"127.0.0.1"});
        source.setPortNumbers(new int[] {port});
        source.setDatabaseName(DATABASE);
        source.setUser(USER);
        source.setPassword(PASSWORD);
        source.setConnectTimeout(5);
        return source;
    }

    private void awaitDatabase() throws Exception {
        long deadline = System.currentTimeMillis() + 120_000L;
        SQLException lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection probe = source().getConnection(); Statement statement = probe.createStatement()) {
                statement.execute("SELECT 1");
                return;
            } catch (SQLException e) {
                lastFailure = e;
                Thread.sleep(500L);
            }
        }
        throw new IllegalStateException("PostgreSQL did not accept connections in " + containerName, lastFailure);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
