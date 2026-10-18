/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mysql.mysql_connector_j;

import com.mysql.cj.jdbc.JdbcPreparedStatement;
import com.mysql.cj.jdbc.MysqlDataSource;
import com.mysql.cj.jdbc.ServerPreparedStatement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Server-side prepared statements over the binary protocol: a price catalog prepares its
/// statements on the server (`useServerPrepStmts`), reuses them through the driver's
/// `cachePrepStmts` cache with LRU eviction, round-trips typed values in binary rows, and
/// contrasts that with client-side emulation and with `emulateUnsupportedPstmts` fallback.
public class PriceCatalogServerPreparedStatementTest {

    private static final String IMAGE = "container-registry.oracle.com/mysql/community-server:26.7.1";
    private static final String DATABASE = "catalog";
    private static final String USER = "pricing";
    private static final String PASSWORD = "pricing-secret";
    private static final String CONTAINER = "real-use-mysql-prepared-" + UUID.randomUUID();
    private static final String LOOKUP = "SELECT list_price FROM price WHERE sku = ?";
    private static final String SELECT_ROW = """
            SELECT sku, list_price, units, discount, active, barcode, note, valid_from
            FROM price WHERE sku = ?""";
    private static final BigInteger MAX_UNSIGNED_BIGINT = new BigInteger("18446744073709551615");

    private static String baseUrl;

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
                "-p", "127.0.0.1:" + port + ":3306",
                IMAGE);
        baseUrl = "jdbc:mysql://127.0.0.1:" + port + "/" + DATABASE;
        awaitServer(dataSource("useServerPrepStmts=false"), TimeUnit.MINUTES.toMillis(3));
        createSchema();
    }

    @AfterAll
    static void stopMysql() throws Exception {
        docker("rm", "-f", CONTAINER);
    }

    @Test
    void cachedServerStatementsAreReusedAndEvicted() throws SQLException {
        MysqlDataSource cached = dataSource("useServerPrepStmts=true&cachePrepStmts=true&prepStmtCacheSize=2");
        try (Connection connection = cached.getConnection()) {
            StatementCounters before = StatementCounters.read(connection);

            long firstId;
            try (PreparedStatement lookup = connection.prepareStatement(LOOKUP)) {
                assertThat(lookup.isWrapperFor(ServerPreparedStatement.class)).isTrue();
                firstId = lookup.unwrap(ServerPreparedStatement.class).getServerStatementId();
                assertThat(listPrice(lookup, "BOLT-M6")).isEqualByComparingTo("0.1250");
                assertThat(listPrice(lookup, "NUT-M6")).isEqualByComparingTo("0.0800");
            }
            // Closing returned the statement to the cache: preparing the same SQL reuses it.
            try (PreparedStatement lookup = connection.prepareStatement(LOOKUP)) {
                assertThat(lookup.unwrap(ServerPreparedStatement.class).getServerStatementId())
                        .isEqualTo(firstId);
                assertThat(listPrice(lookup, "WASHER-M6")).isEqualByComparingTo("0.0150");
            }
            StatementCounters reused = StatementCounters.read(connection).minus(before);
            assertThat(reused.prepared()).isEqualTo(1);
            assertThat(reused.executed()).isEqualTo(3);
            assertThat(reused.closed()).isZero();

            // Two other statements fill the two-entry cache and evict the lookup on the server.
            for (String sql : List.of("SELECT units FROM price WHERE sku = ?",
                    "SELECT discount FROM price WHERE sku = ?")) {
                try (PreparedStatement other = connection.prepareStatement(sql)) {
                    other.setString(1, "BOLT-M6");
                    try (ResultSet row = other.executeQuery()) {
                        assertThat(row.next()).isTrue();
                    }
                }
            }
            try (PreparedStatement lookup = connection.prepareStatement(LOOKUP)) {
                assertThat(lookup.unwrap(ServerPreparedStatement.class).getServerStatementId())
                        .isNotEqualTo(firstId);
                assertThat(listPrice(lookup, "BOLT-M6")).isEqualByComparingTo("0.1250");
            }
            StatementCounters evicted = StatementCounters.read(connection).minus(before);
            assertThat(evicted.prepared()).isEqualTo(4);
            assertThat(evicted.executed()).isEqualTo(6);
            assertThat(evicted.closed()).isEqualTo(2);
        }
    }

    @Test
    void binaryRowsRoundTripTypedValues() throws SQLException {
        MysqlDataSource server = dataSource("useServerPrepStmts=true");
        try (Connection connection = server.getConnection()) {
            StatementCounters before = StatementCounters.read(connection);
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO price (sku, list_price, units, discount, active, barcode, note, valid_from)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)""")) {
                assertThat(insert.isWrapperFor(ServerPreparedStatement.class)).isTrue();
                ParameterMetaData parameters = insert.getParameterMetaData();
                assertThat(parameters.getParameterCount()).isEqualTo(8);

                bindRow(insert, "GEAR-12T", new BigDecimal("1234.5678"), MAX_UNSIGNED_BIGINT, 0.125, true,
                        new byte[] {0x04, 0x00, (byte) 0xFF}, null, LocalDate.of(2026, 10, 18));
                insert.addBatch();
                bindRow(insert, "GEAR-24T", new BigDecimal("-0.0001"), BigInteger.ZERO, -2.5, false,
                        new byte[0], "seasonal \u00fcber-sale", LocalDate.of(2027, 1, 1));
                insert.addBatch();
                assertThat(insert.executeBatch()).containsExactly(1, 1);
            }

            try (PreparedStatement select = connection.prepareStatement(SELECT_ROW)) {
                // The server describes the result columns when it prepares the statement.
                ResultSetMetaData columns = select.getMetaData();
                assertThat(columns.getColumnCount()).isEqualTo(8);
                assertThat(columns.getColumnLabel(3)).isEqualTo("units");
                assertThat(columns.getColumnClassName(3)).isEqualTo(BigInteger.class.getName());
                assertThat(columns.getScale(2)).isEqualTo(4);

                PriceRow gear12 = PriceRow.read(select, "GEAR-12T");
                assertThat(gear12.listPrice()).isEqualTo(new BigDecimal("1234.5678"));
                assertThat(gear12.units()).isEqualTo(MAX_UNSIGNED_BIGINT);
                assertThat(gear12.discount()).isEqualTo(0.125);
                assertThat(gear12.active()).isEqualTo(Boolean.TRUE);
                assertThat(gear12.barcode()).containsExactly(0x04, 0x00, 0xFF);
                assertThat(gear12.note()).isNull();
                assertThat(gear12.validFrom()).isEqualTo(LocalDate.of(2026, 10, 18));

                PriceRow gear24 = PriceRow.read(select, "GEAR-24T");
                assertThat(gear24.listPrice()).isEqualTo(new BigDecimal("-0.0001"));
                assertThat(gear24.units()).isEqualTo(BigInteger.ZERO);
                assertThat(gear24.active()).isEqualTo(Boolean.FALSE);
                assertThat(gear24.barcode()).isEmpty();
                assertThat(gear24.note()).isEqualTo("seasonal \u00fcber-sale");
            }
            // Two batch rows plus two lookups ran as COM_STMT_EXECUTE on server-side statements.
            StatementCounters used = StatementCounters.read(connection).minus(before);
            assertThat(used.prepared()).isPositive();
            assertThat(used.executed()).isGreaterThanOrEqualTo(4);
        }

        // Client-side emulation inlines the parameters into text queries yet reads the same values.
        MysqlDataSource client = dataSource("useServerPrepStmts=false");
        try (Connection connection = client.getConnection()) {
            StatementCounters before = StatementCounters.read(connection);
            try (PreparedStatement select = connection.prepareStatement(SELECT_ROW)) {
                assertThat(select.isWrapperFor(ServerPreparedStatement.class)).isFalse();
                assertThat(select.unwrap(JdbcPreparedStatement.class).getPreparedSql()).isEqualTo(SELECT_ROW);
                PriceRow gear12 = PriceRow.read(select, "GEAR-12T");
                assertThat(gear12.listPrice()).isEqualTo(new BigDecimal("1234.5678"));
                assertThat(gear12.units()).isEqualTo(MAX_UNSIGNED_BIGINT);
                assertThat(gear12.active()).isEqualTo(Boolean.TRUE);
                assertThat(gear12.barcode()).containsExactly(0x04, 0x00, 0xFF);
                assertThat(gear12.validFrom()).isEqualTo(LocalDate.of(2026, 10, 18));
            }
            StatementCounters used = StatementCounters.read(connection).minus(before);
            assertThat(used.prepared()).isZero();
            assertThat(used.executed()).isZero();
        }
    }

    @Test
    void unpreparableStatementsFallBackOrFailAtPrepare() throws SQLException {
        String broken = "SELEC list_price FROM price WHERE sku = ?";

        // By default the driver falls back to a client-side statement, which fails on execute;
        // executeQuery refuses it up front because the text does not start with SELECT.
        try (Connection connection = dataSource("useServerPrepStmts=true").getConnection();
             PreparedStatement emulated = connection.prepareStatement(broken)) {
            assertThat(emulated.isWrapperFor(ServerPreparedStatement.class)).isFalse();
            emulated.setString(1, "BOLT-M6");
            assertThatThrownBy(emulated::executeQuery)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("do not produce result sets");
            assertThatThrownBy(emulated::execute)
                    .isInstanceOfSatisfying(SQLSyntaxErrorException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(1064));
        }

        // Without emulation the server's syntax error surfaces from prepareStatement itself.
        try (Connection connection = dataSource("useServerPrepStmts=true&emulateUnsupportedPstmts=false")
                .getConnection()) {
            assertThatThrownBy(() -> connection.prepareStatement(broken))
                    .isInstanceOfSatisfying(SQLSyntaxErrorException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(1064));
            try (PreparedStatement lookup = connection.prepareStatement(LOOKUP)) {
                assertThat(listPrice(lookup, "NUT-M6")).isEqualByComparingTo("0.0800");
            }
        }
    }

    private static BigDecimal listPrice(PreparedStatement lookup, String sku) throws SQLException {
        lookup.setString(1, sku);
        try (ResultSet row = lookup.executeQuery()) {
            assertThat(row.next()).isTrue();
            BigDecimal price = row.getBigDecimal(1);
            assertThat(row.next()).isFalse();
            return price;
        }
    }

    private static void bindRow(PreparedStatement insert, String sku, BigDecimal listPrice, BigInteger units,
            double discount, boolean active, byte[] barcode, String note, LocalDate validFrom)
            throws SQLException {
        insert.setString(1, sku);
        insert.setBigDecimal(2, listPrice);
        // The binary protocol sends a BigInteger as a signed LONGLONG; DECIMAL keeps the full range.
        insert.setBigDecimal(3, new BigDecimal(units));
        insert.setDouble(4, discount);
        insert.setBoolean(5, active);
        insert.setBytes(6, barcode);
        if (note == null) {
            insert.setNull(7, Types.VARCHAR);
        } else {
            insert.setString(7, note);
        }
        insert.setObject(8, validFrom);
    }

    private static MysqlDataSource dataSource(String properties) {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL(baseUrl + "?" + properties);
        dataSource.setUser(USER);
        dataSource.setPassword(PASSWORD);
        return dataSource;
    }

    private static void createSchema() throws SQLException {
        try (Connection connection = dataSource("useServerPrepStmts=false").getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE price (
                        sku VARCHAR(32) PRIMARY KEY,
                        list_price DECIMAL(12, 4) NOT NULL,
                        units BIGINT UNSIGNED NOT NULL,
                        discount DOUBLE NOT NULL,
                        active TINYINT(1) NOT NULL,
                        barcode VARBINARY(16) NOT NULL,
                        note VARCHAR(64) NULL,
                        valid_from DATE NOT NULL
                    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
                    """);
            statement.execute("""
                    INSERT INTO price VALUES
                        ('BOLT-M6', 0.1250, 5000, 0, 1, x'01', NULL, '2026-01-01'),
                        ('NUT-M6', 0.0800, 4000, 0, 1, x'02', NULL, '2026-01-01'),
                        ('WASHER-M6', 0.0150, 9000, 0, 1, x'03', NULL, '2026-01-01')
                    """);
        }
    }

    /// Polls with a bounded deadline until the server accepts the application user.
    private static void awaitServer(MysqlDataSource dataSource, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        SQLException lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection connection = dataSource.getConnection();
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

    /// The session's server-side prepared statement counters, read over the text protocol.
    record StatementCounters(long prepared, long executed, long closed) {

        static StatementCounters read(Connection connection) throws SQLException {
            Map<String, Long> status = new HashMap<>();
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("""
                         SHOW SESSION STATUS WHERE Variable_name IN
                             ('Com_stmt_prepare', 'Com_stmt_execute', 'Com_stmt_close')""")) {
                while (rows.next()) {
                    status.put(rows.getString(1), rows.getLong(2));
                }
            }
            return new StatementCounters(status.get("Com_stmt_prepare"), status.get("Com_stmt_execute"),
                    status.get("Com_stmt_close"));
        }

        StatementCounters minus(StatementCounters earlier) {
            return new StatementCounters(prepared - earlier.prepared, executed - earlier.executed,
                    closed - earlier.closed);
        }
    }

    /// One catalog row as the application reads it.
    record PriceRow(BigDecimal listPrice, BigInteger units, double discount, Boolean active, byte[] barcode,
            String note, LocalDate validFrom) {

        static PriceRow read(PreparedStatement select, String sku) throws SQLException {
            select.setString(1, sku);
            try (ResultSet row = select.executeQuery()) {
                assertThat(row.next()).isTrue();
                PriceRow price = new PriceRow(
                        row.getObject("list_price", BigDecimal.class),
                        (BigInteger) row.getObject("units"),
                        row.getDouble("discount"),
                        (Boolean) row.getObject("active"),
                        row.getBytes("barcode"),
                        row.getString("note"),
                        row.getObject("valid_from", LocalDate.class));
                assertThat(row.getString("sku")).isEqualTo(sku);
                assertThat(row.next()).isFalse();
                return price;
            }
        }
    }
}
