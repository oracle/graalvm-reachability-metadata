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
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HikariCPTest {
    @Test
    public void recyclesPoolEntryAndResetsTransactionStateOnReborrow() throws SQLException {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:entry_recycling");
        database.setUser("sa");
        database.setPassword("");

        try (HikariDataSource pool = new HikariDataSource()) {
            pool.setDataSource(database);
            pool.setMaximumPoolSize(1);
            pool.setMinimumIdle(0);
            pool.setConnectionTimeout(30000);
            pool.setValidationTimeout(10000);
            pool.setInitializationFailTimeout(30000);
            Connection physical;
            try (Connection first = pool.getConnection();
                 Statement statement = first.createStatement()) {
                physical = first.unwrap(Connection.class);
                assertEquals(1, pool.getHikariPoolMXBean().getActiveConnections());
                statement.executeUpdate("CREATE TABLE application_items (id INTEGER PRIMARY KEY)");
                first.setAutoCommit(false);
                assertEquals(1, statement.executeUpdate("INSERT INTO application_items VALUES (1)"));
                first.commit();
                assertEquals(1, statement.executeUpdate("INSERT INTO application_items VALUES (2)"));
            }

            assertEquals(0, pool.getHikariPoolMXBean().getActiveConnections());
            assertEquals(1, pool.getHikariPoolMXBean().getIdleConnections());
            try (Connection second = pool.getConnection();
                 Statement statement = second.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT COUNT(*), SUM(id) FROM application_items")) {
                assertSame(physical, second.unwrap(Connection.class));
                assertTrue(second.getAutoCommit());
                assertEquals(1, pool.getHikariPoolMXBean().getActiveConnections());
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
                assertEquals(1, rows.getInt(2));
            }
            assertEquals(1, pool.getHikariPoolMXBean().getIdleConnections());
        }
    }
}
