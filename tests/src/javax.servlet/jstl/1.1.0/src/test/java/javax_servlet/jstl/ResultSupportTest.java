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
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;

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
        try (CachedRowSet rows = rows(1)) {
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
        try (CachedRowSet rows = rows(3)) {
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
        try (CachedRowSet rows = rows(2)) {
            Result result = ResultSupport.toResult(rows, 2);

            assertNotNull(result);
            assertEquals(2, result.getRowCount());
            assertFalse(result.isLimitedByMaxRows());
        }
    }

    @Test
    void retainsColumnNamesForAnEmptyResult() throws SQLException {
        try (CachedRowSet rows = rows(0)) {
            Result result = ResultSupport.toResult(rows);

            assertNotNull(result);
            assertEquals(0, result.getRowCount());
            assertArrayEquals(new String[]{"Id", "Description"}, result.getColumnNames());
            assertEquals(0, result.getRows().length);
            assertEquals(0, result.getRowsByIndex().length);
            assertFalse(result.isLimitedByMaxRows());
        }
    }

    private static CachedRowSet rows(int count) throws SQLException {
        RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(2);
        metadata.setColumnName(1, "Id");
        metadata.setColumnType(1, Types.INTEGER);
        metadata.setColumnName(2, "Description");
        metadata.setColumnType(2, Types.VARCHAR);
        CachedRowSet rows = RowSetProvider.newFactory().createCachedRowSet();
        rows.setMetaData(metadata);
        for (int id = 1; id <= count; id++) {
            rows.moveToInsertRow();
            rows.updateInt(1, id);
            rows.updateNull(2);
            rows.insertRow();
            rows.moveToCurrentRow();
        }
        rows.beforeFirst();
        return rows;
    }
}
