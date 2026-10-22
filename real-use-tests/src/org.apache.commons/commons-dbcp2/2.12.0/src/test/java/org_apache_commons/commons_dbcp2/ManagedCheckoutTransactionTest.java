/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_dbcp2;

import org.apache.commons.dbcp2.managed.BasicManagedDataSource;
import org.apache.geronimo.transaction.manager.TransactionManagerImpl;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.transaction.RollbackException;
import javax.transaction.Status;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises DBCP managed XA connection enlistment in a JTA checkout spanning two databases.
/// Order and inventory services close their connection leases before the transaction completes;
/// the transaction manager must still commit or roll back both services' changes together.
public class ManagedCheckoutTransactionTest {

    @Test
    @Timeout(60)
    public void checkoutCommitsBothDatabasesAndCancelledCheckoutChangesNeither() throws Exception {
        TransactionManagerImpl transactions = new TransactionManagerImpl(30);
        JdbcDataSource ordersDatabase = database("jdbc:h2:mem:managed_checkout_orders");
        JdbcDataSource stockDatabase = database("jdbc:h2:mem:managed_checkout_stock");

        // Independent observer connections also keep both in-memory databases alive.
        try (Connection ordersObserver = ordersDatabase.getConnection();
                Connection stockObserver = stockDatabase.getConnection()) {
            try (Statement orders = ordersObserver.createStatement();
                    Statement stock = stockObserver.createStatement()) {
                orders.executeUpdate("CREATE TABLE orders (id INTEGER PRIMARY KEY, quantity INTEGER NOT NULL)");
                stock.executeUpdate("CREATE TABLE inventory (sku VARCHAR(40) PRIMARY KEY, quantity INTEGER NOT NULL)");
                stock.executeUpdate("INSERT INTO inventory VALUES ('notebook', 20)");
            }

            try (BasicManagedDataSource orders = managedPool(ordersDatabase, transactions);
                    BasicManagedDataSource stock = managedPool(stockDatabase, transactions)) {
                try {
                    transactions.begin();
                    recordOrder(orders, 101, 3);
                    reserveStock(stock, 3);
                    transactions.commit();
                    assertCheckoutState(ordersObserver, stockObserver, 17);

                    transactions.begin();
                    recordOrder(orders, 102, 4);
                    reserveStock(stock, 4);
                    // A later checkout step rejects the purchase, after both services have run.
                    transactions.setRollbackOnly();
                    assertThrows(RollbackException.class, transactions::commit);
                    assertCheckoutState(ordersObserver, stockObserver, 17);
                } finally {
                    if (transactions.getStatus() != Status.STATUS_NO_TRANSACTION) {
                        transactions.rollback();
                    }
                }
            }
        }
    }

    private static JdbcDataSource database(String url) {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL(url);
        database.setUser("sa");
        database.setPassword("");
        return database;
    }

    private static BasicManagedDataSource managedPool(JdbcDataSource database,
            TransactionManagerImpl transactions) {
        BasicManagedDataSource pool = new BasicManagedDataSource();
        pool.setTransactionManager(transactions);
        pool.setXaDataSourceInstance(database);
        pool.setUsername("sa");
        pool.setPassword("");
        pool.setMaxTotal(2);
        pool.setMaxIdle(2);
        pool.setMaxWait(Duration.ofSeconds(10));
        return pool;
    }

    private static void recordOrder(BasicManagedDataSource orders, int orderId, int quantity) throws SQLException {
        try (Connection connection = orders.getConnection();
                PreparedStatement insert = connection.prepareStatement("INSERT INTO orders VALUES (?, ?)")) {
            insert.setInt(1, orderId);
            insert.setInt(2, quantity);
            assertEquals(1, insert.executeUpdate());
        }
    }

    private static void reserveStock(BasicManagedDataSource stock, int quantity) throws SQLException {
        try (Connection connection = stock.getConnection();
                PreparedStatement update = connection.prepareStatement(
                        "UPDATE inventory SET quantity = quantity - ? WHERE sku = ? AND quantity >= ?")) {
            update.setInt(1, quantity);
            update.setString(2, "notebook");
            update.setInt(3, quantity);
            assertEquals(1, update.executeUpdate());
        }
    }

    private static void assertCheckoutState(Connection orders, Connection stock, int expectedStock)
            throws SQLException {
        try (Statement query = orders.createStatement();
                ResultSet rows = query.executeQuery("SELECT id, quantity FROM orders ORDER BY id")) {
            assertTrue(rows.next());
            assertEquals(101, rows.getInt("id"));
            assertEquals(3, rows.getInt("quantity"));
            assertFalse(rows.next(), "A cancelled checkout must not leave an order behind");
        }
        try (Statement query = stock.createStatement();
                ResultSet rows = query.executeQuery("SELECT quantity FROM inventory WHERE sku = 'notebook'")) {
            assertTrue(rows.next());
            assertEquals(expectedStock, rows.getInt("quantity"));
            assertFalse(rows.next());
        }
    }
}
