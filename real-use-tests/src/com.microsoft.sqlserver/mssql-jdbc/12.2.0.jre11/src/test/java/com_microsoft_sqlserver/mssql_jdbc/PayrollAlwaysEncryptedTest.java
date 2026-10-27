/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_microsoft_sqlserver.mssql_jdbc;

import com.microsoft.sqlserver.jdbc.SQLServerColumnEncryptionKeyStoreProvider;
import com.microsoft.sqlserver.jdbc.SQLServerConnection;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.microsoft.sqlserver.jdbc.SQLServerException;
import com.microsoft.sqlserver.jdbc.SQLServerPreparedStatement;
import com.microsoft.sqlserver.jdbc.SQLServerStatementColumnEncryptionSetting;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Protects sensitive columns with Always Encrypted through an application-supplied
/// column master key store provider registered on the connection.
///
/// The scenario is a payroll service: the driver encrypts social security numbers
/// (deterministic) and salaries and birth dates (randomized) on the client, the
/// server only ever stores ciphertext, and equality lookups still work on the
/// deterministic column. The column encryption key is wrapped by an RSA master key
/// that never leaves the application's own vault.
public class PayrollAlwaysEncryptedTest {

    private static final String IMAGE = "mcr.microsoft.com/mssql/server:2022-CU14-ubuntu-22.04";

    private static final String PASSWORD = "Payroll#Vault2024";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(180);

    private static final String CONTAINER = "real-use-mssql-ae-" + UUID.randomUUID();

    private static final String PROVIDER_NAME = "PAYROLL_VAULT";

    private static final String MASTER_KEY_PATH = "vault/payroll/master-2024";

    private static final PayrollVault VAULT = new PayrollVault();

    private static SQLServerDataSource dataSource;

    @BeforeAll
    static void startServer() throws Exception {
        docker("run", "-d", "--name", CONTAINER, "-e", "ACCEPT_EULA=Y", "-e", "MSSQL_SA_PASSWORD=" + PASSWORD,
                "-p", "127.0.0.1::1433", IMAGE);
        String mapping = docker("port", CONTAINER, "1433/tcp").lines().findFirst().orElseThrow();
        int port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));

        dataSource = new SQLServerDataSource();
        dataSource.setServerName("127.0.0.1");
        dataSource.setPortNumber(port);
        dataSource.setUser("sa");
        dataSource.setPassword(PASSWORD);
        dataSource.setEncrypt("false");
        dataSource.setApplicationName("payroll-service");
        dataSource.setLoginTimeout(5);
        awaitServer();
        createSchema();
        hireEmployees();
    }

    @AfterAll
    static void stopServer() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @Test
    void encryptedConnectionReadsPlaintextBack() throws Exception {
        try (Connection connection = encryptedConnection();
                PreparedStatement query = connection.prepareStatement("""
                        SELECT name, ssn, salary, birth_date FROM dbo.employees WHERE id = ?
                        """)) {
            query.setInt(1, 2);
            try (ResultSet row = query.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("name")).isEqualTo("Grace Hopper");
                assertThat(row.getString("ssn")).isEqualTo("222-33-4444");
                assertThat(row.getBigDecimal("salary")).isEqualByComparingTo(new BigDecimal("98500.00"));
                assertThat(row.getDate("birth_date").toLocalDate()).isEqualTo(LocalDate.of(1906, 12, 9));
                assertThat(row.next()).isFalse();
            }
        }

        assertThat(VAULT.unwrapCount()).isPositive();
    }

    @Test
    void deterministicColumnSupportsEqualityLookups() throws Exception {
        List<String> holders = new ArrayList<>();
        try (Connection connection = encryptedConnection();
                PreparedStatement query = connection.prepareStatement("""
                        SELECT name FROM dbo.employees WHERE ssn = ? ORDER BY id
                        """)) {
            SQLServerPreparedStatement lookup = query.unwrap(SQLServerPreparedStatement.class);
            lookup.setNString(1, "111-22-3333", true);
            try (ResultSet rows = lookup.executeQuery()) {
                while (rows.next()) {
                    holders.add(rows.getString(1));
                }
            }
        }

        assertThat(holders).containsExactly("Ada Lovelace", "Ada Lovelace (contractor)");
    }

    @Test
    void serverStoresOnlyCiphertext() throws Exception {
        Map<Integer, byte[]> ssnCiphertext = new HashMap<>();
        Map<Integer, byte[]> salaryCiphertext = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT id, ssn, salary FROM dbo.employees")) {
            while (rows.next()) {
                ssnCiphertext.put(rows.getInt("id"), rows.getBytes("ssn"));
                salaryCiphertext.put(rows.getInt("id"), rows.getBytes("salary"));
            }
        }

        assertThat(ssnCiphertext).hasSize(3);
        byte[] adaSsn = ssnCiphertext.get(1);
        assertThat(new String(adaSsn, StandardCharsets.UTF_16LE)).doesNotContain("111-22-3333");
        assertThat(adaSsn[0]).as("AEAD_AES_256_CBC_HMAC_SHA_256 version byte").isEqualTo((byte) 1);
        assertThat(ssnCiphertext.get(3)).as("deterministic encryption of the same SSN").isEqualTo(adaSsn);
        assertThat(ssnCiphertext.get(2)).isNotEqualTo(adaSsn);
        assertThat(salaryCiphertext.get(3)).as("randomized encryption of the same salary")
                .isNotEqualTo(salaryCiphertext.get(1));
    }

    @Test
    void statementCanOptOutOfDecryption() throws Exception {
        try (Connection connection = encryptedConnection();
                PreparedStatement query = connection.unwrap(SQLServerConnection.class).prepareStatement(
                        "SELECT ssn FROM dbo.employees WHERE id = 1", ResultSet.TYPE_FORWARD_ONLY,
                        ResultSet.CONCUR_READ_ONLY, ResultSet.HOLD_CURSORS_OVER_COMMIT,
                        SQLServerStatementColumnEncryptionSetting.Disabled);
                ResultSet row = query.executeQuery()) {
            assertThat(row.next()).isTrue();
            byte[] ciphertext = row.getBytes(1);
            assertThat(ciphertext).isNotEqualTo("111-22-3333".getBytes(StandardCharsets.UTF_16LE));
            assertThat(ciphertext.length).isGreaterThan(32);
        }
    }

    @Test
    void plaintextWritesWithoutEncryptionAreRejected() throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO dbo.employees (id, name, ssn, salary, birth_date) VALUES (?, ?, ?, ?, ?)
                        """)) {
            insert.setInt(1, 99);
            insert.setString(2, "Mallory");
            insert.setString(3, "999-99-9999");
            insert.setBigDecimal(4, new BigDecimal("1.00"));
            insert.setDate(5, Date.valueOf(LocalDate.of(1990, 1, 1)));

            assertThatThrownBy(insert::executeUpdate)
                    .isInstanceOfSatisfying(SQLServerException.class, failure -> {
                        assertThat(failure.getErrorCode()).isEqualTo(206);
                        assertThat(failure.getMessage()).contains("Operand type clash", "encrypted with");
                    });
        }

        try (Connection connection = encryptedConnection();
                Statement statement = connection.createStatement();
                ResultSet count = statement.executeQuery("SELECT COUNT(*) FROM dbo.employees")) {
            assertThat(count.next()).isTrue();
            assertThat(count.getInt(1)).isEqualTo(3);
        }
    }

    @Test
    void untrustedMasterKeyPathBlocksDecryption() throws Exception {
        // The driver keys trusted master key paths by "<server>:<port>".
        String server = dataSource.getServerName() + ":" + dataSource.getPortNumber();
        SQLServerConnection.updateColumnEncryptionTrustedMasterKeyPaths(server, List.of("vault/payroll/retired-2019"));
        try (Connection connection = encryptedConnection();
                PreparedStatement query = connection.prepareStatement("SELECT ssn FROM dbo.employees WHERE id = ?")) {
            query.setInt(1, 1);
            assertThatThrownBy(() -> {
                try (ResultSet row = query.executeQuery()) {
                    row.next();
                    row.getString(1);
                }
            }).isInstanceOf(SQLServerException.class).hasMessageContaining(MASTER_KEY_PATH);
        } finally {
            SQLServerConnection.removeColumnEncryptionTrustedMasterKeyPaths(server);
        }
    }

    private static Connection encryptedConnection() throws SQLException {
        SQLServerDataSource encrypted = new SQLServerDataSource();
        encrypted.setServerName(dataSource.getServerName());
        encrypted.setPortNumber(dataSource.getPortNumber());
        encrypted.setUser("sa");
        encrypted.setPassword(PASSWORD);
        encrypted.setEncrypt("false");
        encrypted.setApplicationName("payroll-service");
        encrypted.setColumnEncryptionSetting("Enabled");
        SQLServerConnection connection = (SQLServerConnection) encrypted.getConnection();
        connection.registerColumnEncryptionKeyStoreProvidersOnConnection(Map.of(PROVIDER_NAME, VAULT));
        return connection;
    }

    private static void hireEmployees() throws SQLException {
        try (Connection connection = encryptedConnection();
                PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO dbo.employees (id, name, ssn, salary, birth_date) VALUES (?, ?, ?, ?, ?)
                        """)) {
            SQLServerPreparedStatement hire = insert.unwrap(SQLServerPreparedStatement.class);
            Object[][] staff = {
                {1, "Ada Lovelace", "111-22-3333", "120000.00", LocalDate.of(1815, 12, 10)},
                {2, "Grace Hopper", "222-33-4444", "98500.00", LocalDate.of(1906, 12, 9)},
                {3, "Ada Lovelace (contractor)", "111-22-3333", "120000.00", LocalDate.of(1815, 12, 10)},
            };
            for (Object[] employee : staff) {
                hire.setInt(1, (Integer) employee[0]);
                hire.setString(2, (String) employee[1]);
                hire.setNString(3, (String) employee[2], true);
                hire.setBigDecimal(4, new BigDecimal((String) employee[3]), 10, 2, true);
                hire.setDate(5, Date.valueOf((LocalDate) employee[4]), null, true);
                assertThat(hire.executeUpdate()).isEqualTo(1);
            }
        }
    }

    private static void createSchema() throws Exception {
        byte[] columnKey = new byte[32];
        new SecureRandom().nextBytes(columnKey);
        byte[] wrappedKey = VAULT.encryptColumnEncryptionKey(MASTER_KEY_PATH, "RSA_OAEP", columnKey);

        List<String> ddl = List.of(
                """
                CREATE COLUMN MASTER KEY PayrollMasterKey WITH (
                    KEY_STORE_PROVIDER_NAME = N'%s',
                    KEY_PATH = N'%s')
                """.formatted(PROVIDER_NAME, MASTER_KEY_PATH),
                """
                CREATE COLUMN ENCRYPTION KEY PayrollColumnKey WITH VALUES (
                    COLUMN_MASTER_KEY = PayrollMasterKey,
                    ALGORITHM = 'RSA_OAEP',
                    ENCRYPTED_VALUE = 0x%s)
                """.formatted(HexFormat.of().formatHex(wrappedKey)),
                """
                CREATE TABLE dbo.employees (
                    id INT PRIMARY KEY,
                    name NVARCHAR(64) NOT NULL,
                    ssn NVARCHAR(11) COLLATE Latin1_General_BIN2 ENCRYPTED WITH (
                        COLUMN_ENCRYPTION_KEY = PayrollColumnKey,
                        ENCRYPTION_TYPE = DETERMINISTIC,
                        ALGORITHM = 'AEAD_AES_256_CBC_HMAC_SHA_256') NOT NULL,
                    salary DECIMAL(10, 2) ENCRYPTED WITH (
                        COLUMN_ENCRYPTION_KEY = PayrollColumnKey,
                        ENCRYPTION_TYPE = RANDOMIZED,
                        ALGORITHM = 'AEAD_AES_256_CBC_HMAC_SHA_256') NOT NULL,
                    birth_date DATE ENCRYPTED WITH (
                        COLUMN_ENCRYPTION_KEY = PayrollColumnKey,
                        ENCRYPTION_TYPE = RANDOMIZED,
                        ALGORITHM = 'AEAD_AES_256_CBC_HMAC_SHA_256') NOT NULL)
                """);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : ddl) {
                statement.execute(sql);
            }
        }
    }

    private static void awaitServer() throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        SQLException last = null;
        while (System.nanoTime() < deadline) {
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
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

    /// The application's key vault: an RSA master key that wraps and unwraps
    /// column encryption keys with RSA-OAEP and never leaves the process.
    static final class PayrollVault extends SQLServerColumnEncryptionKeyStoreProvider {

        private final KeyPair masterKey;

        private final AtomicInteger unwraps = new AtomicInteger();

        private String name = PROVIDER_NAME;

        PayrollVault() {
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                masterKey = generator.generateKeyPair();
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException(e);
            }
        }

        int unwrapCount() {
            return unwraps.get();
        }

        @Override
        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public byte[] encryptColumnEncryptionKey(String masterKeyPath, String algorithm, byte[] columnKey) {
            return transform(Cipher.ENCRYPT_MODE, masterKeyPath, algorithm, columnKey);
        }

        @Override
        public byte[] decryptColumnEncryptionKey(String masterKeyPath, String algorithm, byte[] wrappedKey) {
            unwraps.incrementAndGet();
            return transform(Cipher.DECRYPT_MODE, masterKeyPath, algorithm, wrappedKey);
        }

        @Override
        public boolean verifyColumnMasterKeyMetadata(String masterKeyPath, boolean allowEnclaveComputations,
                byte[] signature) {
            return MASTER_KEY_PATH.equals(masterKeyPath) && !allowEnclaveComputations;
        }

        private byte[] transform(int mode, String masterKeyPath, String algorithm, byte[] input) {
            if (!MASTER_KEY_PATH.equals(masterKeyPath)) {
                throw new IllegalArgumentException("Unknown master key " + masterKeyPath);
            }
            if (!"RSA_OAEP".equalsIgnoreCase(algorithm)) {
                throw new IllegalArgumentException("Unsupported key encryption algorithm " + algorithm);
            }
            try {
                Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
                cipher.init(mode, mode == Cipher.ENCRYPT_MODE ? masterKey.getPublic() : masterKey.getPrivate());
                return cipher.doFinal(input);
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
