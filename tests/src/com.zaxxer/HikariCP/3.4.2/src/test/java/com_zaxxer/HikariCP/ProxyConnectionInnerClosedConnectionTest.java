/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.HikariCP;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProxyConnectionInnerClosedConnectionTest {
    @Test
    public void closedConnectionRejectsOperationsWithoutClosingReborrowedConnection() throws SQLException {
        try (HikariDataSource pool = new HikariDataSource()) {
            pool.setJdbcUrl("jdbc:h2:mem:closed_connection");
            pool.setUsername("sa");
            pool.setPassword("");
            pool.setMaximumPoolSize(1);
            pool.setMinimumIdle(0);
            pool.setConnectionTimeout(30000);
            pool.setValidationTimeout(10000);
            pool.setInitializationFailTimeout(30000);

            try (Connection closed = pool.getConnection()) {
                assertFalse(closed.isClosed());
                assertTrue(closed.isValid(10));
                closed.close();
                assertTrue(closed.isClosed());
                assertFalse(closed.isValid(10));
                assertThrows(SQLException.class, closed::createStatement);
                assertThrows(SQLException.class, closed::getAutoCommit);

                try (Connection borrowed = pool.getConnection()) {
                    closed.close();
                    closed.abort(Runnable::run);
                    assertFalse(borrowed.isClosed());
                    try (Statement statement = borrowed.createStatement();
                         ResultSet rows = statement.executeQuery("SELECT 29")) {
                        assertTrue(rows.next());
                        assertEquals(29, rows.getInt(1));
                    }
                }
            }
        }
    }
}
