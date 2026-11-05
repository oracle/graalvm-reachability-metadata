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

/// Exercises HSQLDB's SQL sequence subsystem for allocating invoice numbers.
public class InvoiceSequenceTest {
    @Test
    @Timeout(60)
    public void allocatesStableInvoiceNumbersForInsertedRows() throws Exception {
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setDatabase("mem:invoice_sequence_scenario");
        dataSource.setUser("SA");
        dataSource.setPassword("");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE SEQUENCE invoice_numbers AS INTEGER START WITH 1000 INCREMENT BY 5");
            statement.executeUpdate("CREATE TABLE invoices ("
                    + "id INTEGER PRIMARY KEY, description VARCHAR(128))");

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO invoices VALUES (NEXT VALUE FOR invoice_numbers, ?)");
                    PreparedStatement nextNumber = connection.prepareStatement(
                            "VALUES (NEXT VALUE FOR invoice_numbers)")) {
                insert.setString(1, "Replacement filters");
                assertEquals(1, insert.executeUpdate());
                insert.setString(1, "Air purifier");
                assertEquals(1, insert.executeUpdate());

                try (ResultSet resultSet = nextNumber.executeQuery()) {
                    assertTrue(resultSet.next());
                    assertEquals(1010, resultSet.getInt(1));
                    assertFalse(resultSet.next());
                }
            }
        }

        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT id, description FROM invoices ORDER BY id");
                ResultSet resultSet = query.executeQuery()) {
            assertTrue(resultSet.next());
            assertEquals(1000, resultSet.getInt("id"));
            assertEquals("Replacement filters", resultSet.getString("description"));
            assertTrue(resultSet.next());
            assertEquals(1005, resultSet.getInt("id"));
            assertEquals("Air purifier", resultSet.getString("description"));
            assertFalse(resultSet.next());
        }
    }
}
