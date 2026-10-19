/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mariadb_jdbc.mariadb_java_client;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mariadb.jdbc.MariaDbDataSource;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Result streaming and statement execution limits: rows fetched in `fetchSize` chunks
/// from a large result set, a second query issued while a stream is still open,
/// `setMaxRows` and `setLargeMaxRows` truncation, `setQueryTimeout` raising
/// `SQLTimeoutException` on a connection that stays usable, and the same export over a
/// zlib-compressed connection opened with `useCompression`.
///
/// The scenario is a ledger exporter that walks a shipment ledger far larger than one
/// fetch, the way a reporting job pages through history, against a real MariaDB server.
public class LedgerExportStreamingTest {

    private static final String IMAGE = "mariadb:13.0";

    private static final String CONTAINER = "real-use-mariadb-streaming-" + UUID.randomUUID();

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(120);

    private static final String DATABASE = "ledger";

    private static final String USER = "exporter";

    private static final String PASSWORD = "exporter-secret";

    private static final int SHIPMENTS = 5000;

    private static final int FETCH_SIZE = 250;

    private static String url;

    private static MariaDbDataSource ledgerSource;

    @BeforeAll
    static void startServer() throws Exception {
        docker("run", "-d", "--name", CONTAINER, "-p", "127.0.0.1::3306",
                "-e", "MARIADB_DATABASE=" + DATABASE,
                "-e", "MARIADB_USER=" + USER,
                "-e", "MARIADB_PASSWORD=" + PASSWORD,
                "-e", "MARIADB_ALLOW_EMPTY_ROOT_PASSWORD=true",
                IMAGE);
        String mapping = docker("port", CONTAINER, "3306/tcp").lines().findFirst().orElseThrow();
        int port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));
        url = "jdbc:mariadb://127.0.0.1:" + port + "/" + DATABASE;

        ledgerSource = dataSource("useCompression=false");
        awaitServer(ledgerSource);

        try (Connection connection = ledgerSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE shipment (
                        id INT NOT NULL PRIMARY KEY,
                        carrier VARCHAR(16) NOT NULL,
                        weight_kg DECIMAL(10, 2) NOT NULL,
                        note VARCHAR(255) NOT NULL
                    ) ENGINE=InnoDB""");
            statement.execute("CREATE TABLE carrier (code VARCHAR(16) NOT NULL PRIMARY KEY, name VARCHAR(64) NOT NULL)");
            statement.execute("INSERT INTO carrier VALUES ('ROAD', 'Road Freight'), ('RAIL', 'Rail Cargo'), "
                    + "('SEA', 'Ocean Lines')");
        }
        try (Connection connection = ledgerSource.getConnection();
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO shipment (id, carrier, weight_kg, note) VALUES (?, ?, ?, ?)")) {
            connection.setAutoCommit(false);
            for (int id = 1; id <= SHIPMENTS; id++) {
                insert.setInt(1, id);
                insert.setString(2, carrierFor(id));
                insert.setBigDecimal(3, weightFor(id));
                insert.setString(4, "consignment " + id + " " + "x".repeat(120));
                insert.addBatch();
            }
            insert.executeBatch();
            connection.commit();
        }
    }

    @AfterAll
    static void stopServer() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @Test
    void streamsTheWholeLedgerInFetchSizedChunks() throws Exception {
        LedgerExporter exporter = new LedgerExporter(ledgerSource);

        LedgerTotals totals = exporter.exportAll(FETCH_SIZE);

        assertThat(totals.fetchSize()).isEqualTo(FETCH_SIZE);
        assertThat(totals.rows()).isEqualTo(SHIPMENTS);
        assertThat(totals.firstId()).isEqualTo(1);
        assertThat(totals.lastId()).isEqualTo(SHIPMENTS);
        assertThat(totals.totalWeight()).isEqualByComparingTo(expectedTotalWeight());
    }

    @Test
    void answersALookupWhileAStreamIsStillOpenOnTheSameConnection() throws Exception {
        LedgerExporter exporter = new LedgerExporter(ledgerSource);

        List<String> labels = exporter.exportWithCarrierNames(FETCH_SIZE, 3);

        assertThat(labels).containsExactly(
                "1 Rail Cargo", "2 Ocean Lines", "3 Road Freight");
    }

    @Test
    void capsReportsWithMaxRows() throws Exception {
        LedgerExporter exporter = new LedgerExporter(ledgerSource);

        assertThat(exporter.heaviestShipments(5)).containsExactly(4999L, 4998L, 4997L, 4996L, 4995L);
        assertThat(exporter.heaviestShipmentsLarge(3L)).containsExactly(4999L, 4998L, 4997L);
    }

    @Test
    void abortsARunawayQueryAndKeepsTheConnectionUsable() throws Exception {
        try (Connection connection = ledgerSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(1);
                assertThat(statement.getQueryTimeout()).isEqualTo(1);

                assertThatThrownBy(() -> statement.executeQuery("""
                        SELECT COUNT(*) FROM shipment a, shipment b, shipment c
                        WHERE a.weight_kg + b.weight_kg > c.weight_kg"""))
                        .isInstanceOf(SQLTimeoutException.class);
            }
            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM carrier")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(3);
            }
        }
    }

    @Test
    void exportsTheSameLedgerOverACompressedConnection() throws Exception {
        LedgerExporter exporter = new LedgerExporter(dataSource("useCompression=true"));

        LedgerTotals totals = exporter.exportAll(FETCH_SIZE);

        assertThat(totals.rows()).isEqualTo(SHIPMENTS);
        assertThat(totals.totalWeight()).isEqualByComparingTo(expectedTotalWeight());
        try (Connection connection = exporter.source.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SHOW SESSION STATUS LIKE 'Compression'")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(2)).isEqualTo("ON");
        }
    }

    private static String carrierFor(int id) {
        return switch (id % 3) {
            case 0 -> "ROAD";
            case 1 -> "RAIL";
            default -> "SEA";
        };
    }

    private static BigDecimal weightFor(int id) {
        return BigDecimal.valueOf(id).movePointLeft(1);
    }

    private static BigDecimal expectedTotalWeight() {
        BigDecimal total = BigDecimal.ZERO;
        for (int id = 1; id <= SHIPMENTS; id++) {
            total = total.add(weightFor(id));
        }
        return total;
    }

    private static MariaDbDataSource dataSource(String options) throws SQLException {
        MariaDbDataSource source = new MariaDbDataSource(url + "?" + options);
        source.setUser(USER);
        source.setPassword(PASSWORD);
        return source;
    }

    private static void awaitServer(MariaDbDataSource source) throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        SQLException last = null;
        while (System.nanoTime() < deadline) {
            try (Connection connection = source.getConnection()) {
                if (connection.isValid(5)) {
                    return;
                }
            } catch (SQLException e) {
                last = e;
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("MariaDB did not accept connections in time", last);
    }

    private static String docker(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (InputStream stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        return output;
    }

    /// Summary a full ledger export reports once the stream is drained.
    record LedgerTotals(int fetchSize, int rows, int firstId, int lastId, BigDecimal totalWeight) {
    }

    /// Reporting job that pages through the shipment ledger without loading it whole.
    static final class LedgerExporter {

        private final MariaDbDataSource source;

        LedgerExporter(MariaDbDataSource source) {
            this.source = source;
        }

        /// Walks every shipment in id order, pulling `fetchSize` rows from the server at a time.
        LedgerTotals exportAll(int fetchSize) throws SQLException {
            try (Connection connection = source.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.setFetchSize(fetchSize);
                try (ResultSet rows = statement.executeQuery(
                        "SELECT id, weight_kg, note FROM shipment ORDER BY id")) {
                    int count = 0;
                    int firstId = -1;
                    int lastId = -1;
                    BigDecimal total = BigDecimal.ZERO;
                    while (rows.next()) {
                        int id = rows.getInt("id");
                        assertThat(rows.getString("note")).startsWith("consignment " + id + " ");
                        if (firstId < 0) {
                            firstId = id;
                        }
                        lastId = id;
                        total = total.add(rows.getBigDecimal("weight_kg"));
                        count++;
                    }
                    return new LedgerTotals(rows.getFetchSize(), count, firstId, lastId, total);
                }
            }
        }

        /// Streams the ledger and, for the first rows, resolves the carrier name with a second
        /// query on the same connection while the stream is still open.
        List<String> exportWithCarrierNames(int fetchSize, int limit) throws SQLException {
            List<String> labels = new ArrayList<>();
            try (Connection connection = source.getConnection();
                    Statement statement = connection.createStatement();
                    PreparedStatement carrierName = connection.prepareStatement(
                            "SELECT name FROM carrier WHERE code = ?")) {
                statement.setFetchSize(fetchSize);
                try (ResultSet rows = statement.executeQuery("SELECT id, carrier FROM shipment ORDER BY id")) {
                    while (labels.size() < limit && rows.next()) {
                        carrierName.setString(1, rows.getString("carrier"));
                        try (ResultSet name = carrierName.executeQuery()) {
                            assertThat(name.next()).isTrue();
                            labels.add(rows.getInt("id") + " " + name.getString(1));
                        }
                    }
                    int remaining = 0;
                    while (rows.next()) {
                        remaining++;
                    }
                    assertThat(remaining).isEqualTo(SHIPMENTS - limit);
                }
            }
            return labels;
        }

        /// Ids of the heaviest shipments, capped by `setMaxRows`.
        List<Long> heaviestShipments(int limit) throws SQLException {
            try (Connection connection = source.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT id FROM shipment WHERE id < ? ORDER BY weight_kg DESC")) {
                statement.setMaxRows(limit);
                statement.setInt(1, SHIPMENTS);
                return ids(statement.executeQuery());
            }
        }

        /// Ids of the heaviest shipments, capped by `setLargeMaxRows`.
        List<Long> heaviestShipmentsLarge(long limit) throws SQLException {
            try (Connection connection = source.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.setLargeMaxRows(limit);
                assertThat(statement.getLargeMaxRows()).isEqualTo(limit);
                return ids(statement.executeQuery(
                        "SELECT id FROM shipment WHERE id < " + SHIPMENTS + " ORDER BY weight_kg DESC"));
            }
        }

        private static List<Long> ids(ResultSet rows) throws SQLException {
            List<Long> ids = new ArrayList<>();
            try (rows) {
                while (rows.next()) {
                    ids.add(rows.getLong(1));
                }
            }
            return ids;
        }
    }
}
