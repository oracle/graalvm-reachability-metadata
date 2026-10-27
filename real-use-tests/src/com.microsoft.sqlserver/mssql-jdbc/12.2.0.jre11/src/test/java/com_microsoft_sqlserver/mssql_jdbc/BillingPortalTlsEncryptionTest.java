/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_microsoft_sqlserver.mssql_jdbc;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.microsoft.sqlserver.jdbc.SQLServerException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Exercises TLS channel encryption of the driver's TDS connection: negotiated
/// encryption, server certificate validation against an application trust store,
/// host-name verification, URL-configured trust settings, and protocol pinning.
///
/// The scenario is a billing portal whose SQL Server presents a certificate
/// issued for `localhost`. The portal trusts that certificate through its own
/// PKCS12 trust store and reads invoices only over a validated channel.
public class BillingPortalTlsEncryptionTest {

    private static final String IMAGE = "mcr.microsoft.com/mssql/server:2022-CU14-ubuntu-22.04";

    private static final String PASSWORD = "Billing#Tls2024";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(180);

    private static final String CONTAINER = "real-use-mssql-billing-tls-" + UUID.randomUUID();

    private static final String CERTIFICATE_HOST = "localhost";

    private static final String TRUST_STORE_PASSWORD = "portal-trust";

    /// Generates the server certificate inside the container, points SQL Server at it
    /// through `mssql.conf`, and then starts the server process.
    private static final String SERVER_BOOTSTRAP = """
            set -e
            mkdir -p /var/opt/mssql/tls
            openssl req -x509 -nodes -newkey rsa:2048 -days 30 -subj /CN=localhost \
              -addext subjectAltName=DNS:localhost \
              -keyout /var/opt/mssql/tls/server.key -out /var/opt/mssql/tls/server.pem 2>/dev/null
            printf '[network]\\ntlscert = /var/opt/mssql/tls/server.pem\\n\
            tlskey = /var/opt/mssql/tls/server.key\\ntlsprotocols = 1.2\\nforceencryption = 0\\n' \
              > /var/opt/mssql/mssql.conf
            exec /opt/mssql/bin/sqlservr
            """;

    private static int port;

    private static Path trustStore;

    private static X509Certificate serverCertificate;

    @BeforeAll
    static void startServer() throws Exception {
        docker("run", "-d", "--name", CONTAINER, "-e", "ACCEPT_EULA=Y", "-e", "MSSQL_SA_PASSWORD=" + PASSWORD,
                "-p", "127.0.0.1::1433", "--entrypoint", "sh", IMAGE, "-c", SERVER_BOOTSTRAP);
        String mapping = docker("port", CONTAINER, "1433/tcp").lines().findFirst().orElseThrow();
        port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));

        SQLServerDataSource admin = plainDataSource("master");
        awaitServer(admin);

        String pem = docker("exec", CONTAINER, "cat", "/var/opt/mssql/tls/server.pem");
        serverCertificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
        trustStore = writeTrustStore(serverCertificate);

        try (Connection connection = admin.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE billing");
            statement.execute("""
                    CREATE TABLE billing.dbo.invoices (
                        number NVARCHAR(20) PRIMARY KEY,
                        customer NVARCHAR(60) NOT NULL,
                        amount DECIMAL(10, 2) NOT NULL
                    )""");
        }
    }

    @AfterAll
    static void stopServer() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
        if (trustStore != null) {
            Files.deleteIfExists(trustStore);
        }
    }

    @Test
    void sessionReportsWhetherTheChannelIsEncrypted() throws Exception {
        try (Connection connection = plainDataSource("billing").getConnection()) {
            assertThat(channelEncryption(connection)).isEqualTo("FALSE");
        }

        SQLServerDataSource trustingAnyCertificate = plainDataSource("billing");
        trustingAnyCertificate.setEncrypt("true");
        trustingAnyCertificate.setTrustServerCertificate(true);
        try (Connection connection = trustingAnyCertificate.getConnection()) {
            assertThat(channelEncryption(connection)).isEqualTo("TRUE");
        }
    }

    @Test
    void applicationTrustStoreValidatesTheServerCertificate() throws Exception {
        assertThat(serverCertificate.getSubjectX500Principal().getName()).isEqualTo("CN=localhost");

        try (Connection connection = validatingDataSource("billing").getConnection()) {
            assertThat(channelEncryption(connection)).isEqualTo("TRUE");
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO invoices (number, customer, amount) VALUES (?, ?, ?)")) {
                addInvoice(insert, "INV-1001", "Northwind Traders", "1250.00");
                addInvoice(insert, "INV-1002", "Contoso Pharmacy", "87.40");
                addInvoice(insert, "INV-1003", "Northwind Traders", "310.15");
                assertThat(insert.executeBatch()).containsExactly(1, 1, 1);
            }
            assertThat(outstandingByCustomer(connection)).containsExactly(
                    Map.entry("Contoso Pharmacy", new BigDecimal("87.40")),
                    Map.entry("Northwind Traders", new BigDecimal("1560.15")));
        }
    }

    @Test
    void certificateIssuedForAnotherHostIsRejected() {
        SQLServerDataSource wrongHost = validatingDataSource("billing");
        wrongHost.setHostNameInCertificate("payments.example.com");

        assertThatThrownBy(wrongHost::getConnection)
                .isInstanceOf(SQLServerException.class)
                .hasMessageContaining("payments.example.com");
    }

    @Test
    void certificateMissingFromTheTrustStoreIsRejected() {
        SQLServerDataSource defaultTrust = plainDataSource("billing");
        defaultTrust.setEncrypt("true");
        defaultTrust.setTrustServerCertificate(false);
        defaultTrust.setHostNameInCertificate(CERTIFICATE_HOST);

        assertThatThrownBy(defaultTrust::getConnection)
                .isInstanceOf(SQLServerException.class)
                .hasMessageContaining("SSL");
    }

    @Test
    void connectionUrlConfiguresTheTrustStore() throws Exception {
        String url = ("jdbc:sqlserver://127.0.0.1:%d;databaseName=billing;user=sa;password=%s;encrypt=true;"
                + "trustStore=%s;trustStorePassword=%s;trustStoreType=PKCS12;hostNameInCertificate=%s;loginTimeout=10")
                .formatted(port, PASSWORD, trustStore, TRUST_STORE_PASSWORD, CERTIFICATE_HOST);

        try (Connection connection = DriverManager.getConnection(url)) {
            assertThat(channelEncryption(connection)).isEqualTo("TRUE");
            assertThat(connection.getMetaData().getURL()).contains("encrypt=true");
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT DB_NAME(), SUSER_SNAME()")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString(1)).isEqualTo("billing");
                assertThat(resultSet.getString(2)).isEqualTo("sa");
            }
        }
    }

    @Test
    void sslProtocolPinsTheHandshakeVersion() throws Exception {
        SQLServerDataSource tls12 = validatingDataSource("billing");
        tls12.setSSLProtocol("TLSv1.2");
        try (Connection connection = tls12.getConnection()) {
            assertThat(channelEncryption(connection)).isEqualTo("TRUE");
        }

        SQLServerDataSource unknownProtocol = validatingDataSource("billing");
        unknownProtocol.setSSLProtocol("SSLv3");
        assertThatThrownBy(unknownProtocol::getConnection)
                .isInstanceOf(SQLServerException.class)
                .hasMessageContaining("SSLv3");
    }

    private static SQLServerDataSource plainDataSource(String database) {
        SQLServerDataSource dataSource = new SQLServerDataSource();
        dataSource.setServerName("127.0.0.1");
        dataSource.setPortNumber(port);
        dataSource.setDatabaseName(database);
        dataSource.setUser("sa");
        dataSource.setPassword(PASSWORD);
        dataSource.setEncrypt("false");
        dataSource.setLoginTimeout(10);
        return dataSource;
    }

    private static SQLServerDataSource validatingDataSource(String database) {
        SQLServerDataSource dataSource = plainDataSource(database);
        dataSource.setEncrypt("true");
        dataSource.setTrustServerCertificate(false);
        dataSource.setTrustStore(trustStore.toString());
        dataSource.setTrustStorePassword(TRUST_STORE_PASSWORD);
        dataSource.setTrustStoreType("PKCS12");
        dataSource.setHostNameInCertificate(CERTIFICATE_HOST);
        return dataSource;
    }

    private static String channelEncryption(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT encrypt_option FROM sys.dm_exec_connections WHERE session_id = @@SPID")) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }

    private static void addInvoice(PreparedStatement insert, String number, String customer, String amount)
            throws SQLException {
        insert.setString(1, number);
        insert.setString(2, customer);
        insert.setBigDecimal(3, new BigDecimal(amount));
        insert.addBatch();
    }

    private static Map<String, BigDecimal> outstandingByCustomer(Connection connection) throws SQLException {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT customer, SUM(amount) FROM invoices GROUP BY customer ORDER BY customer")) {
            while (resultSet.next()) {
                totals.put(resultSet.getString(1), resultSet.getBigDecimal(2));
            }
        }
        return totals;
    }

    private static Path writeTrustStore(X509Certificate certificate) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setCertificateEntry("billing-sql-server", certificate);
        Path file = Files.createTempFile("billing-trust", ".p12");
        try (OutputStream output = Files.newOutputStream(file)) {
            store.store(output, TRUST_STORE_PASSWORD.toCharArray());
        }
        return file;
    }

    private static void awaitServer(SQLServerDataSource admin) throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        SQLException last = null;
        while (System.nanoTime() < deadline) {
            try (Connection connection = admin.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
                return;
            } catch (SQLException e) {
                last = e;
            }
            Thread.sleep(1_000);
        }
        throw new IllegalStateException("SQL Server did not accept connections in time", last);
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
