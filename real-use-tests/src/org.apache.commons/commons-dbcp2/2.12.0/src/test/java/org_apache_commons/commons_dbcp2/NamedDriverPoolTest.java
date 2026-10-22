/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_dbcp2;

import org.apache.commons.dbcp2.DriverManagerConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDriver;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises PoolingDriver's named JDBC pools for a legacy DriverManager-based application.
/// An order writer and an order reader share a named pool without using a DataSource, and
/// shutting down the named pool makes it unavailable to subsequent requests.
public class NamedDriverPoolTest {

    @Test
    @Timeout(60)
    public void legacyClientsShareOrdersThroughANamedPool() throws Exception {
        DriverManagerConnectionFactory connections = new DriverManagerConnectionFactory(
                "jdbc:h2:mem:named_order_pool;DB_CLOSE_ON_EXIT=FALSE", "sa", "");
        PoolableConnectionFactory factory = new PoolableConnectionFactory(connections, null);
        factory.setDefaultAutoCommit(true);
        factory.setDefaultQueryTimeout(Duration.ofSeconds(10));
        GenericObjectPoolConfig<PoolableConnection> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(1);
        config.setMaxIdle(1);
        config.setMaxWait(Duration.ofSeconds(10));
        config.setJmxEnabled(false);

        PoolingDriver driver = new PoolingDriver();
        String poolName = "legacy-orders";
        String applicationUrl = PoolingDriver.URL_PREFIX + poolName;
        try (GenericObjectPool<PoolableConnection> pool = new GenericObjectPool<>(factory, config)) {
            factory.setPool(pool);
            driver.registerPool(poolName, pool);
            try {
                try (Connection connection = DriverManager.getConnection(applicationUrl)) {
                    try (Statement statement = connection.createStatement()) {
                        statement.executeUpdate("""
                                CREATE TABLE orders (
                                    order_id INTEGER PRIMARY KEY,
                                    customer VARCHAR(80) NOT NULL,
                                    total_cents INTEGER NOT NULL
                                )
                                """);
                    }
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO orders (order_id, customer, total_cents) VALUES (?, ?, ?)")) {
                        statement.setInt(1, 1042);
                        statement.setString(2, "Northwind Books");
                        statement.setInt(3, 4250);
                        assertEquals(1, statement.executeUpdate());
                    }
                }

                // Returning the writer's lease must leave the pooled database session alive.
                try (Connection connection = DriverManager.getConnection(applicationUrl);
                        PreparedStatement statement = connection.prepareStatement(
                                "SELECT customer, total_cents FROM orders WHERE order_id = ?")) {
                    statement.setInt(1, 1042);
                    try (ResultSet rows = statement.executeQuery()) {
                        assertTrue(rows.next());
                        assertEquals("Northwind Books", rows.getString("customer"));
                        assertEquals(4250, rows.getInt("total_cents"));
                        assertFalse(rows.next());
                    }
                }
            } finally {
                driver.closePool(poolName);
            }
            assertThrows(SQLException.class, () -> DriverManager.getConnection(applicationUrl));
        }
    }
}
