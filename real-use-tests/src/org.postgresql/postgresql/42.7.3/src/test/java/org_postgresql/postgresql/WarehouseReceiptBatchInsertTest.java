/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_postgresql.postgresql;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.postgresql.PGProperty;
import org.postgresql.ds.PGSimpleDataSource;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.postgresql.util.ServerErrorMessage;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Records warehouse goods receipts through JDBC batched writes: `executeBatch` and
 * `executeLargeBatch`, `reWriteBatchedInserts`, generated keys and `BatchUpdateException`.
 *
 * A receiving desk books each pallet as one batched insert and reads back the generated
 * receipt numbers, a bulk importer relies on the driver rewriting the batch into multi-row
 * inserts, a mixed statement batch adjusts stock, and a batch that hits a duplicate SKU
 * reports the failing constraint while a savepoint keeps the earlier work of the transaction.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WarehouseReceiptBatchInsertTest {

    private static final String POSTGRES_IMAGE = "postgres:18-alpine";

    private static final String DATABASE = "warehouse";

    private static final String USER = "receiver";

    private static final String PASSWORD = "s3cret";

    private static final String INSERT_RECEIPT = "INSERT INTO receipts (sku, quantity, dock) VALUES (?, ?, ?)";

    private final String containerName = "real-use-postgresql-" + UUID.randomUUID();

    private PGSimpleDataSource dataSource;

    private PGSimpleDataSource rewritingDataSource;

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

        dataSource = newDataSource(port);
        rewritingDataSource = newDataSource(port);
        rewritingDataSource.setReWriteBatchedInserts(true);
        assertThat(rewritingDataSource.getProperty(PGProperty.REWRITE_BATCHED_INSERTS)).isEqualTo("true");
        awaitDatabase();

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE receipts (
                        id       bigserial PRIMARY KEY,
                        sku      text      NOT NULL CONSTRAINT receipts_sku_key UNIQUE,
                        quantity integer   NOT NULL CHECK (quantity > 0),
                        dock     text      NOT NULL
                    )""");
            statement.execute("""
                    CREATE TABLE stock (
                        sku     text    PRIMARY KEY,
                        on_hand integer NOT NULL
                    )""");
        }
    }

    @AfterAll
    void stopDatabase() throws Exception {
        Process remove = new ProcessBuilder("docker", "rm", "-f", containerName).inheritIO().start();
        remove.waitFor(60, TimeUnit.SECONDS);
    }

    @BeforeEach
    void emptyTables() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE receipts, stock RESTART IDENTITY");
        }
    }

    @Test
    void palletBatchReturnsGeneratedReceiptNumbers() throws Exception {
        List<Receipt> pallet = List.of(
                new Receipt("SKU-1001", 40, "dock-a"),
                new Receipt("SKU-1002", 12, "dock-a"),
                new Receipt("SKU-1003", 7, "dock-b"));

        try (Connection connection = dataSource.getConnection();
                PreparedStatement insert = connection.prepareStatement(INSERT_RECEIPT, new String[] {"id", "sku"})) {
            for (Receipt receipt : pallet) {
                bind(insert, receipt);
                insert.addBatch();
            }
            assertThat(insert.executeBatch()).containsExactly(1, 1, 1);

            List<Long> receiptNumbers = new ArrayList<>();
            List<String> receivedSkus = new ArrayList<>();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                assertThat(keys.getMetaData().getColumnCount()).isEqualTo(2);
                while (keys.next()) {
                    receiptNumbers.add(keys.getLong("id"));
                    receivedSkus.add(keys.getString("sku"));
                }
            }
            assertThat(receiptNumbers).containsExactly(1L, 2L, 3L);
            assertThat(receivedSkus).containsExactly("SKU-1001", "SKU-1002", "SKU-1003");

            try (PreparedStatement lookup = connection.prepareStatement(
                    "SELECT sku, quantity, dock FROM receipts WHERE id = ?")) {
                lookup.setLong(1, receiptNumbers.get(2));
                try (ResultSet row = lookup.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getString("sku")).isEqualTo("SKU-1003");
                    assertThat(row.getInt("quantity")).isEqualTo(7);
                    assertThat(row.getString("dock")).isEqualTo("dock-b");
                }
            }
        }
    }

    @Test
    void bulkImportIsRewrittenIntoMultiRowInserts() throws Exception {
        int receiptCount = 250;
        try (Connection connection = rewritingDataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(INSERT_RECEIPT)) {
                for (int i = 1; i <= receiptCount; i++) {
                    bind(insert, new Receipt("BULK-" + i, i, i % 2 == 0 ? "dock-a" : "dock-b"));
                    insert.addBatch();
                }
                int[] updateCounts = insert.executeBatch();
                assertThat(updateCounts).hasSize(receiptCount).containsOnly(Statement.SUCCESS_NO_INFO);
            }
            connection.commit();

            try (Statement statement = connection.createStatement();
                    ResultSet totals = statement.executeQuery("""
                            SELECT count(*), sum(quantity), count(*) FILTER (WHERE dock = 'dock-a')
                            FROM receipts""")) {
                assertThat(totals.next()).isTrue();
                assertThat(totals.getInt(1)).isEqualTo(receiptCount);
                assertThat(totals.getLong(2)).isEqualTo(receiptCount * (receiptCount + 1L) / 2);
                assertThat(totals.getInt(3)).isEqualTo(receiptCount / 2);
            }
            connection.commit();
        }
    }

    @Test
    void mixedStatementBatchAdjustsStockLevels() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.addBatch("INSERT INTO stock (sku, on_hand) VALUES ('SKU-2001', 10), ('SKU-2002', 5), ('SKU-2003', 0)");
                statement.addBatch("UPDATE stock SET on_hand = on_hand + 3 WHERE sku IN ('SKU-2001', 'SKU-2002')");
                statement.addBatch("DELETE FROM stock WHERE on_hand = 0");
                statement.addBatch("UPDATE stock SET on_hand = 99 WHERE sku = 'SKU-MISSING'");
                assertThat(statement.executeBatch()).containsExactly(3, 2, 1, 0);
            }

            try (PreparedStatement restock = connection.prepareStatement(
                    "UPDATE stock SET on_hand = on_hand + ? WHERE sku = ?")) {
                restock.setInt(1, 7);
                restock.setString(2, "SKU-2001");
                restock.addBatch();
                restock.setInt(1, 1);
                restock.setString(2, "SKU-2003");
                restock.addBatch();
                assertThat(restock.executeLargeBatch()).containsExactly(1L, 0L);
            }

            try (Statement statement = connection.createStatement();
                    ResultSet levels = statement.executeQuery("SELECT sku, on_hand FROM stock ORDER BY sku")) {
                List<String> rows = new ArrayList<>();
                while (levels.next()) {
                    rows.add(levels.getString("sku") + "=" + levels.getInt("on_hand"));
                }
                assertThat(rows).containsExactly("SKU-2001=20", "SKU-2002=8");
            }
        }
    }

    @Test
    void duplicateSkuFailsBatchAndSavepointKeepsEarlierReceipts() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(INSERT_RECEIPT)) {
                bind(insert, new Receipt("SKU-3001", 5, "dock-a"));
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
            Savepoint beforeBatch = connection.setSavepoint("before_batch");

            Throwable thrown;
            try (PreparedStatement insert = connection.prepareStatement(INSERT_RECEIPT)) {
                bind(insert, new Receipt("SKU-3002", 9, "dock-b"));
                insert.addBatch();
                bind(insert, new Receipt("SKU-3001", 4, "dock-b"));
                insert.addBatch();
                bind(insert, new Receipt("SKU-3003", 2, "dock-b"));
                insert.addBatch();
                thrown = catchThrowable(insert::executeBatch);
            }
            assertThat(thrown).isInstanceOf(BatchUpdateException.class);
            BatchUpdateException failure = (BatchUpdateException) thrown;
            assertThat(failure.getSQLState()).isEqualTo(PSQLState.UNIQUE_VIOLATION.getState());
            // The transaction is aborted, so not even the row before the duplicate counts as done.
            assertThat(failure.getUpdateCounts()).hasSize(3).containsOnly(Statement.EXECUTE_FAILED);

            Throwable cause = failure.getNextException();
            assertThat(cause).isInstanceOf(PSQLException.class);
            ServerErrorMessage serverError = ((PSQLException) cause).getServerErrorMessage();
            assertThat(serverError).isNotNull();
            assertThat(serverError.getSQLState()).isEqualTo("23505");
            assertThat(serverError.getTable()).isEqualTo("receipts");
            assertThat(serverError.getConstraint()).isEqualTo("receipts_sku_key");
            assertThat(serverError.getDetail()).contains("(sku)=(SKU-3001)");

            connection.rollback(beforeBatch);
            try (PreparedStatement insert = connection.prepareStatement(INSERT_RECEIPT)) {
                bind(insert, new Receipt("SKU-3002", 9, "dock-b"));
                insert.addBatch();
                bind(insert, new Receipt("SKU-3003", 2, "dock-b"));
                insert.addBatch();
                assertThat(insert.executeBatch()).containsExactly(1, 1);
            }
            connection.commit();

            try (Statement statement = connection.createStatement();
                    ResultSet skus = statement.executeQuery("SELECT sku, quantity FROM receipts ORDER BY sku")) {
                List<String> rows = new ArrayList<>();
                while (skus.next()) {
                    rows.add(skus.getString("sku") + ":" + skus.getInt("quantity"));
                }
                assertThat(rows).containsExactly("SKU-3001:5", "SKU-3002:9", "SKU-3003:2");
            }
            connection.commit();
        }
    }

    private static void bind(PreparedStatement insert, Receipt receipt) throws SQLException {
        insert.setString(1, receipt.sku());
        insert.setInt(2, receipt.quantity());
        insert.setString(3, receipt.dock());
    }

    private static PGSimpleDataSource newDataSource(int port) {
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

    /// One goods receipt booked at a loading dock.
    private record Receipt(String sku, int quantity, String dock) {
    }
}
