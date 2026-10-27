/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mysql.mysql_connector_j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/// Connection security: a payroll service connects with each `sslMode` (DISABLED, REQUIRED,
/// VERIFY_CA against the server's CA in a PKCS12 trust store, VERIFY_IDENTITY) and a pinned TLS
/// version, then signs its users in through `caching_sha2_password` RSA key exchange and fast
/// authentication, and walks an expired account through the sandbox-mode password reset.
public class SecureConnectionTest {

    private static final String IMAGE = "container-registry.oracle.com/mysql/community-server:26.7.1";
    private static final String ROOT_PASSWORD = "root-secret";
    private static final String TRUST_STORE_PASSWORD = "trust-secret";
    private static final String CONTAINER = "real-use-mysql-tls-" + UUID.randomUUID();

    @TempDir
    static Path workDir;

    private static String url;
    private static Path trustStore;
    private static Path serverPublicKey;

    @BeforeAll
    static void startMysql() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        docker("run", "--rm", "-d", "--name", CONTAINER,
                "-e", "MYSQL_ROOT_PASSWORD=" + ROOT_PASSWORD,
                "-e", "MYSQL_ROOT_HOST=%",
                "-p", "127.0.0.1:" + port + ":3306",
                IMAGE);
        url = "jdbc:mysql://127.0.0.1:" + port + "/";
        awaitServer(TimeUnit.MINUTES.toMillis(3));

        Path caPem = workDir.resolve("ca.pem");
        serverPublicKey = workDir.resolve("public_key.pem");
        docker("cp", CONTAINER + ":/var/lib/mysql/ca.pem", caPem.toString());
        docker("cp", CONTAINER + ":/var/lib/mysql/public_key.pem", serverPublicKey.toString());
        trustStore = writeTrustStore(caPem, workDir.resolve("truststore.p12"));

        try (Connection connection = connect("root", ROOT_PASSWORD, Map.of());
             Statement statement = connection.createStatement()) {
            for (String user : List.of("clerk", "reporter", "keyholder")) {
                statement.execute("CREATE USER '" + user + "'@'%' IDENTIFIED WITH caching_sha2_password BY '"
                        + user + "-pw'");
            }
            statement.execute("CREATE USER 'auditor'@'%' IDENTIFIED BY 'auditor-old' PASSWORD EXPIRE");
        }
    }

    @AfterAll
    static void stopMysql() throws Exception {
        docker("rm", "-f", CONTAINER);
    }

    @Test
    void negotiatesTlsPerSslMode() throws SQLException {
        try (Connection connection = connect("clerk", "clerk-pw", Map.of("sslMode", "REQUIRED"))) {
            assertThat(sessionStatus(connection, "Ssl_version")).startsWith("TLSv1.");
            assertThat(sessionStatus(connection, "Ssl_cipher")).isNotEmpty();
            assertThat(scalar(connection, "SELECT CURRENT_USER()")).isEqualTo("clerk@%");
        }

        try (Connection connection = connect("clerk", "clerk-pw",
                Map.of("sslMode", "REQUIRED", "tlsVersions", "TLSv1.2"))) {
            assertThat(sessionStatus(connection, "Ssl_version")).isEqualTo("TLSv1.2");
        }

        try (Connection connection = connect("clerk", "clerk-pw", Map.of("sslMode", "DISABLED"))) {
            assertThat(sessionStatus(connection, "Ssl_cipher")).isEmpty();
        }

        Map<String, String> verifyCa = Map.of(
                "sslMode", "VERIFY_CA",
                "trustCertificateKeyStoreUrl", trustStore.toUri().toString(),
                "trustCertificateKeyStoreType", "PKCS12",
                "trustCertificateKeyStorePassword", TRUST_STORE_PASSWORD);
        try (Connection connection = connect("clerk", "clerk-pw", verifyCa)) {
            assertThat(sessionStatus(connection, "Ssl_cipher")).isNotEmpty();
        }

        Map<String, String> verifyIdentity = new HashMap<>(verifyCa);
        verifyIdentity.put("sslMode", "VERIFY_IDENTITY");
        SQLException rejected = rejectedLogin("clerk", "clerk-pw", verifyIdentity);
        assertThat(causeMessages(rejected)).anySatisfy(message ->
                assertThat(message).contains("Server identity verification failed"));
    }

    @Test
    void authenticatesCachingSha2UsersWithoutTls() throws SQLException {
        Map<String, String> plain = Map.of("sslMode", "DISABLED");
        SQLException noKey = rejectedLogin("reporter", "reporter-pw", plain);
        assertThat(noKey.getMessage()).contains("Public Key Retrieval is not allowed");

        try (Connection connection = connect("reporter", "reporter-pw",
                Map.of("sslMode", "DISABLED", "allowPublicKeyRetrieval", "true"))) {
            assertThat(scalar(connection, "SELECT CURRENT_USER()")).isEqualTo("reporter@%");
        }
        try (Connection connection = connect("reporter", "reporter-pw", plain)) {
            assertThat(scalar(connection, "SELECT CURRENT_USER()")).isEqualTo("reporter@%");
        }

        try (Connection connection = connect("keyholder", "keyholder-pw",
                Map.of("sslMode", "DISABLED", "serverRSAPublicKeyFile", serverPublicKey.toString()))) {
            assertThat(scalar(connection, "SELECT CURRENT_USER()")).isEqualTo("keyholder@%");
        }

        SQLException wrongPassword = rejectedLogin("keyholder", "not-the-password",
                Map.of("sslMode", "REQUIRED"));
        assertThat(wrongPassword.getErrorCode()).isEqualTo(1045);
        assertThat(wrongPassword.getSQLState()).isEqualTo("28000");
    }

    @Test
    void resetsAnExpiredPasswordInSandboxMode() throws SQLException {
        SQLException expired = rejectedLogin("auditor", "auditor-old", Map.of("sslMode", "REQUIRED"));
        assertThat(expired.getErrorCode()).isEqualTo(1862);

        try (Connection sandbox = connect("auditor", "auditor-old",
                Map.of("sslMode", "REQUIRED", "disconnectOnExpiredPasswords", "false",
                        "useLocalSessionState", "true"));
             Statement statement = sandbox.createStatement()) {
            SQLException blocked = catchThrowableOfType(
                    () -> statement.executeQuery("SELECT 1").close(), SQLException.class);
            assertThat(blocked.getErrorCode()).isEqualTo(1820);
            statement.execute("ALTER USER USER() IDENTIFIED BY 'auditor-new'");
        }

        try (Connection connection = connect("auditor", "auditor-new", Map.of("sslMode", "REQUIRED"))) {
            assertThat(scalar(connection, "SELECT CURRENT_USER()")).isEqualTo("auditor@%");
        }
    }

    private static Connection connect(String user, String password, Map<String, String> options)
            throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", password);
        properties.putAll(options);
        return DriverManager.getConnection(url, properties);
    }

    private static SQLException rejectedLogin(String user, String password, Map<String, String> options) {
        SQLException failure = catchThrowableOfType(() -> connect(user, password, options).close(),
                SQLException.class);
        assertThat((Throwable) failure).as("login of %s rejected", user).isNotNull();
        return failure;
    }

    private static String sessionStatus(Connection connection, String variable) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SHOW SESSION STATUS LIKE '" + variable + "'")) {
            assertThat(rows.next()).as("status variable %s", variable).isTrue();
            return rows.getString("Value");
        }
    }

    private static String scalar(Connection connection, String query) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(query)) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }

    private static List<String> causeMessages(Throwable failure) {
        List<String> messages = new ArrayList<>();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            messages.add(String.valueOf(cause.getMessage()));
        }
        return messages;
    }

    private static Path writeTrustStore(Path caPem, Path target) throws Exception {
        Certificate ca;
        try (InputStream in = Files.newInputStream(caPem)) {
            ca = CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setCertificateEntry("mysql-ca", ca);
        try (OutputStream out = Files.newOutputStream(target)) {
            store.store(out, TRUST_STORE_PASSWORD.toCharArray());
        }
        return target;
    }

    private static void awaitServer(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        SQLException lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection connection = connect("root", ROOT_PASSWORD, Map.of());
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
