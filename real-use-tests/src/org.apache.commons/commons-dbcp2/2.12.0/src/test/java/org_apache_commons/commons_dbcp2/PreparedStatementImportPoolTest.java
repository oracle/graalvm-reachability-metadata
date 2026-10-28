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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises DBCP prepared-statement pooling for sequential inventory import requests.
/// Completed and cancelled imports return their statements to the cache; subsequent imports
/// must not inherit either bound parameters or unexecuted batch entries.
public class PreparedStatementImportPoolTest {

    @Test
    @Timeout(60)
    public void cachedImportStatementsDoNotLeakParametersOrCancelledBatches() throws SQLException {
        try (BasicDataSource dataSource = new BasicDataSource()) {
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:statement_import_pool;DB_CLOSE_ON_EXIT=FALSE");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            dataSource.setMaxTotal(1);
            dataSource.setMaxIdle(1);
            dataSource.setMaxWait(Duration.ofSeconds(10));
            dataSource.setDefaultAutoCommit(true);
            dataSource.setPoolPreparedStatements(true);
            dataSource.setMaxOpenPreparedStatements(1);

            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE inventory_import (
                            sku VARCHAR(40) PRIMARY KEY,
                            quantity INTEGER NOT NULL
                        )
                        """);
            }

            String insertSql = "INSERT INTO inventory_import (sku, quantity) VALUES (?, ?)";
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement insert = connection.prepareStatement(insertSql)) {
                insert.setString(1, "coffee");
                insert.setInt(2, 12);
                insert.addBatch();
                insert.setString(1, "tea");
                insert.setInt(2, 8);
                insert.addBatch();
                assertArrayEquals(new int[]{1, 1}, insert.executeBatch());
            }

            // A validation failure cancels this import after staging, but before executing it.
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement insert = connection.prepareStatement(insertSql)) {
                insert.setString(1, "cancelled-import");
                insert.setInt(2, 99);
                insert.addBatch();
            }

            try (Connection connection = dataSource.getConnection();
                    PreparedStatement insert = connection.prepareStatement(insertSql)) {
                insert.setString(1, "cocoa");
                // An incomplete request must fail rather than reuse the cancelled quantity.
                assertThrows(SQLException.class, () -> insert.executeUpdate());
                insert.setInt(2, 5);
                insert.addBatch();
                assertArrayEquals(new int[]{1}, insert.executeBatch());
            }

            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery(
                            "SELECT sku, quantity FROM inventory_import ORDER BY sku")) {
                assertTrue(rows.next());
                assertEquals("cocoa", rows.getString("sku"));
                assertEquals(5, rows.getInt("quantity"));
                assertTrue(rows.next());
                assertEquals("coffee", rows.getString("sku"));
                assertEquals(12, rows.getInt("quantity"));
                assertTrue(rows.next());
                assertEquals("tea", rows.getString("sku"));
                assertEquals(8, rows.getInt("quantity"));
                assertFalse(rows.next(), "Cancelled batch entries must not reach the database");
            }
        }
    }
}
