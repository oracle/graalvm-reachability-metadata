/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mariadb_jdbc.mariadb_java_client;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mariadb.jdbc.MariaDbDataSource;
import org.mariadb.jdbc.MariaDbPoolDataSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Connection pooling of MariaDB connections: HikariCP built over `MariaDbDataSource`
/// by class name, and the driver's own `MariaDbPoolDataSource`, covering physical
/// connection reuse, session state reset on return, and eviction of a killed connection.
///
/// The scenario is a web session store whose request handlers borrow a pooled
/// connection per request, the way a servlet application talks to its database.
public class PooledSessionStoreTest {

    private static final String IMAGE = "mariadb:13.0";

    private static final String CONTAINER = "real-use-mariadb-pool-" + UUID.randomUUID();

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(120);

    private static final String DATABASE = "sessions";

    private static final String REPORTING = "reporting";

    private static final String USER = "root";

    private static String serverUrl;

    private static MariaDbDataSource admin;

    @BeforeAll
    static void startServer() throws Exception {
        docker("run", "-d", "--name", CONTAINER, "-p", "127.0.0.1::3306",
                "-e", "MARIADB_ALLOW_EMPTY_ROOT_PASSWORD=true",
                IMAGE);
        String mapping = docker("port", CONTAINER, "3306/tcp").lines().findFirst().orElseThrow();
        int port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));
        serverUrl = "jdbc:mariadb://127.0.0.1:" + port + "/";

        admin = new MariaDbDataSource(serverUrl);
        admin.setUser(USER);
        awaitServer(admin);
        try (Connection connection = admin.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + DATABASE);
            statement.execute("CREATE DATABASE " + REPORTING);
            statement.execute("""
                    CREATE TABLE sessions.web_sessions (
                        token VARCHAR(64) PRIMARY KEY,
                        username VARCHAR(40) NOT NULL,
                        hits INT NOT NULL DEFAULT 0
                    ) ENGINE=InnoDB""");
        }
    }

    @AfterAll
    static void stopServer() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @Test
    void hikariReusesOnePhysicalConnectionAcrossRequests() throws Exception {
        try (HikariDataSource pool = hikari("session-reuse")) {
            SessionStore store = new SessionStore(pool);
            HikariPoolMXBean stats = pool.getHikariPoolMXBean();

            long firstServerThread;
            try (Connection connection = pool.getConnection()) {
                assertThat(connection.getMetaData().getDriverName()).contains("MariaDB");
                assertThat(stats.getActiveConnections()).isEqualTo(1);
                firstServerThread = serverThreadId(connection);
            }
            assertThat(stats.getActiveConnections()).isZero();

            store.login("reuse-token", "ada");
            store.touch("reuse-token");
            store.touch("reuse-token");

            try (Connection connection = pool.getConnection()) {
                assertThat(serverThreadId(connection)).isEqualTo(firstServerThread);
            }
            assertThat(store.hits("reuse-token")).isEqualTo(2);
            assertThat(store.owner("reuse-token")).isEqualTo("ada");
        }
    }

    @Test
    void hikariRestoresSessionStateChangedByARequest() throws Exception {
        try (HikariDataSource pool = hikari("session-reset")) {
            long serverThread;
            try (Connection connection = pool.getConnection()) {
                serverThread = serverThreadId(connection);
                connection.setAutoCommit(false);
                connection.setReadOnly(true);
                connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                connection.setCatalog(REPORTING);
                assertThat(currentDatabase(connection)).isEqualTo(REPORTING);
                connection.commit();
            }

            try (Connection connection = pool.getConnection()) {
                assertThat(serverThreadId(connection)).isEqualTo(serverThread);
                assertThat(connection.getAutoCommit()).isTrue();
                assertThat(connection.isReadOnly()).isFalse();
                assertThat(connection.getTransactionIsolation())
                        .isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);
                assertThat(connection.getCatalog()).isEqualTo(DATABASE);
                assertThat(currentDatabase(connection)).isEqualTo(DATABASE);
            }
        }
    }

    @Test
    void hikariEvictsAConnectionTheServerKilled() throws Exception {
        try (HikariDataSource pool = hikari("session-evict")) {
            SessionStore store = new SessionStore(pool);
            store.login("evict-token", "grace");

            long killedThread;
            try (Connection connection = pool.getConnection()) {
                killedThread = serverThreadId(connection);
                try (Connection operator = admin.getConnection();
                     Statement statement = operator.createStatement()) {
                    statement.execute("KILL CONNECTION " + killedThread);
                }
                assertThatThrownBy(() -> serverThreadId(connection)).isInstanceOf(SQLException.class);
            }

            try (Connection connection = pool.getConnection()) {
                assertThat(serverThreadId(connection)).isNotEqualTo(killedThread);
            }
            store.touch("evict-token");
            assertThat(store.hits("evict-token")).isEqualTo(1);
        }
    }

    @Test
    void driverPoolResetsUserVariablesOnReuse() throws Exception {
        String url = serverUrl + DATABASE + "?user=" + USER
                + "&maxPoolSize=1&minPoolSize=1&poolName=session-driver-pool&useResetConnection=true";
        try (MariaDbPoolDataSource pool = new MariaDbPoolDataSource(url)) {
            assertThat(pool.getPoolName()).startsWith("session-driver-pool-");
            SessionStore store = new SessionStore(pool);

            long serverThread;
            try (Connection connection = pool.getConnection();
                 Statement statement = connection.createStatement()) {
                serverThread = serverThreadId(connection);
                statement.execute("SET @current_user = 'linus'");
                assertThat(userVariable(connection)).isEqualTo("linus");
            }

            try (Connection connection = pool.getConnection()) {
                assertThat(serverThreadId(connection)).isEqualTo(serverThread);
                assertThat(userVariable(connection)).isNull();
            }

            store.login("driver-token", "linus");
            store.touch("driver-token");
            assertThat(store.owner("driver-token")).isEqualTo("linus");
            assertThat(store.hits("driver-token")).isEqualTo(1);
        }
    }

    /// Builds a single-connection HikariCP pool that instantiates the driver's
    /// `DataSource` by class name and configures it through its bean properties;
    /// the pool restores the configured catalog when a request switched it.
    private static HikariDataSource hikari(String name) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(name);
        config.setDataSourceClassName(MariaDbDataSource.class.getName());
        config.addDataSourceProperty("url", serverUrl + DATABASE);
        config.addDataSourceProperty("user", USER);
        config.setCatalog(DATABASE);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30_000);
        config.setRegisterMbeans(false);
        return new HikariDataSource(config);
    }

    private static long serverThreadId(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT CONNECTION_ID()")) {
            assertThat(rows.next()).isTrue();
            return rows.getLong(1);
        }
    }

    private static String currentDatabase(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT DATABASE()")) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }

    private static String userVariable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT @current_user")) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }

    private static void awaitServer(MariaDbDataSource dataSource) throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        SQLException last = null;
        while (System.nanoTime() < deadline) {
            try (Connection connection = dataSource.getConnection()) {
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

    /// Web session persistence that borrows a pooled connection for every request.
    private static final class SessionStore {

        private final DataSource pool;

        SessionStore(DataSource pool) {
            this.pool = pool;
        }

        void login(String token, String username) throws SQLException {
            try (Connection connection = pool.getConnection();
                 PreparedStatement insert = connection.prepareStatement(
                         "INSERT INTO web_sessions (token, username) VALUES (?, ?)")) {
                insert.setString(1, token);
                insert.setString(2, username);
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
        }

        void touch(String token) throws SQLException {
            try (Connection connection = pool.getConnection();
                 PreparedStatement update = connection.prepareStatement(
                         "UPDATE web_sessions SET hits = hits + 1 WHERE token = ?")) {
                update.setString(1, token);
                assertThat(update.executeUpdate()).isEqualTo(1);
            }
        }

        int hits(String token) throws SQLException {
            try (Connection connection = pool.getConnection();
                 PreparedStatement query = connection.prepareStatement(
                         "SELECT hits FROM web_sessions WHERE token = ?")) {
                query.setString(1, token);
                try (ResultSet rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    return rows.getInt(1);
                }
            }
        }

        String owner(String token) throws SQLException {
            try (Connection connection = pool.getConnection();
                 PreparedStatement query = connection.prepareStatement(
                         "SELECT username FROM web_sessions WHERE token = ?")) {
                query.setString(1, token);
                try (ResultSet rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    return rows.getString(1);
                }
            }
        }
    }
}
