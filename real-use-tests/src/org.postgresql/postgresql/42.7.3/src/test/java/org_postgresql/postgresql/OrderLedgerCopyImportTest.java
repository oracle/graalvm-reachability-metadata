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
import org.postgresql.copy.CopyIn;
import org.postgresql.copy.CopyManager;
import org.postgresql.ds.PGSimpleDataSource;
import org.postgresql.util.PSQLException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bulk-loads and exports an order ledger through the PostgreSQL COPY API of `CopyManager`.
 *
 * A nightly import streams a CSV file from a partner into a staging table with `copyIn`,
 * pushes a second batch row by row through a `CopyIn` operation, settles the ledger with SQL,
 * and exports a settlement report with `copyOut`; a malformed batch is rejected with the
 * server's SQL state and leaves the ledger untouched.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderLedgerCopyImportTest {

    private static final String POSTGRES_IMAGE = "postgres:18-alpine";

    private static final String DATABASE = "ledger";

    private static final String USER = "importer";

    private static final String PASSWORD = "s3cret";

    private static final String PARTNER_CSV = """
            order_id,customer,amount_cents,note
            1001,Acme Corp,125000,"first order, rush"
            1002,Globex,9900,
            1003,"Initech ""West""\",45050,"multi
            line note"
            """;

    private final String containerName = "real-use-postgresql-" + UUID.randomUUID();

    private PGSimpleDataSource dataSource;

    @BeforeAll
    void startDatabase() throws Exception {
        int port = freePort();
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

        dataSource = new PGSimpleDataSource();
        dataSource.setServerNames(new String[] {"127.0.0.1"});
        dataSource.setPortNumbers(new int[] {port});
        dataSource.setDatabaseName(DATABASE);
        dataSource.setUser(USER);
        dataSource.setPassword(PASSWORD);
        dataSource.setConnectTimeout(5);
        awaitDatabase();

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE orders (
                        order_id     integer PRIMARY KEY,
                        customer     text    NOT NULL,
                        amount_cents bigint  NOT NULL,
                        note         text,
                        settled      boolean NOT NULL DEFAULT false
                    )""");
        }
    }

    @AfterAll
    void stopDatabase() throws Exception {
        Process remove = new ProcessBuilder("docker", "rm", "-f", containerName).inheritIO().start();
        remove.waitFor(60, TimeUnit.SECONDS);
    }

    @Test
    void importsPartnerBatchesAndExportsSettlementReport() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            CopyManager copyManager = connection.unwrap(PGConnection.class).getCopyAPI();

            long loaded = copyManager.copyIn(
                    "COPY orders (order_id, customer, amount_cents, note) FROM STDIN WITH (FORMAT csv, HEADER true)",
                    new StringReader(PARTNER_CSV));
            assertThat(loaded).isEqualTo(3L);

            CopyIn streamed = copyManager.copyIn(
                    "COPY orders (order_id, customer, amount_cents, note) FROM STDIN WITH (FORMAT text)");
            for (String row : List.of("2001\tUmbrella\t70000\t\\N\n", "2002\tHooli\t1\tcent test\n")) {
                byte[] bytes = row.getBytes(StandardCharsets.UTF_8);
                streamed.writeToCopy(bytes, 0, bytes.length);
            }
            assertThat(streamed.isActive()).isTrue();
            assertThat(streamed.endCopy()).isEqualTo(2L);
            assertThat(streamed.isActive()).isFalse();

            try (Statement statement = connection.createStatement()) {
                assertThat(statement.executeUpdate("UPDATE orders SET settled = true WHERE amount_cents >= 10000"))
                        .isEqualTo(3);
            }

            try (Statement statement = connection.createStatement();
                    ResultSet notes = statement.executeQuery(
                            "SELECT order_id, customer, note FROM orders WHERE order_id IN (1002, 1003, 2001)"
                                    + " ORDER BY order_id")) {
                assertThat(notes.next()).isTrue();
                assertThat(notes.getString("note")).isNull();
                assertThat(notes.next()).isTrue();
                assertThat(notes.getString("customer")).isEqualTo("Initech \"West\"");
                assertThat(notes.getString("note")).isEqualTo("multi\nline note");
                assertThat(notes.next()).isTrue();
                assertThat(notes.getString("note")).isNull();
                assertThat(notes.next()).isFalse();
            }

            StringWriter report = new StringWriter();
            long exported = copyManager.copyOut("""
                    COPY (SELECT order_id, customer, amount_cents FROM orders WHERE settled ORDER BY order_id)
                    TO STDOUT WITH (FORMAT csv, HEADER true)""", report);
            assertThat(exported).isEqualTo(3L);
            assertThat(report.toString()).isEqualTo("""
                    order_id,customer,amount_cents
                    1001,Acme Corp,125000
                    1003,"Initech ""West""\",45050
                    2001,Umbrella,70000
                    """);
        }
    }

    @Test
    void rejectsMalformedBatchWithoutTouchingLedger() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            CopyManager copyManager = connection.unwrap(PGConnection.class).getCopyAPI();
            long before = countOrders(connection);

            byte[] malformed = """
                    3001,Wayne Enterprises,5000,
                    3002,Stark Industries,not-a-number,
                    """.getBytes(StandardCharsets.UTF_8);
            assertThatThrownBy(() -> copyManager.copyIn(
                    "COPY orders (order_id, customer, amount_cents, note) FROM STDIN WITH (FORMAT csv)",
                    new ByteArrayInputStream(malformed)))
                    .isInstanceOf(PSQLException.class)
                    .satisfies(error -> {
                        PSQLException psqlError = (PSQLException) error;
                        assertThat(psqlError.getSQLState()).isEqualTo("22P02");
                        assertThat(psqlError.getServerErrorMessage()).isNotNull();
                        assertThat(psqlError.getServerErrorMessage().getWhere()).contains("COPY orders");
                    });
            connection.rollback();

            assertThat(countOrders(connection)).isEqualTo(before);
            connection.commit();
        }
    }

    private static long countOrders(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet count = statement.executeQuery("SELECT count(*) FROM orders WHERE order_id >= 3000")) {
            assertThat(count.next()).isTrue();
            return count.getLong(1);
        }
    }

    private void awaitDatabase() throws Exception {
        long deadline = System.currentTimeMillis() + 120_000L;
        SQLException lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection probe = dataSource.getConnection(); Statement statement = probe.createStatement()) {
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
