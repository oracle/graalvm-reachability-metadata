/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mariadb_jdbc.mariadb_java_client;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mariadb.jdbc.MariaDbDataSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransactionRollbackException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Transaction isolation and row locking across concurrent connections: READ UNCOMMITTED,
/// READ COMMITTED and REPEATABLE READ visibility, `SELECT ... FOR UPDATE` with `SKIP LOCKED`
/// and `NOWAIT`, lock-wait timeouts that leave the transaction open, and deadlock victims
/// reported as `SQLTransactionRollbackException`.
///
/// The scenario is a seat-reservation service whose booking desks each hold their own
/// connection from `MariaDbDataSource` and compete for the same seats on a real MariaDB server.
public class SeatReservationLockingTest {

    private static final String IMAGE = "mariadb:13.0";

    private static final String CONTAINER = "real-use-mariadb-seats-" + UUID.randomUUID();

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(120);

    private static final Duration LOCK_WAIT_POLL_TIMEOUT = Duration.ofSeconds(30);

    private static final String DATABASE = "venue";

    private static final String USER = "venue_app";

    private static final String PASSWORD = "venue-secret";

    private static final int SEATS = 8;

    private static MariaDbDataSource dataSource;

    @BeforeAll
    static void startServer() throws Exception {
        docker("run", "-d", "--name", CONTAINER, "-p", "127.0.0.1::3306",
                "-e", "MARIADB_DATABASE=" + DATABASE,
                "-e", "MARIADB_USER=" + USER,
                "-e", "MARIADB_PASSWORD=" + PASSWORD,
                "-e", "MARIADB_ROOT_PASSWORD=" + PASSWORD,
                IMAGE);
        String mapping = docker("port", CONTAINER, "3306/tcp").lines().findFirst().orElseThrow();
        int port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));

        MariaDbDataSource root = new MariaDbDataSource("jdbc:mariadb://127.0.0.1:" + port + "/" + DATABASE);
        root.setUser("root");
        root.setPassword(PASSWORD);
        awaitServer(root);
        try (Connection connection = root.getConnection(); Statement statement = connection.createStatement()) {
            // The deadlock test watches INNODB_TRX, which needs PROCESS to show other sessions.
            statement.execute("GRANT PROCESS ON *.* TO '" + USER + "'@'%'");
        }

        dataSource = new MariaDbDataSource("jdbc:mariadb://127.0.0.1:" + port + "/" + DATABASE);
        dataSource.setUser(USER);
        dataSource.setPassword(PASSWORD);
        awaitServer(dataSource);

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE seats (
                        seat_no INT PRIMARY KEY,
                        holder VARCHAR(40) NULL,
                        price_cents INT NOT NULL
                    ) ENGINE=InnoDB""");
        }
    }

    @AfterAll
    static void stopServer() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @BeforeEach
    void resetSeats() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM seats");
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO seats (seat_no, holder, price_cents) VALUES (?, NULL, 5000)")) {
                for (int seat = 1; seat <= SEATS; seat++) {
                    insert.setInt(1, seat);
                    insert.addBatch();
                }
                insert.executeBatch();
            }
        }
    }

    @Test
    void desksSeeUncommittedAndCommittedPricesAccordingToTheirIsolationLevel() throws SQLException {
        try (Desk pricing = new Desk("pricing", Connection.TRANSACTION_READ_COMMITTED);
                Desk dirty = new Desk("dirty", Connection.TRANSACTION_READ_UNCOMMITTED);
                Desk committed = new Desk("committed", Connection.TRANSACTION_READ_COMMITTED);
                Desk snapshot = new Desk("snapshot", Connection.TRANSACTION_REPEATABLE_READ)) {
            assertThat(dirty.connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_READ_UNCOMMITTED);
            assertThat(snapshot.connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);
            assertThat(snapshot.sessionIsolation()).isEqualTo("REPEATABLE-READ");
            assertThat(committed.sessionIsolation()).isEqualTo("READ-COMMITTED");

            // The snapshot desk's consistent read is established by its first query.
            assertThat(snapshot.price(1)).isEqualTo(5000);

            pricing.reprice(1, 6500);
            assertThat(dirty.price(1)).isEqualTo(6500);
            assertThat(committed.price(1)).isEqualTo(5000);
            assertThat(snapshot.price(1)).isEqualTo(5000);

            pricing.commit();
            assertThat(committed.price(1)).isEqualTo(6500);
            assertThat(snapshot.price(1)).isEqualTo(5000);

            snapshot.commit();
            assertThat(snapshot.price(1)).isEqualTo(6500);
        }
    }

    @Test
    void competingDesksSkipSeatsAnotherDeskIsHolding() throws SQLException {
        try (Desk north = new Desk("north", Connection.TRANSACTION_READ_COMMITTED);
                Desk south = new Desk("south", Connection.TRANSACTION_READ_COMMITTED)) {
            assertThat(north.holdFirstFreeSeats(3)).containsExactly(1, 2, 3);
            assertThat(south.holdFirstFreeSeats(3)).containsExactly(4, 5, 6);

            assertThatThrownBy(() -> south.holdSeatWithoutWaiting(2))
                    .isInstanceOf(SQLException.class)
                    .satisfies(e -> assertThat(((SQLException) e).getErrorCode()).isEqualTo(1205))
                    .hasMessageContaining("Lock wait timeout exceeded");

            north.book(List.of(1, 2, 3));
            north.commit();
            south.rollback();
        }
        try (Desk east = new Desk("east", Connection.TRANSACTION_READ_COMMITTED)) {
            assertThat(east.holdFirstFreeSeats(SEATS)).containsExactly(4, 5, 6, 7, 8);
            east.rollback();
            assertThat(east.holder(2)).contains("north");
        }
    }

    @Test
    void timedOutLockWaitFailsOnlyTheStatementAndKeepsTheTransactionOpen() throws SQLException {
        try (Desk holder = new Desk("holder", Connection.TRANSACTION_READ_COMMITTED);
                Desk waiter = new Desk("waiter", Connection.TRANSACTION_READ_COMMITTED)) {
            holder.book(List.of(7));
            waiter.lockWaitTimeoutSeconds(1);
            waiter.book(List.of(8));

            assertThatThrownBy(() -> waiter.book(List.of(7)))
                    .isInstanceOf(SQLException.class)
                    .satisfies(e -> assertThat(((SQLException) e).getErrorCode()).isEqualTo(1205));

            assertThat(waiter.connection.isValid(5)).isTrue();
            assertThat(waiter.holder(8)).contains("waiter");
            waiter.commit();
            holder.commit();
        }
        try (Desk auditor = new Desk("auditor", Connection.TRANSACTION_READ_COMMITTED)) {
            assertThat(auditor.holder(7)).contains("holder");
            assertThat(auditor.holder(8)).contains("waiter");
        }
    }

    @Test
    void crossedBookingsDeadlockAndTheLighterTransactionIsRolledBack() throws Exception {
        ExecutorService background = Executors.newSingleThreadExecutor();
        try (Desk group = new Desk("group", Connection.TRANSACTION_REPEATABLE_READ);
                Desk single = new Desk("single", Connection.TRANSACTION_REPEATABLE_READ)) {
            group.book(List.of(1, 3, 4, 5, 6));
            single.book(List.of(2));

            long singleThread = single.connectionId();
            Future<Void> singleWantsSeatOne = background.submit(() -> {
                single.book(List.of(1));
                return null;
            });
            group.awaitLockWait(singleThread);

            // InnoDB resolves the cycle by rolling back the transaction that changed fewer rows.
            group.book(List.of(2));
            Throwable failure = awaitFailure(singleWantsSeatOne);
            assertThat(failure).isInstanceOf(SQLTransactionRollbackException.class);
            SQLTransactionRollbackException deadlock = (SQLTransactionRollbackException) failure;
            assertThat(deadlock.getErrorCode()).isEqualTo(1213);
            assertThat(deadlock.getSQLState()).isEqualTo("40001");
            assertThat(deadlock.getMessage()).contains("Deadlock");

            group.commit();
            single.rollback();
            assertThat(single.holder(2)).contains("group");
            assertThat(single.holder(1)).contains("group");
            assertThat(single.holder(7)).isEmpty();
        } finally {
            background.shutdownNow();
            assertThat(background.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static Throwable awaitFailure(Future<Void> booking) throws Exception {
        try {
            booking.get(LOCK_WAIT_POLL_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            return e.getCause();
        }
        throw new AssertionError("the crossed booking was expected to fail");
    }

    /// One booking desk: a dedicated connection with its own isolation level and open transaction.
    static final class Desk implements AutoCloseable {

        private final String name;

        private final Connection connection;

        Desk(String name, int isolation) throws SQLException {
            this.name = name;
            this.connection = dataSource.getConnection();
            connection.setTransactionIsolation(isolation);
            connection.setAutoCommit(false);
        }

        String sessionIsolation() throws SQLException {
            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("SELECT @@SESSION.transaction_isolation")) {
                assertThat(rows.next()).isTrue();
                return rows.getString(1);
            }
        }

        long connectionId() throws SQLException {
            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("SELECT CONNECTION_ID()")) {
                assertThat(rows.next()).isTrue();
                return rows.getLong(1);
            }
        }

        void lockWaitTimeoutSeconds(int seconds) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET SESSION innodb_lock_wait_timeout = " + seconds);
            }
        }

        int price(int seat) throws SQLException {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT price_cents FROM seats WHERE seat_no = ?")) {
                query.setInt(1, seat);
                try (ResultSet rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    return rows.getInt(1);
                }
            }
        }

        Optional<String> holder(int seat) throws SQLException {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT holder FROM seats WHERE seat_no = ?")) {
                query.setInt(1, seat);
                try (ResultSet rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    return Optional.ofNullable(rows.getString(1));
                }
            }
        }

        void reprice(int seat, int priceCents) throws SQLException {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE seats SET price_cents = ? WHERE seat_no = ?")) {
                update.setInt(1, priceCents);
                update.setInt(2, seat);
                assertThat(update.executeUpdate()).isEqualTo(1);
            }
        }

        List<Integer> holdFirstFreeSeats(int count) throws SQLException {
            try (PreparedStatement query = connection.prepareStatement("""
                    SELECT seat_no FROM seats
                    WHERE holder IS NULL
                    ORDER BY seat_no
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED""")) {
                query.setInt(1, count);
                List<Integer> seats = new ArrayList<>();
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        seats.add(rows.getInt(1));
                    }
                }
                return seats;
            }
        }

        void holdSeatWithoutWaiting(int seat) throws SQLException {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT seat_no FROM seats WHERE seat_no = ? FOR UPDATE NOWAIT")) {
                query.setInt(1, seat);
                try (ResultSet rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                }
            }
        }

        void book(List<Integer> seats) throws SQLException {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE seats SET holder = ? WHERE seat_no = ?")) {
                for (int seat : seats) {
                    update.setString(1, name);
                    update.setInt(2, seat);
                    assertThat(update.executeUpdate()).isEqualTo(1);
                }
            }
        }

        /// Polls until the session with the given id is blocked on a row lock.
        ///
        /// InnoDB refreshes its `INNODB_TRX` cache only after 100 ms without reads, so the
        /// poll interval stays above that or every read would see the same stale snapshot.
        void awaitLockWait(long sessionId) throws Exception {
            long deadline = System.nanoTime() + LOCK_WAIT_POLL_TIMEOUT.toNanos();
            try (PreparedStatement query = connection.prepareStatement("""
                    SELECT trx_state FROM information_schema.INNODB_TRX
                    WHERE trx_mysql_thread_id = ?""")) {
                query.setLong(1, sessionId);
                while (System.nanoTime() < deadline) {
                    try (ResultSet rows = query.executeQuery()) {
                        if (rows.next() && "LOCK WAIT".equals(rows.getString(1))) {
                            return;
                        }
                    }
                    Thread.sleep(250);
                }
            }
            throw new AssertionError("session " + sessionId + " never started waiting for a lock");
        }

        void commit() throws SQLException {
            connection.commit();
        }

        void rollback() throws SQLException {
            connection.rollback();
        }

        @Override
        public void close() throws SQLException {
            connection.rollback();
            connection.close();
        }
    }

    private static void awaitServer(MariaDbDataSource target) throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        SQLException last = null;
        while (System.nanoTime() < deadline) {
            try (Connection connection = target.getConnection()) {
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
}
