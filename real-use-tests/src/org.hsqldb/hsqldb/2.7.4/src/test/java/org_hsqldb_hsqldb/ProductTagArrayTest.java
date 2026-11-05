/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb_hsqldb;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises HSQLDB's JDBC SQL array subsystem for product tags.
public class ProductTagArrayTest {
    @Test
    @Timeout(60)
    public void storesAndReadsProductTagsThroughJdbcArray() throws Exception {
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setDatabase("mem:product_tag_array_scenario");
        dataSource.setUser("SA");
        dataSource.setPassword("");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE products (id INTEGER PRIMARY KEY, name VARCHAR(64), tags VARCHAR(32) ARRAY)");

            Array tags = connection.createArrayOf("VARCHAR", new Object[] {"office", "sale", "popular"});
            try {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO products VALUES (?, ?, ?)")) {
                    insert.setInt(1, 41);
                    insert.setString(2, "Notebook");
                    insert.setArray(3, tags);
                    assertEquals(1, insert.executeUpdate());
                }
            } finally {
                tags.free();
            }
        }

        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT name, tags FROM products WHERE id = ?")) {
            query.setInt(1, 41);
            try (ResultSet resultSet = query.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("Notebook", resultSet.getString("name"));
                Array tags = resultSet.getArray("tags");
                try {
                    assertEquals("VARCHAR", tags.getBaseTypeName());
                    assertArrayEquals(
                            new Object[] {"office", "sale", "popular"},
                            (Object[]) tags.getArray());
                } finally {
                    tags.free();
                }
                assertTrue(!resultSet.next());
            }
        }
    }
}
