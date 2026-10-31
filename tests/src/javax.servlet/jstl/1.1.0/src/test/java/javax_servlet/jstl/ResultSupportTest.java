/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet.jstl;

import java.sql.SQLException;
import java.sql.Types;

import javax.servlet.jsp.jstl.sql.Result;
import javax.servlet.jsp.jstl.sql.ResultSupport;

import org.h2.tools.SimpleResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResultSupportTest {
    @Test
    void snapshotsRowsForIndexedAndCaseInsensitiveColumnAccess() throws SQLException {
        Result result;
        try (SimpleResultSet rows = rows(1)) {
            result = ResultSupport.toResult(rows);
        }

        assertNotNull(result);
        assertEquals(1, result.getRowCount());
        assertArrayEquals(new String[]{"Id", "Description"}, result.getColumnNames());
        assertArrayEquals(new Object[]{1, null}, result.getRowsByIndex()[0]);
        assertEquals(1, result.getRows()[0].get("ID"));
        assertTrue(result.getRows()[0].containsKey("description"));
        assertNull(result.getRows()[0].get("DESCRIPTION"));
        assertFalse(result.isLimitedByMaxRows());
    }

    @Test
    void reportsWhenAdditionalRowsAreExcludedByTheLimit() throws SQLException {
        try (SimpleResultSet rows = rows(3)) {
            Result result = ResultSupport.toResult(rows, 2);

            assertNotNull(result);
            assertEquals(2, result.getRowCount());
            assertEquals(2, result.getRows().length);
            assertEquals(2, result.getRowsByIndex().length);
            assertTrue(result.isLimitedByMaxRows());
        }
    }

    @Test
    void doesNotReportTruncationWhenAllRowsFit() throws SQLException {
        try (SimpleResultSet rows = rows(2)) {
            Result result = ResultSupport.toResult(rows, 2);

            assertNotNull(result);
            assertEquals(2, result.getRowCount());
            assertFalse(result.isLimitedByMaxRows());
        }
    }

    @Test
    void retainsColumnNamesForAnEmptyResult() throws SQLException {
        try (SimpleResultSet rows = rows(0)) {
            Result result = ResultSupport.toResult(rows);

            assertNotNull(result);
            assertEquals(0, result.getRowCount());
            assertArrayEquals(new String[]{"Id", "Description"}, result.getColumnNames());
            assertEquals(0, result.getRows().length);
            assertEquals(0, result.getRowsByIndex().length);
            assertFalse(result.isLimitedByMaxRows());
        }
    }

    private static SimpleResultSet rows(int count) {
        SimpleResultSet rows = new SimpleResultSet();
        rows.addColumn("Id", Types.INTEGER, 10, 0);
        rows.addColumn("Description", Types.VARCHAR, 40, 0);
        for (int id = 1; id <= count; id++) {
            rows.addRow(id, null);
        }
        return rows;
    }
}
