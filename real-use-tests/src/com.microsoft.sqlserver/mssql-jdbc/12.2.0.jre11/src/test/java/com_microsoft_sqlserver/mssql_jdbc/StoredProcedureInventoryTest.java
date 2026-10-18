/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_microsoft_sqlserver.mssql_jdbc;

import com.microsoft.sqlserver.jdbc.SQLServerCallableStatement;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.microsoft.sqlserver.jdbc.SQLServerError;
import com.microsoft.sqlserver.jdbc.SQLServerException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Calls Transact-SQL stored procedures through JDBC callable statements with
/// return codes, OUTPUT parameters, result sets, and server-raised errors.
///
/// The scenario is an inventory service whose business rules live in the
/// database: it reserves stock, reports low-stock items, and reacts to the
/// errors a procedure throws, against a real SQL Server.
public class StoredProcedureInventoryTest {

    private static final String IMAGE = "mcr.microsoft.com/mssql/server:2022-CU14-ubuntu-22.04";

    private static final String PASSWORD = "Inventory#2024";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(180);

    private static final String CONTAINER = "real-use-mssql-sproc-" + UUID.randomUUID();

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
        dataSource.setApplicationName("inventory-service");
        dataSource.setLoginTimeout(5);
        awaitServer();
        createSchema();
    }

    @AfterAll
    static void stopServer() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @Test
    void reservingStockReturnsStatusAndRemainingQuantity() throws Exception {
        try (Connection connection = dataSource.getConnection();
                CallableStatement reserve = connection.prepareCall("{? = call dbo.reserve_stock(?, ?, ?)}")) {
            reserve.registerOutParameter(1, Types.INTEGER);
            reserve.setString(2, "SKU-KEYBOARD");
            reserve.setInt(3, 4);
            reserve.registerOutParameter(4, Types.INTEGER);
            reserve.execute();

            assertThat(reserve.getInt(1)).isZero();
            assertThat(reserve.getInt(4)).isEqualTo(16);

            reserve.setString(2, "SKU-UNKNOWN");
            reserve.setInt(3, 1);
            reserve.execute();

            assertThat(reserve.getInt(1)).isEqualTo(1);
            reserve.getInt(4);
            assertThat(reserve.wasNull()).isTrue();
        }

        assertThat(quantityOf("SKU-KEYBOARD")).isEqualTo(16);
    }

    @Test
    void namedParametersBindByProcedureParameterName() throws Exception {
        try (Connection connection = dataSource.getConnection();
                CallableStatement price = connection.prepareCall("{call dbo.price_order(?, ?, ?)}")) {
            SQLServerCallableStatement sqlServerCall = price.unwrap(SQLServerCallableStatement.class);
            sqlServerCall.setString("sku", "SKU-MONITOR");
            sqlServerCall.setInt("quantity", 3);
            sqlServerCall.registerOutParameter("total", Types.DECIMAL, 2);
            sqlServerCall.execute();

            assertThat(sqlServerCall.getBigDecimal("total")).isEqualByComparingTo(new BigDecimal("599.97"));
        }
    }

    @Test
    void reportProcedureStreamsRowsBeforeItsOutputParameter() throws Exception {
        Map<String, Integer> lowStock = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
                CallableStatement report = connection.prepareCall("{call dbo.low_stock_report(?, ?)}")) {
            report.setInt(1, 10);
            report.registerOutParameter(2, Types.INTEGER);
            try (ResultSet rows = report.executeQuery()) {
                while (rows.next()) {
                    lowStock.put(rows.getString("sku"), rows.getInt("quantity"));
                }
            }

            assertThat(report.getInt(2)).isEqualTo(lowStock.size());
        }

        assertThat(lowStock).containsExactly(Map.entry("SKU-CABLE", 0), Map.entry("SKU-MONITOR", 7));
    }

    @Test
    void procedureErrorsSurfaceAsSqlServerExceptions() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (CallableStatement reserve = connection.prepareCall("{? = call dbo.reserve_stock(?, ?, ?)}")) {
                reserve.registerOutParameter(1, Types.INTEGER);
                reserve.setString(2, "SKU-CABLE");
                reserve.setInt(3, 2);
                reserve.registerOutParameter(4, Types.INTEGER);

                assertThatThrownBy(reserve::execute)
                        .isInstanceOfSatisfying(SQLServerException.class, failure -> {
                            assertThat(failure.getErrorCode()).isEqualTo(50001);
                            assertThat(failure.getMessage()).isEqualTo("Insufficient stock for SKU-CABLE");
                            SQLServerError error = failure.getSQLServerError();
                            assertThat(error.getErrorNumber()).isEqualTo(50001);
                            assertThat(error.getErrorState()).isEqualTo(3);
                            assertThat(error.getProcedureName()).isEqualTo("dbo.reserve_stock");
                        });
            }
            connection.rollback();
        }

        assertThat(quantityOf("SKU-CABLE")).isZero();
    }

    private static int quantityOf(String sku) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement lookup = connection.prepareStatement("SELECT quantity FROM dbo.inventory WHERE sku = ?")) {
            lookup.setString(1, sku);
            try (ResultSet row = lookup.executeQuery()) {
                assertThat(row.next()).isTrue();
                return row.getInt(1);
            }
        }
    }

    private static void createSchema() throws SQLException {
        List<String> ddl = List.of(
                """
                CREATE TABLE dbo.inventory (
                    sku NVARCHAR(32) PRIMARY KEY,
                    quantity INT NOT NULL,
                    unit_price DECIMAL(10, 2) NOT NULL)
                """,
                """
                INSERT INTO dbo.inventory (sku, quantity, unit_price) VALUES
                    ('SKU-KEYBOARD', 20, 49.90),
                    ('SKU-MONITOR', 7, 199.99),
                    ('SKU-CABLE', 0, 4.50)
                """,
                """
                CREATE PROCEDURE dbo.reserve_stock
                    @sku NVARCHAR(32), @quantity INT, @remaining INT OUTPUT
                AS
                BEGIN
                    SET NOCOUNT ON;
                    DECLARE @available INT;
                    SELECT @available = quantity FROM dbo.inventory WITH (UPDLOCK) WHERE sku = @sku;
                    IF @available IS NULL
                        RETURN 1;
                    IF @available < @quantity
                    BEGIN
                        DECLARE @message NVARCHAR(200) = CONCAT('Insufficient stock for ', @sku);
                        THROW 50001, @message, 3;
                    END
                    UPDATE dbo.inventory SET @remaining = quantity = quantity - @quantity WHERE sku = @sku;
                    RETURN 0;
                END
                """,
                """
                CREATE PROCEDURE dbo.price_order
                    @sku NVARCHAR(32), @quantity INT, @total DECIMAL(12, 2) OUTPUT
                AS
                BEGIN
                    SET NOCOUNT ON;
                    SELECT @total = unit_price * @quantity FROM dbo.inventory WHERE sku = @sku;
                END
                """,
                """
                CREATE PROCEDURE dbo.low_stock_report
                    @threshold INT, @count INT OUTPUT
                AS
                BEGIN
                    SET NOCOUNT ON;
                    SELECT sku, quantity FROM dbo.inventory WHERE quantity < @threshold ORDER BY sku;
                    SET @count = @@ROWCOUNT;
                END
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
}
