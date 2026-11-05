/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb_hsqldb;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises HSQLDB through an application-managed HikariCP connection pool.
public class HikariConnectionPoolTest {
    @Test
    @Timeout(60)
    public void sharesDatabaseStateAcrossPooledConnections() throws Exception {
        JDBCDataSource database = new JDBCDataSource();
        database.setDatabase("mem:hikari_connection_pool_scenario");
        database.setUser("SA");
        database.setPassword("");

        HikariConfig poolConfig = new HikariConfig();
        poolConfig.setDataSource(database);
        poolConfig.setMaximumPoolSize(2);
        poolConfig.setMinimumIdle(1);
        poolConfig.setConnectionTimeout(10000);

        try (HikariDataSource pool = new HikariDataSource(poolConfig)) {
            try (Connection connection = pool.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "CREATE TABLE shipments (id INTEGER PRIMARY KEY, status VARCHAR(32))");
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO shipments VALUES (?, ?)")) {
                    insert.setInt(1, 12);
                    insert.setString(2, "READY");
                    assertEquals(1, insert.executeUpdate());
                }
            }

            try (Connection connection = pool.getConnection();
                    PreparedStatement query = connection.prepareStatement(
                            "SELECT status FROM shipments WHERE id = ?")) {
                query.setInt(1, 12);
                try (ResultSet resultSet = query.executeQuery()) {
                    assertTrue(resultSet.next());
                    assertEquals("READY", resultSet.getString(1));
                    assertTrue(!resultSet.next());
                }
            }
        }
    }
}
