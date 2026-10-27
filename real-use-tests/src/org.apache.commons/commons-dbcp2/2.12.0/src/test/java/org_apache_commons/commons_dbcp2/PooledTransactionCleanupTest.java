/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_dbcp2;

import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises BasicDataSource transaction cleanup when application connections return to the pool.
/// A transfer service commits one payment but abandons a second transaction before returning its
/// connection. The next request must see only the committed payment and a clean auto-commit session.
public class PooledTransactionCleanupTest {

    @Test
    @Timeout(60)
    public void returningAnUnfinishedTransferDoesNotLeakWorkToTheNextRequest() throws SQLException {
        try (BasicDataSource dataSource = new BasicDataSource()) {
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:pooled_transfer;DB_CLOSE_ON_EXIT=FALSE");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            dataSource.setMaxTotal(1);
            dataSource.setMaxIdle(1);
            dataSource.setMaxWait(Duration.ofSeconds(10));
            dataSource.setDefaultAutoCommit(true);
            dataSource.setRollbackOnReturn(true);
            dataSource.setAutoCommitOnReturn(true);

            try (Connection connection = dataSource.getConnection()) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("""
                            CREATE TABLE accounts (
                                id INTEGER PRIMARY KEY,
                                balance INTEGER NOT NULL CHECK (balance >= 0)
                            )
                            """);
                    statement.executeUpdate("INSERT INTO accounts VALUES (1, 1000), (2, 200)");
                }
                connection.setAutoCommit(false);
                transfer(connection, 150);
                connection.commit();
            }

            try (Connection connection = dataSource.getConnection()) {
                assertTrue(connection.getAutoCommit(), "Each request starts in auto-commit mode");
                assertBalances(connection, 850, 350);
                connection.setAutoCommit(false);
                transfer(connection, 75);
                assertBalances(connection, 775, 425);
                // The request exits without committing; closing returns its lease to DBCP.
            }

            try (Connection connection = dataSource.getConnection()) {
                assertTrue(connection.getAutoCommit(), "An abandoned transaction must not leak state");
                assertBalances(connection, 850, 350);
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE accounts SET balance = balance + ? WHERE id = ?")) {
                    statement.setInt(1, 20);
                    statement.setInt(2, 2);
                    assertEquals(1, statement.executeUpdate());
                }
            }

            try (Connection connection = dataSource.getConnection()) {
                assertBalances(connection, 850, 370);
            }
        }
    }

    private static void transfer(Connection connection, int amount) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE accounts SET balance = balance + ? WHERE id = ?")) {
            statement.setInt(1, -amount);
            statement.setInt(2, 1);
            assertEquals(1, statement.executeUpdate());
            statement.setInt(1, amount);
            statement.setInt(2, 2);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void assertBalances(Connection connection, int sender, int recipient) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT id, balance FROM accounts ORDER BY id")) {
            assertTrue(rows.next());
            assertEquals(1, rows.getInt("id"));
            assertEquals(sender, rows.getInt("balance"));
            assertTrue(rows.next());
            assertEquals(2, rows.getInt("id"));
            assertEquals(recipient, rows.getInt("balance"));
            assertFalse(rows.next());
        }
    }
}
