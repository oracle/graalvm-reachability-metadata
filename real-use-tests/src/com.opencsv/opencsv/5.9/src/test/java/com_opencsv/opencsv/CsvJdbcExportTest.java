/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;
import com.opencsv.ResultSetHelperService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringWriter;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/// Exercises OpenCSV's JDBC result-set export for a report produced from stored orders.
public class CsvJdbcExportTest {
    @Test
    @Timeout(120)
    public void exportsOrdersWithColumnNamesAndConfiguredDates() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:orders")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE orders (order_id VARCHAR(8), quantity INT, "
                        + "shipped_on DATE, note VARCHAR(64))");
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO orders VALUES (?, ?, ?, ?)")) {
                insert.setString(1, "O-17");
                insert.setInt(2, 3);
                insert.setDate(3, Date.valueOf("2024-02-03"));
                insert.setString(4, "priority");
                insert.executeUpdate();

                insert.setString(1, "O-23");
                insert.setInt(2, 1);
                insert.setDate(3, Date.valueOf("2024-02-14"));
                insert.setString(4, "gift, wrap");
                insert.executeUpdate();
            }

            StringWriter report = new StringWriter();
            try (Statement query = connection.createStatement();
                    ResultSet orders = query.executeQuery("SELECT order_id, quantity, shipped_on, note "
                            + "FROM orders ORDER BY order_id");
                    ICSVWriter writer = new CSVWriterBuilder(report).withLineEnd("\n").build()) {
                ResultSetHelperService resultService = new ResultSetHelperService();
                resultService.setDateFormat("dd/MM/yyyy");
                writer.setResultService(resultService);

                int recordsWritten = writer.writeAll(orders, true, false, false);

                assertEquals(3, recordsWritten);
                assertFalse(writer.checkError());
            }

            assertEquals("ORDER_ID,QUANTITY,SHIPPED_ON,NOTE\n"
                    + "O-17,3,03/02/2024,priority\n"
                    + "O-23,1,14/02/2024,\"gift, wrap\"\n", report.toString());
        }
    }
}
