/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.hikari;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises HikariCP's pooled transaction cleanup and connection-state reset subsystem.
/// An order service commits successful orders and leaves an interrupted order uncommitted;
/// the next request must receive a clean connection rather than inherit that transaction.
public class PooledOrderTransactionTest {
    @Test
    @Timeout(60)
    public void commitsSuccessfulOrdersAndDiscardsAbandonedWorkBeforeReuse() throws SQLException {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:pooled_order_transactions");

        HikariConfig config = new HikariConfig();
        config.setDataSource(database);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setAutoCommit(true);
        config.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
        config.setConnectionTimeout(10000);
        config.setValidationTimeout(10000);

        try (HikariDataSource pool = new HikariDataSource(config)) {
            try (Connection connection = pool.getConnection();
                            Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE purchase_orders (
                            order_id INTEGER PRIMARY KEY,
                            customer VARCHAR(100) NOT NULL
                        )
                        """);
            }

            try (Connection connection = pool.getConnection()) {
                connection.setAutoCommit(false);
                insertOrder(connection, 101, "Alice");
                connection.commit();
            }

            // An interrupted request closes its connection without committing its order.
            try (Connection connection = pool.getConnection()) {
                connection.setAutoCommit(false);
                connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                insertOrder(connection, 102, "Abandoned checkout");
            }

            // A one-connection pool makes reuse deterministic, without inspecting internals.
            try (Connection connection = pool.getConnection()) {
                assertTrue(connection.getAutoCommit());
                assertEquals(Connection.TRANSACTION_READ_COMMITTED, connection.getTransactionIsolation());
                insertOrder(connection, 103, "Carol");
            }

            try (Connection connection = pool.getConnection();
                            PreparedStatement query = connection.prepareStatement(
                                    "SELECT order_id, customer FROM purchase_orders ORDER BY order_id");
                            ResultSet orders = query.executeQuery()) {
                assertTrue(orders.next());
                assertEquals(101, orders.getInt("order_id"));
                assertEquals("Alice", orders.getString("customer"));
                assertTrue(orders.next());
                assertEquals(103, orders.getInt("order_id"));
                assertEquals("Carol", orders.getString("customer"));
                assertFalse(orders.next(), "The pool must roll back the abandoned checkout before reuse");
            }
        }
    }

    private static void insertOrder(Connection connection, int orderId, String customer) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO purchase_orders (order_id, customer) VALUES (?, ?)")) {
            insert.setInt(1, orderId);
            insert.setString(2, customer);
            assertEquals(1, insert.executeUpdate());
        }
    }
}
