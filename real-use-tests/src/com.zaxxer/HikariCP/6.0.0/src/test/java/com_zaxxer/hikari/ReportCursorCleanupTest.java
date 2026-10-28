/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.hikari;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises HikariCP's JDBC statement and result-set cleanup subsystem on connection return.
/// A reporting request stops reading a report early and releases its connection; the pool
/// closes the unfinished cursor and statement before another request reuses the session.
public class ReportCursorCleanupTest {
    @Test
    @Timeout(60)
    public void releasesUnfinishedReportResourcesWithoutDiscardingTheDatabaseSession() throws SQLException {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:report_cursor_cleanup");

        HikariConfig config = new HikariConfig();
        config.setDataSource(database);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setMaxLifetime(0);
        config.setKeepaliveTime(0);
        config.setConnectionTimeout(10000);
        config.setValidationTimeout(10000);

        try (HikariDataSource pool = new HikariDataSource(config)) {
            try (Connection migration = pool.getConnection();
                            Statement schema = migration.createStatement()) {
                schema.execute("""
                        CREATE TABLE report_sales (
                            sale_id INTEGER PRIMARY KEY,
                            region VARCHAR(20) NOT NULL,
                            cents INTEGER NOT NULL
                        )
                        """);
                schema.execute("INSERT INTO report_sales VALUES (1, 'north', 1200), (2, 'north', 1800)");
            }

            PreparedStatement unfinishedReport;
            ResultSet reportRows;
            int reportingSession;
            try (Connection request = pool.getConnection()) {
                unfinishedReport = request.prepareStatement("""
                        SELECT sale_id, cents, SESSION_ID() AS database_session
                        FROM report_sales WHERE region = ? ORDER BY sale_id
                        """);
                unfinishedReport.setString(1, "north");
                reportRows = unfinishedReport.executeQuery();
                assertTrue(reportRows.next());
                assertEquals(1, reportRows.getInt("sale_id"));
                assertEquals(1200, reportRows.getInt("cents"));
                reportingSession = reportRows.getInt("database_session");
                assertFalse(unfinishedReport.isClosed());
                assertFalse(reportRows.isClosed());
                // The caller stops streaming after the first row. Only the connection is
                // explicitly closed, leaving the pool responsible for the outstanding query.
            }

            assertTrue(unfinishedReport.isClosed(), "Returning the lease must close its outstanding statement");
            assertTrue(reportRows.isClosed(), "The unfinished report cursor must not survive the request");

            try (Connection nextRequest = pool.getConnection();
                            PreparedStatement totals = nextRequest.prepareStatement("""
                                    SELECT SUM(cents) AS total_cents, COUNT(*) AS sale_count,
                                           SESSION_ID() AS database_session
                                    FROM report_sales WHERE region = ?
                                    """)) {
                totals.setString(1, "north");
                try (ResultSet summary = totals.executeQuery()) {
                    assertTrue(summary.next());
                    assertEquals(reportingSession, summary.getInt("database_session"),
                                    "Resource cleanup must leave the physical session reusable");
                    assertEquals(3000, summary.getInt("total_cents"));
                    assertEquals(2, summary.getInt("sale_count"));
                    assertFalse(summary.next());
                }
            }
        }
    }
}
