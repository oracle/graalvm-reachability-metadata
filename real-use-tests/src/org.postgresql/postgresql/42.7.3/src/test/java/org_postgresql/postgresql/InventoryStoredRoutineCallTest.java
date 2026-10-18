/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_postgresql.postgresql;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.postgresql.PGProperty;
import org.postgresql.ds.PGSimpleDataSource;
import org.postgresql.util.PSQLException;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Calls server-side stored functions and procedures through JDBC `CallableStatement`, covering
 * the `{call}` escape syntax, OUT and INOUT parameters, scalar returns and `refcursor` results.
 *
 * A warehouse service keeps its stock rules in PL/pgSQL: it reserves stock through a function
 * with OUT parameters, prices an order through an INOUT parameter, reads a stock level through a
 * `{? = call}` return value, lists low-stock items through a returned cursor, and restocks through
 * a procedure invoked with `CALL` on a connection configured with `escapeSyntaxCallMode`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InventoryStoredRoutineCallTest {

    private static final String POSTGRES_IMAGE = "postgres:18-alpine";

    private static final String DATABASE = "warehouse";

    private static final String USER = "stockkeeper";

    private static final String PASSWORD = "s3cret";

    private final String containerName = "real-use-postgresql-" + UUID.randomUUID();

    private PGSimpleDataSource dataSource;

    private String jdbcUrl;

    @BeforeAll
    void startDatabase() throws Exception {
        int port = freePort();
        Process run = new ProcessBuilder("docker", "run", "-d", "--rm", "--name", containerName,
                "-p", port + ":5432",
                "-e", "POSTGRES_DB=" + DATABASE,
                "-e", "POSTGRES_USER=" + USER,
                "-e", "POSTGRES_PASSWORD=" + PASSWORD,
                POSTGRES_IMAGE)
                .inheritIO()
                .start();
        assertThat(run.waitFor(120, TimeUnit.SECONDS)).isTrue();
        assertThat(run.exitValue()).isZero();

        dataSource = new PGSimpleDataSource();
        dataSource.setServerNames(new String[] {"127.0.0.1"});
        dataSource.setPortNumbers(new int[] {port});
        dataSource.setDatabaseName(DATABASE);
        dataSource.setUser(USER);
        dataSource.setPassword(PASSWORD);
        dataSource.setConnectTimeout(5);
        jdbcUrl = "jdbc:postgresql://127.0.0.1:" + port + "/" + DATABASE;
        awaitDatabase();

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE stock (
                        sku      text PRIMARY KEY,
                        on_hand  integer NOT NULL CHECK (on_hand >= 0),
                        reorder  integer NOT NULL
                    )""");
            statement.execute("""
                    INSERT INTO stock (sku, on_hand, reorder) VALUES
                        ('bolt-m6', 120, 50),
                        ('nut-m6', 30, 40),
                        ('washer-m6', 8, 25)""");
            statement.execute("""
                    CREATE FUNCTION reserve_stock(p_sku text, p_quantity integer,
                                                  OUT remaining integer, OUT reserved boolean)
                    LANGUAGE plpgsql AS $$
                    BEGIN
                        UPDATE stock SET on_hand = on_hand - p_quantity
                        WHERE sku = p_sku AND on_hand >= p_quantity
                        RETURNING on_hand INTO remaining;
                        reserved := FOUND;
                        IF NOT reserved THEN
                            SELECT on_hand INTO remaining FROM stock WHERE sku = p_sku;
                        END IF;
                    END
                    $$""");
            statement.execute("""
                    CREATE FUNCTION apply_discount(INOUT price numeric, p_percent integer)
                    LANGUAGE plpgsql AS $$
                    BEGIN
                        IF p_percent NOT BETWEEN 0 AND 100 THEN
                            RAISE EXCEPTION 'invalid discount %', p_percent USING ERRCODE = '22023';
                        END IF;
                        price := round(price * (100 - p_percent) / 100, 2);
                    END
                    $$""");
            statement.execute("""
                    CREATE FUNCTION stock_level(p_sku text) RETURNS integer
                    LANGUAGE sql AS 'SELECT on_hand FROM stock WHERE sku = p_sku'""");
            statement.execute("""
                    CREATE FUNCTION low_stock() RETURNS refcursor
                    LANGUAGE plpgsql AS $$
                    DECLARE
                        items refcursor;
                    BEGIN
                        OPEN items FOR
                            SELECT sku, reorder - on_hand AS shortfall FROM stock
                            WHERE on_hand < reorder ORDER BY sku;
                        RETURN items;
                    END
                    $$""");
            statement.execute("""
                    CREATE PROCEDURE restock(p_sku text, p_quantity integer, INOUT new_level integer)
                    LANGUAGE plpgsql AS $$
                    BEGIN
                        UPDATE stock SET on_hand = on_hand + p_quantity WHERE sku = p_sku
                        RETURNING on_hand INTO new_level;
                    END
                    $$""");
        }
    }

    @AfterAll
    void stopDatabase() throws Exception {
        Process remove = new ProcessBuilder("docker", "rm", "-f", containerName).inheritIO().start();
        remove.waitFor(60, TimeUnit.SECONDS);
    }

    @Test
    void runsWarehouseRulesThroughStoredRoutines() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            try (CallableStatement reserve = connection.prepareCall("{call reserve_stock(?, ?, ?, ?)}")) {
                reserve.registerOutParameter(3, Types.INTEGER);
                reserve.registerOutParameter(4, Types.BOOLEAN);

                reserve.setString(1, "bolt-m6");
                reserve.setInt(2, 45);
                reserve.execute();
                assertThat(reserve.getInt(3)).isEqualTo(75);
                assertThat(reserve.getBoolean(4)).isTrue();

                reserve.setString(1, "washer-m6");
                reserve.setInt(2, 10);
                reserve.execute();
                assertThat(reserve.getInt(3)).isEqualTo(8);
                assertThat(reserve.getBoolean(4)).isFalse();
                assertThat(reserve.wasNull()).isFalse();
            }

            try (CallableStatement level = connection.prepareCall("{? = call stock_level(?)}")) {
                level.registerOutParameter(1, Types.INTEGER);
                level.setString(2, "bolt-m6");
                level.execute();
                assertThat(level.getInt(1)).isEqualTo(75);

                level.setString(2, "unknown-sku");
                level.execute();
                assertThat(level.getObject(1)).isNull();
                assertThat(level.wasNull()).isTrue();
            }

            try (CallableStatement discount = connection.prepareCall("{call apply_discount(?, ?)}")) {
                discount.registerOutParameter(1, Types.NUMERIC);
                discount.setBigDecimal(1, new BigDecimal("249.90"));
                discount.setInt(2, 15);
                discount.execute();
                assertThat(discount.getBigDecimal(1)).isEqualByComparingTo("212.42");

                discount.setBigDecimal(1, new BigDecimal("10.00"));
                discount.setInt(2, 120);
                assertThatThrownBy(discount::execute)
                        .isInstanceOfSatisfying(PSQLException.class, failure -> {
                            assertThat(failure.getSQLState()).isEqualTo("22023");
                            assertThat(failure.getServerErrorMessage().getMessage())
                                    .isEqualTo("invalid discount 120");
                        });
            }

            connection.setAutoCommit(false);
            try (CallableStatement lowStock = connection.prepareCall("{? = call low_stock()}")) {
                lowStock.registerOutParameter(1, Types.REF_CURSOR);
                lowStock.execute();
                Map<String, Integer> shortfalls = new LinkedHashMap<>();
                try (ResultSet items = lowStock.getObject(1, ResultSet.class)) {
                    while (items.next()) {
                        shortfalls.put(items.getString("sku"), items.getInt("shortfall"));
                    }
                }
                assertThat(shortfalls).containsExactly(Map.entry("nut-m6", 10), Map.entry("washer-m6", 17));
            }
            connection.commit();
        }

        Properties properties = new Properties();
        PGProperty.USER.set(properties, USER);
        PGProperty.PASSWORD.set(properties, PASSWORD);
        PGProperty.ESCAPE_SYNTAX_CALL_MODE.set(properties, "callIfNoReturn");
        try (Connection connection = DriverManager.getConnection(jdbcUrl, properties);
                CallableStatement restock = connection.prepareCall("{call restock(?, ?, ?)}")) {
            restock.setString(1, "washer-m6");
            restock.setInt(2, 40);
            restock.setNull(3, Types.INTEGER);
            restock.registerOutParameter(3, Types.INTEGER);
            restock.execute();
            assertThat(restock.getInt(3)).isEqualTo(48);
        }

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT sku, on_hand FROM stock ORDER BY sku")) {
            Map<String, Integer> onHand = new LinkedHashMap<>();
            while (rows.next()) {
                onHand.put(rows.getString("sku"), rows.getInt("on_hand"));
            }
            assertThat(onHand).containsExactly(
                    Map.entry("bolt-m6", 75), Map.entry("nut-m6", 30), Map.entry("washer-m6", 48));
        }
    }

    private void awaitDatabase() throws Exception {
        long deadline = System.currentTimeMillis() + 120_000L;
        SQLException lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection probe = dataSource.getConnection(); Statement statement = probe.createStatement()) {
                statement.execute("SELECT 1");
                return;
            } catch (SQLException e) {
                lastFailure = e;
                Thread.sleep(500L);
            }
        }
        throw new IllegalStateException("PostgreSQL did not accept connections in " + containerName, lastFailure);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
