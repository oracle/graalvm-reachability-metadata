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

import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// High-availability connection URLs: `sequential` failover past an unreachable host and
/// after a killed session, `transactionReplay` of an interrupted transaction, the lost
/// transaction reported without replay, and `replication` routing of read-only work.
///
/// The scenario is an order service talking to a primary MariaDB server and a standby
/// that doubles as its read replica. The two servers are independent containers, so
/// each holds its own copy of the `orders` table and the rows a query returns tell
/// which server the driver routed it to.
///
/// The driver remembers a failed host address JVM-wide for `deniedListTimeout`, so
/// every test that makes the primary fail reaches it through a port no other test uses.
public class OrderServiceFailoverTest {

    private static final String IMAGE = "mariadb:13.0";

    private static final String RUN = UUID.randomUUID().toString();

    private static final String PRIMARY = "real-use-mariadb-primary-" + RUN;

    private static final String STANDBY = "real-use-mariadb-standby-" + RUN;

    private static final String PRIMARY_HOSTNAME = "orders-primary";

    private static final String STANDBY_HOSTNAME = "orders-standby";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(120);

    private static final String DATABASE = "shop";

    private static final String USER = "orders";

    private static final String PASSWORD = "orders-secret";

    private static List<Integer> primaryPorts;

    private static int standbyPort;

    @BeforeAll
    static void startServers() throws Exception {
        primaryPorts = List.of(unusedPort(), unusedPort(), unusedPort());
        standbyPort = unusedPort();
        startServer(PRIMARY, PRIMARY_HOSTNAME, primaryPorts);
        startServer(STANDBY, STANDBY_HOSTNAME, List.of(standbyPort));

        awaitServer(primaryPorts.get(0));
        awaitServer(standbyPort);
        createOrders(primaryPorts.get(0), "primary-seed");
        createOrders(standbyPort, "replica-snapshot");
    }

    @AfterAll
    static void stopServers() throws Exception {
        docker("rm", "-f", "-v", PRIMARY);
        docker("rm", "-f", "-v", STANDBY);
    }

    @Test
    void sequentialUrlSkipsAnUnreachableHost() throws Exception {
        OrderService service = new OrderService(
                "jdbc:mariadb:sequential://" + address(unusedPort()) + "," + address(standbyPort)
                        + "/" + DATABASE + "?connectTimeout=2000");

        try (Connection connection = service.open()) {
            assertThat(OrderService.serverName(connection)).isEqualTo(STANDBY_HOSTNAME);
            assertThat(OrderService.references(connection)).contains("replica-snapshot");
        }
    }

    @Test
    void replaysAnInterruptedTransactionOnTheStandby() throws Exception {
        OrderService service = new OrderService(
                "jdbc:mariadb:sequential://" + address(primaryPorts.get(1)) + "," + address(standbyPort)
                        + "/" + DATABASE + "?transactionReplay=true");

        try (Connection connection = service.open()) {
            assertThat(OrderService.serverName(connection)).isEqualTo(PRIMARY_HOSTNAME);
            long firstSession = connection.unwrap(org.mariadb.jdbc.Connection.class).getThreadId();

            connection.setAutoCommit(false);
            OrderService.placeOrder(connection, "replayed-1");
            killSession(primaryPorts.get(0), firstSession);
            OrderService.placeOrder(connection, "replayed-2");
            connection.commit();

            assertThat(OrderService.serverName(connection)).isEqualTo(STANDBY_HOSTNAME);
            assertThat(OrderService.references(connection)).contains("replayed-1", "replayed-2");
        }
        try (Connection primary = directConnection(primaryPorts.get(0))) {
            assertThat(OrderService.references(primary)).doesNotContain("replayed-1", "replayed-2");
        }
    }

    @Test
    void reportsTheLostTransactionWhenReplayIsOff() throws Exception {
        OrderService service = new OrderService(
                "jdbc:mariadb:sequential://" + address(primaryPorts.get(2)) + "," + address(standbyPort)
                        + "/" + DATABASE);

        try (Connection connection = service.open()) {
            assertThat(OrderService.serverName(connection)).isEqualTo(PRIMARY_HOSTNAME);
            long firstSession = connection.unwrap(org.mariadb.jdbc.Connection.class).getThreadId();

            connection.setAutoCommit(false);
            OrderService.placeOrder(connection, "lost-1");
            killSession(primaryPorts.get(0), firstSession);

            assertThatThrownBy(() -> OrderService.placeOrder(connection, "lost-2"))
                    .isInstanceOf(SQLTransientConnectionException.class)
                    .hasMessageContaining("In progress transaction was lost")
                    .extracting(e -> ((SQLException) e).getSQLState())
                    .isEqualTo("25S03");

            connection.rollback();
            connection.setAutoCommit(true);
            assertThat(OrderService.serverName(connection)).isEqualTo(STANDBY_HOSTNAME);
            assertThat(OrderService.references(connection)).doesNotContain("lost-1", "lost-2");
        }
        try (Connection primary = directConnection(primaryPorts.get(0))) {
            assertThat(OrderService.references(primary)).doesNotContain("lost-1", "lost-2");
        }
    }

    @Test
    void replicationUrlRoutesReadOnlyWorkToTheReplica() throws Exception {
        OrderService service = new OrderService(
                "jdbc:mariadb:replication://" + address(primaryPorts.get(0)) + "," + address(standbyPort)
                        + "/" + DATABASE);

        try (Connection connection = service.open()) {
            assertThat(connection.isReadOnly()).isFalse();
            assertThat(OrderService.serverName(connection)).isEqualTo(PRIMARY_HOSTNAME);
            OrderService.placeOrder(connection, "routed-write");

            connection.setReadOnly(true);
            assertThat(connection.isReadOnly()).isTrue();
            assertThat(OrderService.serverName(connection)).isEqualTo(STANDBY_HOSTNAME);
            assertThat(OrderService.references(connection))
                    .contains("replica-snapshot")
                    .doesNotContain("routed-write");

            connection.setReadOnly(false);
            assertThat(OrderService.serverName(connection)).isEqualTo(PRIMARY_HOSTNAME);
            assertThat(OrderService.references(connection)).contains("primary-seed", "routed-write");
        }
    }

    private static void startServer(String container, String hostname, List<Integer> ports) throws Exception {
        List<String> command = new ArrayList<>(List.of("run", "-d", "--name", container, "--hostname", hostname));
        for (int port : ports) {
            command.addAll(List.of("-p", "127.0.0.1:" + port + ":3306"));
        }
        command.addAll(List.of(
                "-e", "MARIADB_DATABASE=" + DATABASE,
                "-e", "MARIADB_USER=" + USER,
                "-e", "MARIADB_PASSWORD=" + PASSWORD,
                "-e", "MARIADB_ROOT_PASSWORD=" + PASSWORD,
                IMAGE));
        docker(command.toArray(String[]::new));
    }

    private static String address(int port) {
        return "127.0.0.1:" + port;
    }

    private static int unusedPort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static Connection directConnection(int port) throws SQLException {
        return DriverManager.getConnection("jdbc:mariadb://" + address(port) + "/" + DATABASE, USER, PASSWORD);
    }

    private static void createOrders(int port, String seedReference) throws SQLException {
        try (Connection connection = directConnection(port);
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE orders (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        reference VARCHAR(64) NOT NULL UNIQUE
                    ) ENGINE=InnoDB""");
            OrderService.placeOrder(connection, seedReference);
        }
    }

    /// Ends a session from an operator console the way `KILL` does when a server is drained.
    private static void killSession(int port, long threadId) throws SQLException {
        try (Connection admin = DriverManager.getConnection(
                "jdbc:mariadb://" + address(port) + "/", "root", PASSWORD);
                Statement statement = admin.createStatement()) {
            statement.execute("KILL CONNECTION " + threadId);
        }
    }

    private static void awaitServer(int port) throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        SQLException last = null;
        while (System.nanoTime() < deadline) {
            try (Connection connection = directConnection(port)) {
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

    /// Order service data access configured with one high-availability connection URL.
    static final class OrderService {

        private final String url;

        OrderService(String url) {
            this.url = url;
        }

        Connection open() throws SQLException {
            return DriverManager.getConnection(url, USER, PASSWORD);
        }

        /// The server that answered, as the operations dashboard shows it.
        static String serverName(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery("SELECT @@hostname")) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }

        static void placeOrder(Connection connection, String reference) throws SQLException {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO orders (reference) VALUES (?)")) {
                insert.setString(1, reference);
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
        }

        static List<String> references(Connection connection) throws SQLException {
            List<String> references = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("SELECT reference FROM orders ORDER BY id")) {
                while (rows.next()) {
                    references.add(rows.getString(1));
                }
            }
            return references;
        }
    }
}
