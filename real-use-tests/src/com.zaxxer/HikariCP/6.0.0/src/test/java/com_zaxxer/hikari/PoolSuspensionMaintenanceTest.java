/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.hikari;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises HikariCP's pool suspension and resumption subsystem for application maintenance.
/// An administrator pauses new database work while a price update is queued, then resumes
/// the pool so the waiting request completes without being rejected or losing its update.
public class PoolSuspensionMaintenanceTest {
    @Test
    @Timeout(60)
    public void queuesPriceUpdateUntilMaintenanceResumesThePool() throws Exception {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:suspended_price_updates");

        HikariConfig config = new HikariConfig();
        config.setDataSource(database);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setAllowPoolSuspension(true);
        config.setConnectionTimeout(10000);
        config.setValidationTimeout(10000);

        // The maintenance connection observes the database independently of the paused pool.
        try (Connection maintenance = database.getConnection();
                        HikariDataSource pool = new HikariDataSource(config)) {
            try (Statement schema = maintenance.createStatement()) {
                schema.execute("CREATE TABLE prices (sku VARCHAR(30) PRIMARY KEY, cents INTEGER NOT NULL)");
                schema.execute("INSERT INTO prices VALUES ('COFFEE-250G', 1200)");
            }

            HikariPoolMXBean administration = pool.getHikariPoolMXBean();
            ExecutorService requests = Executors.newSingleThreadExecutor();
            CountDownLatch requestStarted = new CountDownLatch(1);
            administration.suspendPool();
            try {
                Future<Integer> update = requests.submit(() -> {
                    requestStarted.countDown();
                    try (Connection request = pool.getConnection();
                                    PreparedStatement change = request.prepareStatement(
                                                    "UPDATE prices SET cents = ? WHERE sku = ?")) {
                        change.setInt(1, 1350);
                        change.setString(2, "COFFEE-250G");
                        return change.executeUpdate();
                    }
                });

                assertTrue(requestStarted.await(10, TimeUnit.SECONDS), "The application request must start");
                assertFalse(update.isDone(), "A suspended pool must hold, not reject, a new request");
                assertPrice(maintenance, 1200);

                administration.resumePool();
                assertEquals(1, update.get(10, TimeUnit.SECONDS).intValue());
                try (Connection confirmation = pool.getConnection()) {
                    assertPrice(confirmation, 1350);
                }
            } finally {
                // Resume even after an assertion failure so a borrower cannot remain suspended.
                administration.resumePool();
                requests.shutdownNow();
                assertTrue(requests.awaitTermination(10, TimeUnit.SECONDS), "Application worker must stop");
            }
        }
    }

    private static void assertPrice(Connection connection, int expectedCents) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT cents FROM prices WHERE sku = ?")) {
            query.setString(1, "COFFEE-250G");
            try (ResultSet price = query.executeQuery()) {
                assertTrue(price.next());
                assertEquals(expectedCents, price.getInt("cents"));
                assertFalse(price.next());
            }
        }
    }
}
