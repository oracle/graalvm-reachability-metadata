/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_dbcp2;

import org.apache.commons.dbcp2.DataSourceConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDataSource;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.h2.jdbcx.JdbcDataSource;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises PoolingDataSource admission control with a bounded, nonblocking connection pool.
/// A checkout service rejects excess work while its only connection is occupied, then accepts
/// the rejected order on retry once the active checkout returns its lease.
public class CheckoutPoolAdmissionTest {

    @Test
    @Timeout(60)
    public void overloadedCheckoutCanBeRetriedAfterTheActiveRequestCompletes() throws Exception {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:checkout_pool_admission");
        database.setUser("sa");
        database.setPassword("");

        // Keep the database alive independently of the application's bounded pool.
        try (Connection administrator = database.getConnection()) {
            try (Statement setup = administrator.createStatement()) {
                setup.executeUpdate("CREATE TABLE orders (id INTEGER PRIMARY KEY, total_cents INTEGER NOT NULL)");
            }

            DataSourceConnectionFactory connections = new DataSourceConnectionFactory(database);
            PoolableConnectionFactory factory = new PoolableConnectionFactory(connections, null);
            factory.setDefaultAutoCommit(true);
            factory.setDefaultQueryTimeout(Duration.ofSeconds(10));
            GenericObjectPoolConfig<PoolableConnection> configuration = new GenericObjectPoolConfig<>();
            configuration.setMaxTotal(1);
            configuration.setMaxIdle(1);
            configuration.setBlockWhenExhausted(false);
            configuration.setMaxWait(Duration.ofSeconds(10));
            configuration.setJmxEnabled(false);

            try (GenericObjectPool<PoolableConnection> pool = new GenericObjectPool<>(factory, configuration)) {
                factory.setPool(pool);
                try (PoolingDataSource<PoolableConnection> checkouts = new PoolingDataSource<>(pool)) {
                    try (Connection activeCheckout = checkouts.getConnection()) {
                        activeCheckout.setAutoCommit(false);
                        insertOrder(activeCheckout, 101, 3200);

                        // Fail fast instead of queuing another checkout behind a busy database.
                        assertThrows(SQLException.class, () -> submitOrder(checkouts, 102, 1800));

                        // Rejecting another request must not invalidate the accepted transaction.
                        insertOrder(activeCheckout, 103, 950);
                        activeCheckout.commit();
                    }

                    submitOrder(checkouts, 102, 1800);

                    try (Connection report = checkouts.getConnection();
                            Statement query = report.createStatement();
                            ResultSet rows = query.executeQuery("SELECT id, total_cents FROM orders ORDER BY id")) {
                        assertTrue(rows.next());
                        assertEquals(101, rows.getInt("id"));
                        assertEquals(3200, rows.getInt("total_cents"));
                        assertTrue(rows.next());
                        assertEquals(102, rows.getInt("id"));
                        assertEquals(1800, rows.getInt("total_cents"));
                        assertTrue(rows.next());
                        assertEquals(103, rows.getInt("id"));
                        assertEquals(950, rows.getInt("total_cents"));
                        assertFalse(rows.next(), "The rejected attempt must not create a duplicate order");
                    }
                }
            }
        }
    }

    private static void submitOrder(PoolingDataSource<PoolableConnection> checkouts, int id, int totalCents)
            throws SQLException {
        try (Connection connection = checkouts.getConnection()) {
            insertOrder(connection, id, totalCents);
        }
    }

    private static void insertOrder(Connection connection, int id, int totalCents) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO orders VALUES (?, ?)")) {
            insert.setInt(1, id);
            insert.setInt(2, totalCents);
            assertEquals(1, insert.executeUpdate());
        }
    }
}
