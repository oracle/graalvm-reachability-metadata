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
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Authentication plugins and encrypted transport: logins through the driver's
/// `mysql_native_password` and `client_ed25519` plugins, TLS negotiated with
/// `sslMode`, `restrictedAuth` allow-lists, and credentials supplied by the
/// `PROPERTY` credential plugin.
///
/// The scenario is a ledger service whose operators sign in with per-account
/// credentials, the way a back office application connects for each role.
public class SecureLoginTest {

    private static final String IMAGE = "mariadb:13.0";

    private static final String CONTAINER = "real-use-mariadb-auth-" + UUID.randomUUID();

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(120);

    private static final String DATABASE = "ledger";

    private static final String ROOT_PASSWORD = "root-Secret-42";

    private static final String NATIVE_USER = "clerk";

    private static final String ED25519_USER = "auditor";

    private static final String TLS_ONLY_USER = "remote_clerk";

    private static final String PASSWORD = "Ledger-Pass-7";

    private static String serverUrl;

    @BeforeAll
    static void startServer() throws Exception {
        docker("run", "-d", "--name", CONTAINER, "-p", "127.0.0.1::3306",
                "-e", "MARIADB_ROOT_PASSWORD=" + ROOT_PASSWORD,
                IMAGE);
        String mapping = docker("port", CONTAINER, "3306/tcp").lines().findFirst().orElseThrow();
        int port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));
        serverUrl = "jdbc:mariadb://127.0.0.1:" + port + "/";

        MariaDbDataSource admin = new MariaDbDataSource(serverUrl);
        admin.setUser("root");
        admin.setPassword(ROOT_PASSWORD);
        awaitServer(admin);
        try (Connection connection = admin.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSTALL SONAME 'auth_ed25519'");
            statement.execute("CREATE DATABASE " + DATABASE);
            statement.execute("""
                    CREATE TABLE ledger.entries (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        account VARCHAR(20) NOT NULL,
                        amount DECIMAL(10, 2) NOT NULL,
                        posted_by VARCHAR(40) NOT NULL
                    ) ENGINE=InnoDB""");
            statement.execute("CREATE USER " + NATIVE_USER
                    + "@'%' IDENTIFIED VIA mysql_native_password USING PASSWORD('" + PASSWORD + "')");
            statement.execute("CREATE USER " + ED25519_USER
                    + "@'%' IDENTIFIED VIA ed25519 USING PASSWORD('" + PASSWORD + "')");
            statement.execute("CREATE USER " + TLS_ONLY_USER
                    + "@'%' IDENTIFIED BY '" + PASSWORD + "' REQUIRE SSL");
            for (String user : List.of(NATIVE_USER, ED25519_USER, TLS_ONLY_USER)) {
                statement.execute("GRANT SELECT, INSERT ON ledger.* TO " + user + "@'%'");
            }
        }
    }

    @AfterAll
    static void stopServer() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @Test
    void everyAuthenticationPluginSignsAnOperatorIn() throws Exception {
        for (String user : List.of(NATIVE_USER, ED25519_USER)) {
            String url = serverUrl + DATABASE + "?user=" + user + "&password=" + PASSWORD;
            try (Connection connection = DriverManager.getConnection(url)) {
                assertThat(currentUser(connection)).isEqualTo(user + "@%");
                Ledger ledger = new Ledger(connection);
                ledger.post("cash-" + user, "125.50");
                assertThat(ledger.postedBy("cash-" + user)).isEqualTo(user);
            }
        }
    }

    @Test
    void wrongPasswordIsRejectedWithAnAccessDeniedError() {
        for (String user : List.of(NATIVE_USER, ED25519_USER)) {
            MariaDbDataSource dataSource = dataSource(user, "not-the-password", "");
            assertThatThrownBy(dataSource::getConnection)
                    .isInstanceOfSatisfying(SQLException.class, e -> {
                        assertThat(e.getErrorCode()).isEqualTo(1045);
                        assertThat(e.getSQLState()).isEqualTo("28000");
                        assertThat(e.getMessage()).contains("Access denied");
                    });
        }
    }

    @Test
    void restrictedAuthRefusesAPluginOutsideTheAllowList() throws Exception {
        MariaDbDataSource nativeOnly = dataSource(ED25519_USER, PASSWORD, "&restrictedAuth=mysql_native_password");
        assertThatThrownBy(nativeOnly::getConnection)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("client_ed25519");

        MariaDbDataSource ed25519Allowed = dataSource(ED25519_USER, PASSWORD,
                "&restrictedAuth=mysql_native_password,client_ed25519");
        try (Connection connection = ed25519Allowed.getConnection()) {
            assertThat(currentUser(connection)).isEqualTo(ED25519_USER + "@%");
        }
    }

    @Test
    void tlsIsNegotiatedWhenTheAccountRequiresIt() throws Exception {
        MariaDbDataSource plain = dataSource(TLS_ONLY_USER, PASSWORD, "&sslMode=disable");
        assertThatThrownBy(plain::getConnection)
                .isInstanceOfSatisfying(SQLException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(1045));

        MariaDbDataSource encrypted = dataSource(TLS_ONLY_USER, PASSWORD, "&sslMode=trust");
        try (Connection connection = encrypted.getConnection()) {
            assertThat(sessionStatus(connection, "Ssl_version")).startsWith("TLSv1.");
            assertThat(sessionStatus(connection, "Ssl_cipher")).isNotEmpty();
            Ledger ledger = new Ledger(connection);
            ledger.post("wire-transfer", "980.00");
            assertThat(ledger.postedBy("wire-transfer")).isEqualTo(TLS_ONLY_USER);
        }

        try (Connection connection = dataSource(NATIVE_USER, PASSWORD, "&sslMode=disable").getConnection()) {
            assertThat(sessionStatus(connection, "Ssl_version")).isEmpty();
        }
    }

    @Test
    void credentialsComeFromSystemPropertiesThroughTheCredentialPlugin() throws Exception {
        String url = serverUrl + DATABASE + "?credentialType=PROPERTY"
                + "&userKey=ledger.db.user&pwdKey=ledger.db.password";
        System.setProperty("ledger.db.user", ED25519_USER);
        System.setProperty("ledger.db.password", PASSWORD);
        try (Connection connection = DriverManager.getConnection(url)) {
            assertThat(currentUser(connection)).isEqualTo(ED25519_USER + "@%");
        } finally {
            System.clearProperty("ledger.db.user");
            System.clearProperty("ledger.db.password");
        }
    }

    private static MariaDbDataSource dataSource(String user, String password, String options) {
        try {
            MariaDbDataSource dataSource = new MariaDbDataSource(serverUrl + DATABASE + "?connectTimeout=10000" + options);
            dataSource.setUser(user);
            dataSource.setPassword(password);
            return dataSource;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String currentUser(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT CURRENT_USER()")) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }

    private static String sessionStatus(Connection connection, String variable) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT VARIABLE_VALUE FROM information_schema.SESSION_STATUS
                WHERE VARIABLE_NAME = ?""")) {
            query.setString(1, variable.toUpperCase());
            try (ResultSet rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getString(1);
            }
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

    /// Ledger postings recorded under the identity of the signed-in operator.
    private static final class Ledger {

        private final Connection connection;

        Ledger(Connection connection) {
            this.connection = connection;
        }

        void post(String account, String amount) throws SQLException {
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO entries (account, amount, posted_by)
                    VALUES (?, ?, SUBSTRING_INDEX(CURRENT_USER(), '@', 1))""")) {
                insert.setString(1, account);
                insert.setBigDecimal(2, new BigDecimal(amount));
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
        }

        String postedBy(String account) throws SQLException {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT posted_by FROM entries WHERE account = ?")) {
                query.setString(1, account);
                try (ResultSet rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    return rows.getString(1);
                }
            }
        }
    }
}
