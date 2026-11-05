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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises HSQLDB's JDBC batch-update subsystem for importing order lines.
public class BatchOrderImportTest {
    @Test
    @Timeout(60)
    public void importsOrderLinesAsOneBatchAndReadsThemBack() throws Exception {
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setDatabase("mem:batch_order_import_scenario");
        dataSource.setUser("SA");
        dataSource.setPassword("");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE order_lines ("
                    + "order_id INTEGER, line_number INTEGER, item VARCHAR(64), "
                    + "quantity INTEGER, PRIMARY KEY (order_id, line_number))");
        }

        try (Connection connection = dataSource.getConnection();
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO order_lines VALUES (?, ?, ?, ?)")) {
            connection.setAutoCommit(false);
            addLine(insert, 4001, 1, "Notebook", 2);
            addLine(insert, 4001, 2, "Pen", 6);
            addLine(insert, 4001, 3, "Folder", 1);

            assertArrayEquals(new int[] {1, 1, 1}, insert.executeBatch());
            connection.commit();
        }

        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT line_number, item, quantity FROM order_lines "
                                + "WHERE order_id = ? ORDER BY line_number")) {
            query.setInt(1, 4001);
            try (ResultSet resultSet = query.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(1, resultSet.getInt("line_number"));
                assertEquals("Notebook", resultSet.getString("item"));
                assertEquals(2, resultSet.getInt("quantity"));

                assertTrue(resultSet.next());
                assertEquals(2, resultSet.getInt("line_number"));
                assertEquals("Pen", resultSet.getString("item"));
                assertEquals(6, resultSet.getInt("quantity"));

                assertTrue(resultSet.next());
                assertEquals(3, resultSet.getInt("line_number"));
                assertEquals("Folder", resultSet.getString("item"));
                assertEquals(1, resultSet.getInt("quantity"));
                assertTrue(!resultSet.next());
            }
        }
    }

    private static void addLine(PreparedStatement insert, int orderId, int lineNumber,
            String item, int quantity) throws Exception {
        insert.setInt(1, orderId);
        insert.setInt(2, lineNumber);
        insert.setString(3, item);
        insert.setInt(4, quantity);
        insert.addBatch();
    }
}
