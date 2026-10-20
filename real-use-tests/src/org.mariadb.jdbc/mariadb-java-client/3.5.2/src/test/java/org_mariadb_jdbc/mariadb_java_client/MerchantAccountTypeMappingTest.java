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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Numeric, boolean, UUID, enumerated and JSON column mapping: `BIGINT UNSIGNED` as
/// `BigInteger`, exact `DECIMAL` money, `TINYINT(1)` and `BIT` flags, `YEAR`, `UUID`,
/// `ENUM`, `SET` and `JSON` values bound and read back over the text and binary protocols,
/// with the `uuidAsString`, `tinyInt1isBit` and `yearIsDateType` connection options.
///
/// The scenario is a payment platform's merchant registry: lifetime volumes exceed the
/// signed 64-bit range, balances must never lose a cent, and merchant settings live in a
/// JSON document the platform queries on the server.
public class MerchantAccountTypeMappingTest {

    private static final String IMAGE = "mariadb:13.0";

    private static final String CONTAINER = "real-use-mariadb-types-" + UUID.randomUUID();

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(120);

    private static final String DATABASE = "payments";

    private static final String USER = "registry";

    private static final String PASSWORD = "registry-secret";

    /// The largest `BIGINT UNSIGNED`, one past what a Java `long` can hold twice over.
    private static final BigInteger MAX_UNSIGNED_BIGINT = new BigInteger("18446744073709551615");

    /// A merchant name with an accented letter and an emoji, kept ASCII in source through escapes.
    private static final String ROCKET_CAFE = "Rocket Caf\u00e9 \uD83D\uDE80";

    private static String url;

    private static MariaDbDataSource textProtocolSource;

    private static MariaDbDataSource binaryProtocolSource;

    private static MariaDbDataSource legacyMappingSource;

    @BeforeAll
    static void startServer() throws Exception {
        docker("run", "-d", "--name", CONTAINER, "-p", "127.0.0.1::3306",
                "-e", "MARIADB_DATABASE=" + DATABASE,
                "-e", "MARIADB_USER=" + USER,
                "-e", "MARIADB_PASSWORD=" + PASSWORD,
                "-e", "MARIADB_ALLOW_EMPTY_ROOT_PASSWORD=true",
                IMAGE);
        String mapping = docker("port", CONTAINER, "3306/tcp").lines().findFirst().orElseThrow();
        int port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));
        url = "jdbc:mariadb://127.0.0.1:" + port + "/" + DATABASE;

        textProtocolSource = dataSource("useServerPrepStmts=false");
        binaryProtocolSource = dataSource("useServerPrepStmts=true");
        legacyMappingSource = dataSource("uuidAsString=true&tinyInt1isBit=false&yearIsDateType=false");
        awaitServer(textProtocolSource);

        try (Connection connection = textProtocolSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE merchants (
                        id UUID NOT NULL PRIMARY KEY,
                        name VARCHAR(64) CHARACTER SET utf8mb4 NOT NULL,
                        lifetime_volume_cents BIGINT UNSIGNED NOT NULL,
                        balance DECIMAL(30, 10) NOT NULL,
                        fee_rate DOUBLE NOT NULL,
                        risk_score FLOAT NOT NULL,
                        active TINYINT(1) NOT NULL,
                        permissions BIT(8) NOT NULL,
                        founded YEAR NOT NULL,
                        tier ENUM('BRONZE', 'SILVER', 'GOLD') NOT NULL,
                        channels SET('WEB', 'POS', 'MOBILE') NOT NULL,
                        settings JSON NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");
        }
    }

    @AfterAll
    static void stopServer() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @BeforeEach
    void clearRegistry() throws SQLException {
        try (Connection connection = textProtocolSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM merchants");
        }
    }

    @Test
    void merchantsRoundTripOverBothProtocols() throws SQLException {
        Merchant cafe = new Merchant(UUID.fromString("3f1c2a9e-7b4d-4e21-9c55-0a6b8d2e4f10"), ROCKET_CAFE,
                MAX_UNSIGNED_BIGINT, new BigDecimal("12345678901234567890.0123456789"), 0.0175, 0.25f,
                true, 0b1010_0101, 2019, "GOLD", "POS,WEB", "{\"currency\": \"EUR\", \"payoutDays\": 2}");
        Merchant kiosk = new Merchant(UUID.fromString("a0b1c2d3-e4f5-4a6b-8c7d-9e0f1a2b3c4d"), "Corner Kiosk",
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), new BigDecimal("-0.0000000001"), 0.029,
                0.875f, false, 0b0000_0001, 1999, "BRONZE", "MOBILE", "{\"currency\": \"USD\", \"payoutDays\": 7}");

        new MerchantRegistry(textProtocolSource).register(cafe);
        new MerchantRegistry(binaryProtocolSource).register(kiosk);

        for (MariaDbDataSource source : List.of(textProtocolSource, binaryProtocolSource)) {
            MerchantRegistry registry = new MerchantRegistry(source);
            // A `SET` comes back in declaration order, whatever order it was written in.
            assertThat(registry.find(cafe.id())).isEqualTo(cafe.withChannels("WEB,POS"));
            assertThat(registry.find(kiosk.id())).isEqualTo(kiosk);
            assertThat(registry.activeMerchantNames()).containsExactly(ROCKET_CAFE);
        }
    }

    @Test
    void defaultObjectMappingFollowsTheColumnTypes() throws SQLException {
        Merchant cafe = sampleMerchant();
        new MerchantRegistry(textProtocolSource).register(cafe);

        for (MariaDbDataSource source : List.of(textProtocolSource, binaryProtocolSource)) {
            try (Connection connection = source.getConnection();
                    PreparedStatement query = connection.prepareStatement("""
                            SELECT id, lifetime_volume_cents, balance, active, permissions, founded, tier, settings
                            FROM merchants WHERE id = ?""")) {
                query.setObject(1, cafe.id());
                try (ResultSet row = query.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getObject("id")).isEqualTo(cafe.id());
                    assertThat(row.getObject("lifetime_volume_cents")).isEqualTo(cafe.lifetimeVolumeCents());
                    assertThat(row.getObject("balance")).isEqualTo(cafe.balance());
                    assertThat(row.getObject("active")).isEqualTo(Boolean.TRUE);
                    assertThat(row.getObject("permissions")).isEqualTo(new byte[] {(byte) 0xA5});
                    assertThat(row.getObject("founded")).isEqualTo(Date.valueOf("2019-01-01"));
                    assertThat(row.getObject("tier")).isEqualTo("GOLD");
                    assertThat(row.getObject("settings")).isEqualTo(cafe.settings());

                    ResultSetMetaData shape = row.getMetaData();
                    assertThat(shape.getColumnType(2)).isEqualTo(Types.BIGINT);
                    assertThat(shape.isSigned(2)).isFalse();
                    assertThat(shape.getColumnClassName(2)).isEqualTo(BigInteger.class.getName());
                    assertThat(shape.getColumnType(3)).isEqualTo(Types.DECIMAL);
                    assertThat(shape.getPrecision(3)).isEqualTo(30);
                    assertThat(shape.getScale(3)).isEqualTo(10);
                    assertThat(shape.getColumnType(4)).isEqualTo(Types.BOOLEAN);
                }
            }
        }
    }

    @Test
    void legacyOptionsMapUuidFlagsAndYearsToPlainValues() throws SQLException {
        Merchant cafe = sampleMerchant();
        new MerchantRegistry(binaryProtocolSource).register(cafe);

        try (Connection connection = legacyMappingSource.getConnection();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT id, active, founded FROM merchants WHERE name = ?")) {
            query.setString(1, cafe.name());
            try (ResultSet row = query.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getObject("id")).isEqualTo(cafe.id().toString());
                assertThat(row.getObject("active")).isEqualTo(1);
                assertThat(row.getObject("founded")).isEqualTo((short) 2019);
                assertThat(row.getMetaData().getColumnType(2)).isEqualTo(Types.TINYINT);
            }
        }
    }

    @Test
    void volumesBeyondLongRangeAreRejectedAsLongButKeptAsBigInteger() throws SQLException {
        Merchant cafe = sampleMerchant();
        new MerchantRegistry(textProtocolSource).register(cafe);

        for (MariaDbDataSource source : List.of(textProtocolSource, binaryProtocolSource)) {
            try (Connection connection = source.getConnection();
                    PreparedStatement query = connection.prepareStatement(
                            "SELECT lifetime_volume_cents, lifetime_volume_cents DIV 2 FROM merchants WHERE id = ?")) {
                query.setObject(1, cafe.id());
                try (ResultSet row = query.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    assertThatThrownBy(() -> row.getLong(1)).isInstanceOf(SQLException.class);
                    assertThat(row.getObject(1, BigInteger.class)).isEqualTo(MAX_UNSIGNED_BIGINT);
                    assertThat(row.getString(1)).isEqualTo("18446744073709551615");
                    assertThat(row.getLong(2)).isEqualTo(Long.MAX_VALUE);
                }
            }
        }
    }

    @Test
    void balancesAddUpExactlyOnTheServerAndTheClient() throws SQLException {
        MerchantRegistry registry = new MerchantRegistry(binaryProtocolSource);
        BigDecimal expected = BigDecimal.ZERO;
        for (int i = 1; i <= 10; i++) {
            BigDecimal balance = new BigDecimal("0.1000000001").multiply(BigDecimal.valueOf(i));
            registry.register(new Merchant(UUID.randomUUID(), "Merchant " + i, BigInteger.valueOf(i),
                    balance.setScale(10), 0.01, 0.5f, true, 0, 2000 + i, "SILVER", "WEB", "{}"));
            expected = expected.add(balance);
        }

        assertThat(registry.totalBalance()).isEqualByComparingTo(expected);
        assertThat(registry.totalBalance()).isEqualByComparingTo("5.5000000055");
        assertThat(registry.clientSideTotal()).isEqualByComparingTo(expected);
    }

    @Test
    void settingsAreQueriedAsJsonAndEnumeratedValuesFilterOnTheServer() throws SQLException {
        MerchantRegistry registry = new MerchantRegistry(textProtocolSource);
        registry.register(sampleMerchant());
        registry.register(new Merchant(UUID.randomUUID(), "Night Market", BigInteger.TEN, BigDecimal.ONE.setScale(10),
                0.02, 0.1f, true, 0b0000_0011, 2021, "SILVER", "MOBILE,POS",
                "{\"currency\": \"EUR\", \"payoutDays\": 1}"));
        registry.register(new Merchant(UUID.randomUUID(), "Harbor Books", BigInteger.ONE, BigDecimal.ONE.setScale(10),
                0.02, 0.1f, true, 0b0000_0000, 2010, "BRONZE", "WEB",
                "{\"currency\": \"GBP\", \"payoutDays\": 3}"));

        assertThat(registry.namesSettlingIn("EUR")).containsExactly("Night Market", ROCKET_CAFE);
        assertThat(registry.namesSellingThrough("POS")).containsExactly("Night Market", ROCKET_CAFE);
        assertThat(registry.namesWithPermission(0b0000_0001)).containsExactly("Night Market", ROCKET_CAFE);
        assertThat(registry.namesAtOrAbove("SILVER")).containsExactly(ROCKET_CAFE, "Night Market");
    }

    private static Merchant sampleMerchant() {
        return new Merchant(UUID.fromString("3f1c2a9e-7b4d-4e21-9c55-0a6b8d2e4f10"), ROCKET_CAFE,
                MAX_UNSIGNED_BIGINT, new BigDecimal("12345678901234567890.0123456789"), 0.0175, 0.25f,
                true, 0b1010_0101, 2019, "GOLD", "POS,WEB", "{\"currency\": \"EUR\", \"payoutDays\": 2}");
    }

    private static MariaDbDataSource dataSource(String options) throws SQLException {
        MariaDbDataSource source = new MariaDbDataSource(url + "?" + options);
        source.setUser(USER);
        source.setPassword(PASSWORD);
        return source;
    }

    private static void awaitServer(MariaDbDataSource source) throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        SQLException last = null;
        while (System.nanoTime() < deadline) {
            try (Connection connection = source.getConnection()) {
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

    /// A merchant account as the registry stores it; `permissions` is an 8-bit flag mask.
    record Merchant(UUID id, String name, BigInteger lifetimeVolumeCents, BigDecimal balance, double feeRate,
            float riskScore, boolean active, int permissions, int founded, String tier, String channels,
            String settings) {

        Merchant withChannels(String newChannels) {
            return new Merchant(id, name, lifetimeVolumeCents, balance, feeRate, riskScore, active, permissions,
                    founded, tier, newChannels, settings);
        }
    }

    /// Registers merchants and answers the platform's account queries.
    static final class MerchantRegistry {

        private final MariaDbDataSource source;

        MerchantRegistry(MariaDbDataSource source) {
            this.source = source;
        }

        void register(Merchant merchant) throws SQLException {
            try (Connection connection = source.getConnection();
                    PreparedStatement insert = connection.prepareStatement("""
                            INSERT INTO merchants (id, name, lifetime_volume_cents, balance, fee_rate, risk_score,
                                active, permissions, founded, tier, channels, settings)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")) {
                insert.setObject(1, merchant.id());
                insert.setString(2, merchant.name());
                insert.setObject(3, merchant.lifetimeVolumeCents());
                insert.setBigDecimal(4, merchant.balance());
                insert.setDouble(5, merchant.feeRate());
                insert.setFloat(6, merchant.riskScore());
                insert.setBoolean(7, merchant.active());
                insert.setInt(8, merchant.permissions());
                insert.setShort(9, (short) merchant.founded());
                insert.setString(10, merchant.tier());
                insert.setString(11, merchant.channels());
                insert.setString(12, merchant.settings());
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
        }

        Merchant find(UUID id) throws SQLException {
            try (Connection connection = source.getConnection();
                    PreparedStatement query = connection.prepareStatement("""
                            SELECT id, name, lifetime_volume_cents, balance, fee_rate, risk_score, active,
                                permissions, founded, tier, channels, settings
                            FROM merchants WHERE id = ?""")) {
                query.setObject(1, id);
                try (ResultSet row = query.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    Merchant merchant = new Merchant(
                            row.getObject("id", UUID.class),
                            row.getString("name"),
                            row.getObject("lifetime_volume_cents", BigInteger.class),
                            row.getBigDecimal("balance"),
                            row.getDouble("fee_rate"),
                            row.getFloat("risk_score"),
                            row.getBoolean("active"),
                            row.getInt("permissions"),
                            row.getShort("founded"),
                            row.getString("tier"),
                            row.getString("channels"),
                            row.getString("settings"));
                    assertThat(row.next()).isFalse();
                    return merchant;
                }
            }
        }

        List<String> activeMerchantNames() throws SQLException {
            return names("SELECT name FROM merchants WHERE active = ? ORDER BY name", true);
        }

        List<String> namesSettlingIn(String currency) throws SQLException {
            return names("SELECT name FROM merchants WHERE JSON_VALUE(settings, '$.currency') = ?"
                    + " ORDER BY JSON_VALUE(settings, '$.payoutDays')", currency);
        }

        List<String> namesSellingThrough(String channel) throws SQLException {
            return names("SELECT name FROM merchants WHERE FIND_IN_SET(?, channels) > 0 ORDER BY name", channel);
        }

        List<String> namesWithPermission(int flag) throws SQLException {
            return names("SELECT name FROM merchants WHERE permissions & ? <> 0 ORDER BY name", flag);
        }

        /// `ENUM` values order by their declaration index, so `GOLD` outranks `SILVER`.
        List<String> namesAtOrAbove(String tier) throws SQLException {
            return names("SELECT name FROM merchants WHERE tier + 0 >= (SELECT FIND_IN_SET(?, 'BRONZE,SILVER,GOLD'))"
                    + " ORDER BY tier DESC, name", tier);
        }

        BigDecimal totalBalance() throws SQLException {
            try (Connection connection = source.getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet row = statement.executeQuery("SELECT SUM(balance) FROM merchants")) {
                assertThat(row.next()).isTrue();
                return row.getBigDecimal(1);
            }
        }

        BigDecimal clientSideTotal() throws SQLException {
            BigDecimal total = BigDecimal.ZERO;
            try (Connection connection = source.getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("SELECT balance FROM merchants")) {
                while (rows.next()) {
                    total = total.add(rows.getObject(1, BigDecimal.class));
                }
            }
            return total;
        }

        private List<String> names(String sql, Object parameter) throws SQLException {
            List<String> names = new ArrayList<>();
            try (Connection connection = source.getConnection();
                    PreparedStatement query = connection.prepareStatement(sql)) {
                query.setObject(1, parameter);
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        names.add(rows.getString(1));
                    }
                }
            }
            return names;
        }
    }
}
