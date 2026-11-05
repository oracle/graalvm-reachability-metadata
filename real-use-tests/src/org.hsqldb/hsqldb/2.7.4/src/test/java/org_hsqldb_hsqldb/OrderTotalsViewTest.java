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
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises HSQLDB's SQL view subsystem for an order totals read model.
public class OrderTotalsViewTest {
    @Test
    @Timeout(60)
    public void readsAnAggregatedOrderViewLikeAnApplicationReport() throws Exception {
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setDatabase("mem:order_totals_view_scenario");
        dataSource.setUser("SA");
        dataSource.setPassword("");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE customers ("
                    + "id INTEGER PRIMARY KEY, name VARCHAR(80))");
            statement.executeUpdate("CREATE TABLE order_lines ("
                    + "order_id INTEGER, customer_id INTEGER, quantity INTEGER, "
                    + "PRIMARY KEY (order_id), FOREIGN KEY (customer_id) REFERENCES customers(id))");
            statement.executeUpdate("INSERT INTO customers VALUES (8, 'Northwind Office')");
            statement.executeUpdate("INSERT INTO order_lines VALUES (501, 8, 7)");
            statement.executeUpdate("CREATE VIEW order_totals AS "
                    + "SELECT c.id AS customer_id, c.name AS customer_name, "
                    + "COUNT(o.order_id) AS order_count, SUM(o.quantity) AS total_units "
                    + "FROM customers c JOIN order_lines o ON o.customer_id = c.id "
                    + "GROUP BY c.id, c.name");
        }

        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT customer_id, customer_name, order_count, total_units "
                                + "FROM order_totals WHERE customer_id = ?")) {
            query.setInt(1, 8);
            try (ResultSet resultSet = query.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(8, resultSet.getInt("customer_id"));
                assertEquals("Northwind Office", resultSet.getString("customer_name"));
                assertEquals(1, resultSet.getInt("order_count"));
                assertEquals(7, resultSet.getInt("total_units"));
                assertFalse(resultSet.next());
            }
        }
    }
}
