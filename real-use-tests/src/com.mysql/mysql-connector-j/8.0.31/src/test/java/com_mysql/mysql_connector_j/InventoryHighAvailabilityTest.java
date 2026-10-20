/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mysql.mysql_connector_j;

import com.mysql.cj.jdbc.ConnectionGroupManager;
import com.mysql.cj.jdbc.ha.LoadBalancedConnection;
import com.mysql.cj.jdbc.ha.ReplicationConnection;
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
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// High-availability connection URLs: an inventory service reaches a source and a replica server
/// through `jdbc:mysql:replication://`, fails over past a dead primary with a multi-host URL, and
/// balances with `jdbc:mysql:loadbalance://` under server affinity and a managed connection group.
public class InventoryHighAvailabilityTest {

    private static final String IMAGE = "container-registry.oracle.com/mysql/community-server:26.7.1";
    private static final String DATABASE = "inventory";
    private static final String USER = "inventory";
    private static final String PASSWORD = "inventory-secret";
    private static final String RUN = UUID.randomUUID().toString();
    private static final String SOURCE_CONTAINER = "real-use-mysql-ha-source-" + RUN;
    private static final String REPLICA_CONTAINER = "real-use-mysql-ha-replica-" + RUN;

    private static String sourceHost;
    private static String replicaHost;
    private static String deadHost;

    @BeforeAll
    static void startServers() throws Exception {
        int sourcePort = freePort();
        int replicaPort = freePort();
        sourceHost = "127.0.0.1:" + sourcePort;
        replicaHost = "127.0.0.1:" + replicaPort;
        deadHost = "127.0.0.1:" + freePort();
        startContainer(SOURCE_CONTAINER, sourcePort);
        startContainer(REPLICA_CONTAINER, replicaPort);
        long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(3);
        awaitServer(sourceHost, deadline);
        awaitServer(replicaHost, deadline);
        createSchema(sourceHost, "source");
        createSchema(replicaHost, "replica");
    }

    @AfterAll
    static void stopServers() throws Exception {
        docker("rm", "-f", SOURCE_CONTAINER, REPLICA_CONTAINER);
    }

    @Test
    void replicationRoutesReadsToReplicaAndWritesToSource() throws SQLException {
        String url = "jdbc:mysql:replication://" + sourceHost + "," + replicaHost + "/" + DATABASE;
        try (Connection connection = DriverManager.getConnection(url, credentials())) {
            assertThat(connection).isInstanceOf(ReplicationConnection.class);
            ReplicationConnection replication = (ReplicationConnection) connection;
            assertThat(replication.isHostSource(sourceHost)).isTrue();
            assertThat(replication.isHostReplica(replicaHost)).isTrue();

            assertThat(connection.isReadOnly()).isFalse();
            assertThat(nodeName(connection)).isEqualTo("source");
            recordMovement(connection, "restock widgets");

            connection.setReadOnly(true);
            assertThat(nodeName(connection)).isEqualTo("replica");
            assertThatThrownBy(() -> recordMovement(connection, "rejected on replica"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("Connection is read-only");

            connection.setReadOnly(false);
            assertThat(nodeName(connection)).isEqualTo("source");
            recordMovement(connection, "ship gadgets");
        }

        assertThat(movements(sourceHost)).contains("restock widgets", "ship gadgets")
                .doesNotContain("rejected on replica");
        assertThat(movements(replicaHost)).doesNotContain("restock widgets", "ship gadgets");
    }

    @Test
    void failoverSkipsDeadPrimaryAndOpensSecondaryReadOnly() throws SQLException {
        String url = "jdbc:mysql://" + deadHost + "," + replicaHost + "/" + DATABASE + "?connectTimeout=5000";
        try (Connection connection = DriverManager.getConnection(url, credentials())) {
            assertThat(nodeName(connection)).isEqualTo("replica");
            assertThat(connection.isReadOnly()).isTrue();
            assertThatThrownBy(() -> recordMovement(connection, "write after failover"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("Connection is read-only");
        }

        String writableUrl = url + "&failOverReadOnly=false";
        try (Connection connection = DriverManager.getConnection(writableUrl, credentials())) {
            assertThat(nodeName(connection)).isEqualTo("replica");
            assertThat(connection.isReadOnly()).isFalse();
            recordMovement(connection, "accepted by secondary");
        }
        assertThat(movements(replicaHost)).contains("accepted by secondary");
    }

    @Test
    void loadBalanceFollowsServerAffinityAndConnectionGroup() throws SQLException {
        String group = "inventory-" + RUN;
        String url = "jdbc:mysql:loadbalance://" + deadHost + "," + replicaHost + "," + sourceHost + "/" + DATABASE
                + "?ha.loadBalanceStrategy=serverAffinity"
                + "&serverAffinityOrder=" + deadHost + "," + replicaHost + "," + sourceHost
                + "&loadBalanceConnectionGroup=" + group
                + "&connectTimeout=5000";
        try (Connection connection = DriverManager.getConnection(url, credentials())) {
            assertThat(connection).isInstanceOf(LoadBalancedConnection.class);
            assertThat(nodeName(connection)).isEqualTo("replica");

            connection.setAutoCommit(false);
            recordMovement(connection, "balanced to replica");
            connection.commit();
            assertThat(nodeName(connection)).isEqualTo("replica");
            connection.commit();

            assertThat(ConnectionGroupManager.getActiveLogicalConnectionCount(group)).isEqualTo(1);
            assertThat(ConnectionGroupManager.getActiveHostCount(group)).isEqualTo(3);

            connection.setAutoCommit(true);
            ConnectionGroupManager.removeHost(group, replicaHost, true);
            assertThat(ConnectionGroupManager.getActiveHostCount(group)).isEqualTo(2);
            assertThat(ConnectionGroupManager.getActiveHostLists(group)).doesNotContain(replicaHost);
            assertThat(nodeName(connection)).isEqualTo("source");
        }

        assertThat(movements(replicaHost)).contains("balanced to replica");
        assertThat(ConnectionGroupManager.getActiveLogicalConnectionCount(group)).isZero();
    }

    private static String nodeName(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT name FROM node")) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }

    private static void recordMovement(Connection connection, String entry) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO movement (entry) VALUES (?)")) {
            insert.setString(1, entry);
            assertThat(insert.executeUpdate()).isEqualTo(1);
        }
    }

    private static List<String> movements(String host) throws SQLException {
        List<String> entries = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl(host), credentials());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT entry FROM movement ORDER BY id")) {
            while (rows.next()) {
                entries.add(rows.getString(1));
            }
        }
        return entries;
    }

    private static void createSchema(String host, String name) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl(host), credentials());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE node (name VARCHAR(20) NOT NULL)");
            statement.execute("INSERT INTO node (name) VALUES ('" + name + "')");
            statement.execute("""
                    CREATE TABLE movement (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        entry VARCHAR(100) NOT NULL
                    )""");
        }
    }

    private static Properties credentials() {
        Properties properties = new Properties();
        properties.setProperty("user", USER);
        properties.setProperty("password", PASSWORD);
        return properties;
    }

    private static String jdbcUrl(String host) {
        return "jdbc:mysql://" + host + "/" + DATABASE;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void startContainer(String name, int port) throws IOException, InterruptedException {
        docker("run", "--rm", "-d", "--name", name,
                "-e", "MYSQL_RANDOM_ROOT_PASSWORD=yes",
                "-e", "MYSQL_DATABASE=" + DATABASE,
                "-e", "MYSQL_USER=" + USER,
                "-e", "MYSQL_PASSWORD=" + PASSWORD,
                "-p", "127.0.0.1:" + port + ":3306",
                IMAGE);
    }

    private static void awaitServer(String host, long deadline) throws InterruptedException {
        SQLException lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection connection = DriverManager.getConnection(jdbcUrl(host), credentials());
                 Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
                return;
            } catch (SQLException e) {
                lastFailure = e;
                Thread.sleep(500);
            }
        }
        throw new IllegalStateException("MySQL at " + host + " did not become reachable", lastFailure);
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
