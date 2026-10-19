/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mysql.mysql_connector_j;

import com.mysql.cj.MysqlConnection;
import com.mysql.cj.Query;
import com.mysql.cj.Session;
import com.mysql.cj.exceptions.CJException;
import com.mysql.cj.interceptors.QueryInterceptor;
import com.mysql.cj.jdbc.MysqlDataSource;
import com.mysql.cj.log.Log;
import com.mysql.cj.log.ProfilerEvent;
import com.mysql.cj.log.ProfilerEventHandler;
import com.mysql.cj.log.Slf4JLogger;
import com.mysql.cj.protocol.Resultset;
import com.mysql.cj.protocol.ServerSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Driver extension points loaded by class name: a catalog service configures `MysqlDataSource`
/// with a `QueryInterceptor` that audits every statement with its transaction state and vetoes
/// unbounded deletes, and with a `ProfilerEventHandler` fed by `profileSQL` through `Slf4JLogger`.
public class CatalogQueryAuditTest {

    private static final String IMAGE = "container-registry.oracle.com/mysql/community-server:26.7.1";
    private static final String DATABASE = "catalog";
    private static final String USER = "merchant";
    private static final String PASSWORD = "merchant-secret";
    private static final String CONTAINER = "real-use-mysql-catalog-" + UUID.randomUUID();

    private static String url;

    @BeforeAll
    static void startMysql() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        docker("run", "--rm", "-d", "--name", CONTAINER,
                "-e", "MYSQL_DATABASE=" + DATABASE,
                "-e", "MYSQL_USER=" + USER,
                "-e", "MYSQL_PASSWORD=" + PASSWORD,
                "-e", "MYSQL_RANDOM_ROOT_PASSWORD=yes",
                "-p", "127.0.0.1:" + port + ":3306",
                IMAGE);
        url = "jdbc:mysql://127.0.0.1:" + port + "/" + DATABASE;
        awaitServer(TimeUnit.MINUTES.toMillis(3));
    }

    @AfterAll
    static void stopMysql() throws Exception {
        docker("rm", "-f", CONTAINER);
    }

    @Test
    void auditsAndProfilesCatalogQueries() throws SQLException {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL(url
                + "?queryInterceptors=" + AuditInterceptor.class.getName()
                + "&profileSQL=true"
                + "&profilerEventHandler=" + RecordingProfiler.class.getName()
                + "&logger=" + Slf4JLogger.class.getName());
        dataSource.setUser(USER);
        dataSource.setPassword(PASSWORD);

        long connectionId;
        List<String> lookedUp = new ArrayList<>();
        List<String> searched = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            connectionId = connection.unwrap(MysqlConnection.class).getId();
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE product (
                            sku VARCHAR(16) PRIMARY KEY,
                            title VARCHAR(64) NOT NULL,
                            description VARCHAR(255) NOT NULL
                        ) ENGINE = InnoDB
                        """);
                assertThat(statement.executeUpdate("""
                        INSERT INTO product (sku, title, description) VALUES
                            ('LAMP-01', 'Desk lamp', 'Adjustable arm, warm light'),
                            ('MUG-02', 'Coffee mug', 'Stoneware, holds warm drinks'),
                            ('PEN-03', 'Fountain pen', 'Steel nib, blue ink')
                        """)).isEqualTo(3);
            }

            try (PreparedStatement bySku = connection.prepareStatement(
                    "SELECT title FROM product WHERE sku = ?")) {
                bySku.setString(1, "MUG-02");
                try (ResultSet rows = bySku.executeQuery()) {
                    while (rows.next()) {
                        lookedUp.add(rows.getString("title"));
                    }
                }
            }

            try (PreparedStatement byText = connection.prepareStatement(
                    "SELECT sku FROM product WHERE description LIKE ? ORDER BY sku")) {
                byText.setString(1, "%warm%");
                try (ResultSet rows = byText.executeQuery()) {
                    while (rows.next()) {
                        searched.add(rows.getString("sku"));
                    }
                }
            }

            // The interceptor refuses a delete with no WHERE clause before it reaches the server.
            try (Statement statement = connection.createStatement()) {
                assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM product"))
                        .isInstanceOf(SQLException.class)
                        .hasMessage("unbounded DELETE rejected by audit policy");
                connection.setAutoCommit(false);
                assertThat(statement.executeUpdate("DELETE FROM product WHERE sku = 'PEN-03'")).isEqualTo(1);
                connection.commit();
                connection.setAutoCommit(true);
                try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM product")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(2);
                }
            }
        }

        assertThat(lookedUp).containsExactly("Coffee mug");
        assertThat(searched).containsExactly("LAMP-01", "MUG-02");

        // The driver built one interceptor for the connection and handed it the configured logger.
        assertThat(AuditInterceptor.INITIALIZED).containsExactly(USER);
        assertThat(AuditInterceptor.LOGGERS).containsOnly(Slf4JLogger.class.getName());
        assertThat(AuditInterceptor.DESTROYED.get()).isEqualTo(1);

        List<AuditEntry> productEntries = AuditInterceptor.ENTRIES.stream()
                .filter(entry -> entry.sql().contains("product"))
                .toList();
        assertThat(productEntries).extracting(AuditEntry::sql).containsExactly(
                "CREATE TABLE product (",
                "INSERT INTO product (sku, title, description) VALUES",
                "SELECT title FROM product WHERE sku = 'MUG-02'",
                "SELECT sku FROM product WHERE description LIKE '%warm%' ORDER BY sku",
                "DELETE FROM product WHERE sku = 'PEN-03'",
                "SELECT COUNT(*) FROM product");
        assertThat(productEntries).extracting(AuditEntry::returnedRows)
                .containsExactly(false, false, true, true, false, true);
        assertThat(productEntries).extracting(AuditEntry::affectedRows)
                .containsExactly(0L, 3L, -1L, -1L, 1L, -1L);
        // Only the delete ran inside the explicit transaction.
        assertThat(productEntries).extracting(AuditEntry::autoCommit)
                .containsExactly(true, true, true, true, false, true);

        // profileSQL routed query and fetch events to the configured handler.
        assertThat(RecordingProfiler.LOGGERS).containsOnly(Slf4JLogger.class.getName());
        assertThat(RecordingProfiler.EVENTS)
                .filteredOn(event -> event.type() == ProfilerEvent.TYPE_QUERY)
                .extracting(ProfiledEvent::message)
                .anyMatch(message -> message.contains("SELECT sku FROM product WHERE description LIKE '%warm%'"));
        assertThat(RecordingProfiler.EVENTS)
                .filteredOn(event -> event.type() == ProfilerEvent.TYPE_FETCH)
                .isNotEmpty();
        assertThat(RecordingProfiler.EVENTS).allMatch(event -> event.connectionId() == connectionId);
        assertThat(RecordingProfiler.DESTROYED.get()).isEqualTo(1);
    }

    /// One statement as the audit interceptor saw it, with what the server reported back.
    record AuditEntry(String sql, boolean returnedRows, long affectedRows, boolean autoCommit) {
    }

    /// One profiler event as the recording handler received it.
    record ProfiledEvent(byte type, long connectionId, String message) {
    }

    /// Audits every top-level statement and rejects deletes that would empty a table.
    public static class AuditInterceptor implements QueryInterceptor {

        static final Queue<String> INITIALIZED = new ConcurrentLinkedQueue<>();
        static final Queue<String> LOGGERS = new ConcurrentLinkedQueue<>();
        static final Queue<AuditEntry> ENTRIES = new ConcurrentLinkedQueue<>();
        static final AtomicInteger DESTROYED = new AtomicInteger();

        @Override
        public QueryInterceptor init(MysqlConnection connection, Properties properties, Log log) {
            // The interceptor is built before the session connects, so it has no server thread id yet.
            INITIALIZED.add(connection.getUser());
            LOGGERS.add(log.getClass().getName());
            return this;
        }

        @Override
        public <T extends Resultset> T preProcess(Supplier<String> sql, Query query) {
            String statement = sql.get().strip();
            String normalized = statement.toUpperCase(Locale.ROOT);
            if (normalized.startsWith("DELETE") && !normalized.contains(" WHERE ")) {
                throw new CJException("unbounded DELETE rejected by audit policy");
            }
            return null;
        }

        @Override
        public <T extends Resultset> T postProcess(Supplier<String> sql, Query query, T result,
                                                   ServerSession session) {
            if (result == null) {
                // The driver still calls back for a statement that failed, without a result.
                return null;
            }
            String firstLine = sql.get().strip().lines().findFirst().orElse("").strip();
            long affectedRows = result.hasRows() ? -1 : result.getUpdateCount();
            ENTRIES.add(new AuditEntry(firstLine, result.hasRows(), affectedRows, session.isAutoCommit()));
            return null;
        }

        @Override
        public boolean executeTopLevelOnly() {
            return true;
        }

        @Override
        public void destroy() {
            DESTROYED.incrementAndGet();
        }
    }

    /// Collects the events `profileSQL` emits instead of writing them to the log.
    public static class RecordingProfiler implements ProfilerEventHandler {

        static final Queue<String> LOGGERS = new ConcurrentLinkedQueue<>();
        static final Queue<ProfiledEvent> EVENTS = new ConcurrentLinkedQueue<>();
        static final AtomicInteger DESTROYED = new AtomicInteger();

        @Override
        public void init(Log log) {
            LOGGERS.add(log.getClass().getName());
        }

        @Override
        public void destroy() {
            DESTROYED.incrementAndGet();
        }

        @Override
        public void consumeEvent(ProfilerEvent event) {
            EVENTS.add(new ProfiledEvent(event.getEventType(), event.getConnectionId(), event.getMessage()));
        }

        @Override
        public void processEvent(byte eventType, Session session, Query query, Resultset resultSet,
                                 long eventDuration, Throwable eventCreationPoint, String message) {
            EVENTS.add(new ProfiledEvent(eventType, session.getThreadId(), message));
        }
    }

    /// Polls with a bounded deadline until the server accepts the application user.
    private static void awaitServer(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        SQLException lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection connection = DriverManager.getConnection(url, USER, PASSWORD);
                 Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
                return;
            } catch (SQLException e) {
                lastFailure = e;
                Thread.sleep(500);
            }
        }
        throw new IllegalStateException("MySQL did not become reachable", lastFailure);
    }

    private static void docker(String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).inheritIO().start();
        assertThat(process.waitFor(5, TimeUnit.MINUTES)).as("docker %s finished", args[0]).isTrue();
        assertThat(process.exitValue()).as("docker %s exit code", args[0]).isZero();
    }
}
