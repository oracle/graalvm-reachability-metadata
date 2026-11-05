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
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises HSQLDB's JDBC savepoint transaction subsystem for an inventory reservation.
public class InventorySavepointTransactionTest {
    @Test
    @Timeout(60)
    public void commitsOnlyTheReservationsMadeAfterACompensatingRollback() throws Exception {
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setDatabase("mem:inventory_savepoint_scenario");
        dataSource.setUser("SA");
        dataSource.setPassword("");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE inventory (sku VARCHAR(32) PRIMARY KEY, quantity INTEGER)");
            connection.setAutoCommit(false);
            statement.executeUpdate("INSERT INTO inventory VALUES ('PENCIL', 20)");

            Savepoint beforeReservationBatch = connection.setSavepoint("before_reservation_batch");
            statement.executeUpdate("UPDATE inventory SET quantity = quantity - 4 WHERE sku = 'PENCIL'");
            statement.executeUpdate("INSERT INTO inventory VALUES ('ERASER', 8)");
            connection.rollback(beforeReservationBatch);

            statement.executeUpdate("UPDATE inventory SET quantity = quantity - 2 WHERE sku = 'PENCIL'");
            connection.commit();
        }

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT sku, quantity FROM inventory ORDER BY sku")) {
            assertEquals(true, resultSet.next());
            assertEquals("PENCIL", resultSet.getString("sku"));
            assertEquals(18, resultSet.getInt("quantity"));
            assertEquals(false, resultSet.next());
        }
    }
}
