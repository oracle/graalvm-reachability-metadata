/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.hikari;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.SQLExceptionOverride;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises HikariCP's SQL-exception override subsystem for retryable database lock timeouts.
/// A stock reservation service preserves a healthy session after lock contention, then retries
/// the reservation after the competing transaction commits instead of discarding the connection.
public class LockTimeoutRetryPolicyTest {
    @Test
    @Timeout(60)
    public void retriesContendedReservationOnTheSameHealthySession() throws SQLException {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:lock_timeout_retry_policy;LOCK_TIMEOUT=10000");

        try (Connection migration = database.getConnection()) {
            try (Statement schema = migration.createStatement()) {
                schema.execute("CREATE TABLE stock (sku VARCHAR(30) PRIMARY KEY, available INTEGER NOT NULL)");
                schema.execute("INSERT INTO stock VALUES ('TEA-100G', 10)");
            }

            HikariConfig config = new HikariConfig();
            config.setDataSource(database);
            config.setMaximumPoolSize(2);
            config.setMinimumIdle(0);
            config.setAutoCommit(false);
            config.setMaxLifetime(0);
            config.setKeepaliveTime(0);
            config.setConnectionTimeout(10000);
            config.setValidationTimeout(10000);
            config.setExceptionOverride(new LockContentionPolicy());

            try (HikariDataSource pool = new HikariDataSource(config);
                            Connection competingReservation = pool.getConnection();
                            Connection retryingReservation = pool.getConnection()) {
                int reservationSession = sessionId(retryingReservation);

                // Keep the first transaction's row lock until the second request times out.
                // This ordering forces contention without sleeps or thread scheduling races.
                reserve(competingReservation, 2);
                SQLTimeoutException contention = assertThrows(SQLTimeoutException.class,
                                () -> reserve(retryingReservation, 3));
                assertEquals(50200, contention.getErrorCode());
                assertFalse(retryingReservation.isClosed(),
                                "The exception policy must keep a session with a retryable lock timeout open");
                retryingReservation.rollback();

                competingReservation.commit();

                assertEquals(reservationSession, sessionId(retryingReservation),
                                "The application must be able to retry without losing its database session");
                assertAvailable(retryingReservation, 8);
                reserve(retryingReservation, 3);
                retryingReservation.commit();

                // An independent reader sees both reservations, with neither lost nor applied twice.
                assertAvailable(migration, 5);
            }
        }
    }

    private static void reserve(Connection connection, int quantity) throws SQLException {
        try (PreparedStatement reservation = connection.prepareStatement("""
                UPDATE stock SET available = available - ?
                WHERE sku = ? AND available >= ?
                """)) {
            reservation.setQueryTimeout(10);
            reservation.setInt(1, quantity);
            reservation.setString(2, "TEA-100G");
            reservation.setInt(3, quantity);
            assertEquals(1, reservation.executeUpdate());
        }
    }

    private static int sessionId(Connection connection) throws SQLException {
        try (Statement query = connection.createStatement();
                        ResultSet session = query.executeQuery("SELECT SESSION_ID()")) {
            assertTrue(session.next());
            return session.getInt(1);
        }
    }

    private static void assertAvailable(Connection connection, int expected) throws SQLException {
        try (Statement query = connection.createStatement();
                        ResultSet stock = query.executeQuery("SELECT available FROM stock WHERE sku = 'TEA-100G'")) {
            assertTrue(stock.next());
            assertEquals(expected, stock.getInt(1));
            assertFalse(stock.next());
        }
    }

    /// Only H2's row-lock timeout is retryable; unrelated errors retain HikariCP's eviction policy.
    public static final class LockContentionPolicy implements SQLExceptionOverride {
        public Override adjudicate(SQLException exception) {
            if (exception instanceof SQLTimeoutException && exception.getErrorCode() == 50200) {
                return Override.DO_NOT_EVICT;
            }
            return Override.CONTINUE_EVICT;
        }
    }
}
