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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises DBCP statement and result-set tracking when a pooled connection is returned.
/// A cancelled report releases its connection scope with an unfinished cursor; DBCP must close
/// the cursor and statement before the next report borrows the single available connection.
public class CancelledReportResourceCleanupTest {

    @Test
    @Timeout(60)
    public void cancellingAReportRevokesItsCursorWithoutDisruptingTheNextReport() throws SQLException {
        try (BasicDataSource dataSource = new BasicDataSource()) {
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:cancelled_report_cleanup;DB_CLOSE_ON_EXIT=FALSE");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            dataSource.setMaxTotal(1);
            dataSource.setMaxIdle(1);
            dataSource.setMaxWait(Duration.ofSeconds(10));
            dataSource.setPoolPreparedStatements(false);

            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE report_invoices (id INTEGER PRIMARY KEY, cents INTEGER)");
                statement.executeUpdate("INSERT INTO report_invoices VALUES (1, 1200), (2, 3400), (3, 5600)");
            }

            Connection reportConnection = dataSource.getConnection();
            Statement reportStatement;
            ResultSet reportRows;
            List<Integer> preview = new ArrayList<>();
            try (reportConnection) {
                reportStatement = reportConnection.createStatement();
                reportRows = reportStatement.executeQuery("SELECT id, cents FROM report_invoices ORDER BY id");
                assertTrue(reportRows.next());
                preview.add(reportRows.getInt("cents"));
                assertFalse(reportRows.isClosed());
                // The preview request ends early and releases only its owning connection scope.
            }

            assertEquals(List.of(1200), preview);
            assertTrue(reportStatement.isClosed(), "Returning the lease must close its unfinished statement");
            assertTrue(reportRows.isClosed(), "The cancelled report must not retain a live cursor");

            try (Connection nextReport = dataSource.getConnection();
                    Statement statement = nextReport.createStatement()) {
                // Handles retained by the cancelled request cannot access the next request's session.
                assertThrows(SQLException.class,
                        () -> reportStatement.executeQuery("SELECT COUNT(*) FROM report_invoices"));
                assertThrows(SQLException.class, () -> reportRows.next());

                List<Integer> invoices = new ArrayList<>();
                try (ResultSet rows = statement.executeQuery("SELECT cents FROM report_invoices ORDER BY id")) {
                    while (rows.next()) {
                        invoices.add(rows.getInt("cents"));
                    }
                }
                assertEquals(List.of(1200, 3400, 5600), invoices);
                assertEquals(10200, invoices.stream().mapToInt(Integer::intValue).sum());
            }
        }
    }
}
