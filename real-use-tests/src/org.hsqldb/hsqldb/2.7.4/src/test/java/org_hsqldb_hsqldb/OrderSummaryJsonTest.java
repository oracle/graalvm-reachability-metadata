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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/// Exercises HSQLDB's JDBC SQL/JSON subsystem for producing an order summary.
public class OrderSummaryJsonTest {
    @Test
    @Timeout(60)
    public void buildsAnOrderSummaryJsonDocumentFromRows() throws Exception {
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setDatabase("mem:order_summary_json_scenario");
        dataSource.setUser("SA");
        dataSource.setPassword("");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE order_items ("
                    + "order_id INTEGER, sku VARCHAR(32), quantity INTEGER, "
                    + "PRIMARY KEY (order_id, sku))");
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO order_items VALUES (?, ?, ?)")) {
                insert.setInt(1, 7);
                insert.setString(2, "PENCIL");
                insert.setInt(3, 3);
                insert.addBatch();
                insert.setInt(1, 7);
                insert.setString(2, "ERASER");
                insert.setInt(3, 2);
                insert.addBatch();
                assertEquals(2, insert.executeBatch().length);
            }
        }

        String summary;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement("""
                        SELECT JSON_OBJECT(
                            'orderId' VALUE order_id,
                            'items' VALUE JSON_ARRAYAGG(
                                JSON_OBJECT('sku' VALUE sku, 'quantity' VALUE quantity)
                                ORDER BY sku),
                            'totalUnits' VALUE SUM(quantity))
                        FROM order_items
                        WHERE order_id = ?
                        GROUP BY order_id
                        """)) {
            query.setInt(1, 7);
            try (ResultSet resultSet = query.executeQuery()) {
                assertEquals(true, resultSet.next());
                summary = resultSet.getString(1);
                assertFalse(resultSet.next());
            }
        }

        assertEquals(
                "{\"orderId\":7,\"items\":[{\"sku\":\"ERASER\",\"quantity\":2},"
                        + "{\"sku\":\"PENCIL\",\"quantity\":3}],\"totalUnits\":5}",
                summary);
    }
}
