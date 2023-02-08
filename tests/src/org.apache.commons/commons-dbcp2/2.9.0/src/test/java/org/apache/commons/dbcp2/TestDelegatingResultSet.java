/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.commons.dbcp2;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


@SuppressWarnings({"deprecation", "unchecked", "rawtypes", "FieldCanBeLocal", "ThrowableNotThrown", "MagicConstant", "ResultOfMethodCallIgnored"})
public class TestDelegatingResultSet {
    private TesterConnection testConn;
    private DelegatingConnection<Connection> conn;
    private ResultSet rs;
    private DelegatingResultSet delegate;

    @BeforeEach
    public void setUp() {
        testConn = new TesterConnection("foo", "bar");
        conn = new DelegatingConnection<>(testConn);
        rs = mock(ResultSet.class);
        delegate = (DelegatingResultSet) DelegatingResultSet.wrapResultSet(conn, rs);
    }

    @Test
    public void testAbsoluteInteger() throws Exception {
        try {
            delegate.absolute(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).absolute(1);
    }

    @Test
    public void testAbsolutes() throws Exception {
        try {
            delegate.absolute(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).absolute(1);
    }

    @Test
    public void testAfterLast() throws Exception {
        try {
            delegate.afterLast();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).afterLast();
    }

    @Test
    public void testBeforeFirst() throws Exception {
        try {
            delegate.beforeFirst();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).beforeFirst();
    }

    @Test
    public void testCancelRowUpdates() throws Exception {
        try {
            delegate.cancelRowUpdates();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).cancelRowUpdates();
    }

    @Test
    public void testClearWarnings() throws Exception {
        try {
            delegate.clearWarnings();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).clearWarnings();
    }

    @Test
    public void testClose() throws Exception {
        try {
            delegate.close();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).close();
    }

    @Test
    public void testDeleteRow() throws Exception {
        try {
            delegate.deleteRow();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).deleteRow();
    }

    @Test
    public void testFindColumnString() throws Exception {
        try {
            delegate.findColumn("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).findColumn("foo");
    }

    @Test
    public void testFirst() throws Exception {
        try {
            delegate.first();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).first();
    }

    @Test
    public void testGetArrayInteger() throws Exception {
        try {
            delegate.getArray(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getArray(1);
    }

    @Test
    public void testGetArrayString() throws Exception {
        try {
            delegate.getArray("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getArray("foo");
    }

    @Test
    public void testGetAsciiStreamInteger() throws Exception {
        try {
            delegate.getAsciiStream(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getAsciiStream(1);
    }

    @Test
    public void testGetAsciiStreamString() throws Exception {
        try {
            delegate.getAsciiStream("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getAsciiStream("foo");
    }

    @Test
    public void testGetBigDecimalInteger() throws Exception {
        try {
            delegate.getBigDecimal(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getBigDecimal(1);
    }

    @Test
    public void testGetBigDecimalString() throws Exception {
        try {
            delegate.getBigDecimal("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getBigDecimal("foo");
    }

    @Test
    public void testGetBinaryStreamInteger() throws Exception {
        try {
            delegate.getBinaryStream(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getBinaryStream(1);
    }

    @Test
    public void testGetBinaryStreamString() throws Exception {
        try {
            delegate.getBinaryStream("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getBinaryStream("foo");
    }

    @Test
    public void testGetBlobInteger() throws Exception {
        try {
            delegate.getBlob(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getBlob(1);
    }

    @Test
    public void testGetBlobString() throws Exception {
        try {
            delegate.getBlob("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getBlob("foo");
    }

    @Test
    public void testGetBooleanInteger() throws Exception {
        try {
            delegate.getBoolean(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getBoolean(1);
    }

    @Test
    public void testGetBooleanString() throws Exception {
        try {
            delegate.getBoolean("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getBoolean("foo");
    }

    @Test
    public void testGetByteInteger() throws Exception {
        try {
            delegate.getByte(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getByte(1);
    }

    @Test
    public void testGetBytesInteger() throws Exception {
        try {
            delegate.getBytes(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getBytes(1);
    }

    @Test
    public void testGetBytesString() throws Exception {
        try {
            delegate.getBytes("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getBytes("foo");
    }

    @Test
    public void testGetByteString() throws Exception {
        try {
            delegate.getByte("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getByte("foo");
    }

    @Test
    public void testGetCharacterStreamInteger() throws Exception {
        try {
            delegate.getCharacterStream(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getCharacterStream(1);
    }

    @Test
    public void testGetCharacterStreamString() throws Exception {
        try {
            delegate.getCharacterStream("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getCharacterStream("foo");
    }

    @Test
    public void testGetClobInteger() throws Exception {
        try {
            delegate.getClob(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getClob(1);
    }

    @Test
    public void testGetClobString() throws Exception {
        try {
            delegate.getClob("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getClob("foo");
    }

    @Test
    public void testGetConcurrency() throws Exception {
        try {
            delegate.getConcurrency();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getConcurrency();
    }

    @Test
    public void testGetCursorName() throws Exception {
        try {
            delegate.getCursorName();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getCursorName();
    }

    @Test
    public void testGetDateInteger() throws Exception {
        try {
            delegate.getDate(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getDate(1);
    }

    @Test
    public void testGetDateIntegerCalendar() throws Exception {
        try {
            delegate.getDate(1, null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getDate(1, null);
    }

    @Test
    public void testGetDateString() throws Exception {
        try {
            delegate.getDate("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getDate("foo");
    }

    @Test
    public void testGetDateStringCalendar() throws Exception {
        try {
            delegate.getDate("foo", null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getDate("foo", null);
    }

    @Test
    public void testGetDoubleInteger() throws Exception {
        try {
            delegate.getDouble(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getDouble(1);
    }

    @Test
    public void testGetDoubleString() throws Exception {
        try {
            delegate.getDouble("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getDouble("foo");
    }

    @Test
    public void testGetFetchDirection() throws Exception {
        try {
            delegate.getFetchDirection();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getFetchDirection();
    }

    @Test
    public void testGetFetchSize() throws Exception {
        try {
            delegate.getFetchSize();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getFetchSize();
    }

    @Test
    public void testGetFloatInteger() throws Exception {
        try {
            delegate.getFloat(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getFloat(1);
    }

    @Test
    public void testGetFloatString() throws Exception {
        try {
            delegate.getFloat("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getFloat("foo");
    }

    @Test
    public void testGetHoldability() throws Exception {
        try {
            delegate.getHoldability();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getHoldability();
    }

    @Test
    public void testGetIntInteger() throws Exception {
        try {
            delegate.getInt(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getInt(1);
    }

    @Test
    public void testGetIntString() throws Exception {
        try {
            delegate.getInt("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getInt("foo");
    }

    @Test
    public void testGetLongInteger() throws Exception {
        try {
            delegate.getLong(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getLong(1);
    }

    @Test
    public void testGetLongString() throws Exception {
        try {
            delegate.getLong("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getLong("foo");
    }

    @Test
    public void testGetMetaData() throws Exception {
        try {
            delegate.getMetaData();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getMetaData();
    }

    @Test
    public void testGetNCharacterStreamInteger() throws Exception {
        try {
            delegate.getNCharacterStream(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getNCharacterStream(1);
    }

    @Test
    public void testGetNCharacterStreamString() throws Exception {
        try {
            delegate.getNCharacterStream("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getNCharacterStream("foo");
    }

    @Test
    public void testGetNClobInteger() throws Exception {
        try {
            delegate.getNClob(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getNClob(1);
    }

    @Test
    public void testGetNClobString() throws Exception {
        try {
            delegate.getNClob("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getNClob("foo");
    }

    @Test
    public void testGetNStringInteger() throws Exception {
        try {
            delegate.getNString(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getNString(1);
    }

    @Test
    public void testGetNStringString() throws Exception {
        try {
            delegate.getNString("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getNString("foo");
    }

    @Test
    public void testGetObjectInteger() throws Exception {
        try {
            delegate.getObject(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getObject(1);
    }

    @Test
    public void testGetObjectIntegerClass() throws Exception {
        try {
            delegate.getObject(1, Object.class);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getObject(1, Object.class);
    }

    @Test
    public void testGetObjectIntegerMap() throws Exception {
        try {
            delegate.getObject(1, (java.util.Map) null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getObject(1, (java.util.Map) null);
    }

    @Test
    public void testGetObjectString() throws Exception {
        try {
            delegate.getObject("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getObject("foo");
    }

    @Test
    public void testGetObjectStringClass() throws Exception {
        try {
            delegate.getObject("foo", Object.class);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getObject("foo", Object.class);
    }

    @Test
    public void testGetObjectStringMap() throws Exception {
        try {
            delegate.getObject("foo", (java.util.Map) null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getObject("foo", (java.util.Map) null);
    }

    @Test
    public void testGetRefInteger() throws Exception {
        try {
            delegate.getRef(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getRef(1);
    }

    @Test
    public void testGetRefString() throws Exception {
        try {
            delegate.getRef("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getRef("foo");
    }

    @Test
    public void testGetRow() throws Exception {
        try {
            delegate.getRow();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getRow();
    }

    @Test
    public void testGetRowIdInteger() throws Exception {
        try {
            delegate.getRowId(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getRowId(1);
    }

    @Test
    public void testGetRowIdString() throws Exception {
        try {
            delegate.getRowId("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getRowId("foo");
    }

    @Test
    public void testGetShortInteger() throws Exception {
        try {
            delegate.getShort(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getShort(1);
    }

    @Test
    public void testGetShortString() throws Exception {
        try {
            delegate.getShort("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getShort("foo");
    }

    @Test
    public void testGetSQLXMLInteger() throws Exception {
        try {
            delegate.getSQLXML(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getSQLXML(1);
    }

    @Test
    public void testGetSQLXMLString() throws Exception {
        try {
            delegate.getSQLXML("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getSQLXML("foo");
    }

    @Test
    public void testGetStatement() throws Exception {
        try {
            delegate.getStatement();
        } catch (final SQLException e) {
        }
        verify(rs, times(0)).getStatement();
    }

    @Test
    public void testGetStringInteger() throws Exception {
        try {
            delegate.getString(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getString(1);
    }

    @Test
    public void testGetStringString() throws Exception {
        try {
            delegate.getString("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getString("foo");
    }

    @Test
    public void testGetTimeInteger() throws Exception {
        try {
            delegate.getTime(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getTime(1);
    }

    @Test
    public void testGetTimeIntegerCalendar() throws Exception {
        try {
            delegate.getTime(1, null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getTime(1, null);
    }

    @Test
    public void testGetTimestampInteger() throws Exception {
        try {
            delegate.getTimestamp(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getTimestamp(1);
    }

    @Test
    public void testGetTimestampIntegerCalendar() throws Exception {
        try {
            delegate.getTimestamp(1, null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getTimestamp(1, null);
    }

    @Test
    public void testGetTimestampString() throws Exception {
        try {
            delegate.getTimestamp("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getTimestamp("foo");
    }

    @Test
    public void testGetTimestampStringCalendar() throws Exception {
        try {
            delegate.getTimestamp("foo", null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getTimestamp("foo", null);
    }

    @Test
    public void testGetTimeString() throws Exception {
        try {
            delegate.getTime("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getTime("foo");
    }

    @Test
    public void testGetTimeStringCalendar() throws Exception {
        try {
            delegate.getTime("foo", null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getTime("foo", null);
    }

    @Test
    public void testGetType() throws Exception {
        try {
            delegate.getType();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getType();
    }

    @Test
    public void testGetUnicodeStreamInteger() throws Exception {
        try {
            delegate.getUnicodeStream(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getUnicodeStream(1);
    }

    @Test
    public void testGetUnicodeStreamString() throws Exception {
        try {
            delegate.getUnicodeStream("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getUnicodeStream("foo");
    }

    @Test
    public void testGetURLInteger() throws Exception {
        try {
            delegate.getURL(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getURL(1);
    }

    @Test
    public void testGetURLString() throws Exception {
        try {
            delegate.getURL("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getURL("foo");
    }

    @Test
    public void testGetWarnings() throws Exception {
        try {
            delegate.getWarnings();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).getWarnings();
    }

    @Test
    public void testInsertRow() throws Exception {
        try {
            delegate.insertRow();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).insertRow();
    }

    @Test
    public void testIsAfterLast() throws Exception {
        try {
            delegate.isAfterLast();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).isAfterLast();
    }

    @Test
    public void testIsBeforeFirst() throws Exception {
        try {
            delegate.isBeforeFirst();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).isBeforeFirst();
    }

    @Test
    public void testIsClosed() throws Exception {
        try {
            delegate.isClosed();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).isClosed();
    }

    @Test
    public void testIsFirst() throws Exception {
        try {
            delegate.isFirst();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).isFirst();
    }

    @Test
    public void testIsLast() throws Exception {
        try {
            delegate.isLast();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).isLast();
    }

    @Test
    public void testLast() throws Exception {
        try {
            delegate.last();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).last();
    }

    @Test
    public void testMoveToCurrentRow() throws Exception {
        try {
            delegate.moveToCurrentRow();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).moveToCurrentRow();
    }

    @Test
    public void testMoveToInsertRow() throws Exception {
        try {
            delegate.moveToInsertRow();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).moveToInsertRow();
    }

    @Test
    public void testNext() throws Exception {
        try {
            delegate.next();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).next();
    }

    @Test
    public void testPrevious() throws Exception {
        try {
            delegate.previous();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).previous();
    }

    @Test
    public void testRefreshRow() throws Exception {
        try {
            delegate.refreshRow();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).refreshRow();
    }

    @Test
    public void testRelativeInteger() throws Exception {
        try {
            delegate.relative(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).relative(1);
    }

    @Test
    public void testRowDeleted() throws Exception {
        try {
            delegate.rowDeleted();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).rowDeleted();
    }

    @Test
    public void testRowInserted() throws Exception {
        try {
            delegate.rowInserted();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).rowInserted();
    }

    @Test
    public void testRowUpdated() throws Exception {
        try {
            delegate.rowUpdated();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).rowUpdated();
    }

    @Test
    public void testSetFetchDirectionInteger() throws Exception {
        try {
            delegate.setFetchDirection(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).setFetchDirection(1);
    }

    @Test
    public void testSetFetchSizeInteger() throws Exception {
        try {
            delegate.setFetchSize(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).setFetchSize(1);
    }

    @Test
    public void testToString() {
        final String toString = delegate.toString();
        Assertions.assertTrue(toString.contains("DelegatingResultSet"));
        Assertions.assertTrue(toString.contains("Mock for ResultSet"));
    }

    @Test
    public void testUpdateArrayIntegerArray() throws Exception {
        try {
            delegate.updateArray(1, null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateArray(1, null);
    }

    @Test
    public void testUpdateArrayStringArray() throws Exception {
        try {
            delegate.updateArray("foo", null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateArray("foo", null);
    }

    @Test
    public void testUpdateAsciiStreamIntegerInputStream() throws Exception {
        try {
            delegate.updateAsciiStream(1, null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateAsciiStream(1, null);
    }

    @Test
    public void testUpdateAsciiStreamIntegerInputStreamInteger() throws Exception {
        try {
            delegate.updateAsciiStream(1, null, 1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateAsciiStream(1, null, 1);
    }

    @Test
    public void testUpdateAsciiStreamIntegerInputStreamLong() throws Exception {
        try {
            delegate.updateAsciiStream(1, null, 1L);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateAsciiStream(1, null, 1L);
    }

    @Test
    public void testUpdateAsciiStreamStringInputStream() throws Exception {
        try {
            delegate.updateAsciiStream("foo", null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateAsciiStream("foo", null);
    }

    @Test
    public void testUpdateAsciiStreamStringInputStreamInteger() throws Exception {
        try {
            delegate.updateAsciiStream("foo", null, 1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateAsciiStream("foo", null, 1);
    }

    @Test
    public void testUpdateAsciiStreamStringInputStreamLong() throws Exception {
        try {
            delegate.updateAsciiStream("foo", null, 1L);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateAsciiStream("foo", null, 1L);
    }

    @Test
    public void testUpdateBigDecimalIntegerBigDecimal() throws Exception {
        try {
            delegate.updateBigDecimal(1, java.math.BigDecimal.valueOf(1.0d));
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateBigDecimal(1, java.math.BigDecimal.valueOf(1.0d));
    }

    @Test
    public void testUpdateBigDecimalStringBigDecimal() throws Exception {
        try {
            delegate.updateBigDecimal("foo", java.math.BigDecimal.valueOf(1.0d));
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateBigDecimal("foo", java.math.BigDecimal.valueOf(1.0d));
    }

    @Test
    public void testUpdateBinaryStreamIntegerInputStream() throws Exception {
        try {
            delegate.updateBinaryStream(1, null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateBinaryStream(1, null);
    }

    @Test
    public void testUpdateBinaryStreamIntegerInputStreamInteger() throws Exception {
        try {
            delegate.updateBinaryStream(1, null, 1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateBinaryStream(1, null, 1);
    }

    @Test
    public void testUpdateBinaryStreamIntegerInputStreamLong() throws Exception {
        try {
            delegate.updateBinaryStream(1, null, 1L);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateBinaryStream(1, null, 1L);
    }

    @Test
    public void testUpdateBinaryStreamStringInputStream() throws Exception {
        try {
            delegate.updateBinaryStream("foo", null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateBinaryStream("foo", null);
    }

    @Test
    public void testUpdateBinaryStreamStringInputStreamInteger() throws Exception {
        try {
            delegate.updateBinaryStream("foo", null, 1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateBinaryStream("foo", null, 1);
    }

    @Test
    public void testUpdateBinaryStreamStringInputStreamLong() throws Exception {
        try {
            delegate.updateBinaryStream("foo", null, 1L);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateBinaryStream("foo", null, 1L);
    }

    @Test
    public void testUpdateBlobIntegerBlob() throws Exception {
        try {
            delegate.updateBlob(1, (java.sql.Blob) null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateBlob(1, (java.sql.Blob) null);
    }

    @Test
    public void testUpdateBlobIntegerInputStream() throws Exception {
        try {
            delegate.updateBlob(1, (java.io.InputStream) null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateBlob(1, (java.io.InputStream) null);
    }

    @Test
    public void testUpdateBlobIntegerInputStreamLong() throws Exception {
        try {
            delegate.updateBlob(1, null, 1L);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateBlob(1, null, 1L);
    }

    @Test
    public void testUpdateBlobStringBlob() throws Exception {
        try {
            delegate.updateBlob("foo", (java.sql.Blob) null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateBlob("foo", (java.sql.Blob) null);
    }

    @Test
    public void testUpdateBlobStringInputStream() throws Exception {
        try {
            delegate.updateBlob("foo", (java.io.InputStream) null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateBlob("foo", (java.io.InputStream) null);
    }

    @Test
    public void testUpdateBlobStringInputStreamLong() throws Exception {
        try {
            delegate.updateBlob("foo", null, 1L);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateBlob("foo", null, 1L);
    }

    @Test
    public void testUpdateBooleanIntegerBoolean() throws Exception {
        try {
            delegate.updateBoolean(1, Boolean.TRUE);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateBoolean(1, Boolean.TRUE);
    }

    @Test
    public void testUpdateBooleanStringBoolean() throws Exception {
        try {
            delegate.updateBoolean("foo", Boolean.TRUE);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateBoolean("foo", Boolean.TRUE);
    }

    @Test
    public void testUpdateByteIntegerByte() throws Exception {
        try {
            delegate.updateByte(1, (byte) 1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateByte(1, (byte) 1);
    }

    @Test
    public void testUpdateBytesIntegerByteArray() throws Exception {
        try {
            delegate.updateBytes(1, new byte[]{1});
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateBytes(1, new byte[]{1});
    }

    @Test
    public void testUpdateBytesStringByteArray() throws Exception {
        try {
            delegate.updateBytes("foo", new byte[]{1});
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateBytes("foo", new byte[]{1});
    }

    @Test
    public void testUpdateByteStringByte() throws Exception {
        try {
            delegate.updateByte("foo", (byte) 1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateByte("foo", (byte) 1);
    }

    @Test
    public void testUpdateCharacterStreamIntegerReader() throws Exception {
        try {
            delegate.updateCharacterStream(1, null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateCharacterStream(1, null);
    }

    @Test
    public void testUpdateCharacterStreamIntegerReaderInteger() throws Exception {
        try {
            delegate.updateCharacterStream(1, null, 1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateCharacterStream(1, null, 1);
    }

    @Test
    public void testUpdateCharacterStreamIntegerReaderLong() throws Exception {
        try {
            delegate.updateCharacterStream(1, null, 1L);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateCharacterStream(1, null, 1L);
    }

    @Test
    public void testUpdateCharacterStreamStringReader() throws Exception {
        try {
            delegate.updateCharacterStream("foo", null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateCharacterStream("foo", null);
    }

    @Test
    public void testUpdateCharacterStreamStringReaderInteger() throws Exception {
        try {
            delegate.updateCharacterStream("foo", null, 1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateCharacterStream("foo", null, 1);
    }

    @Test
    public void testUpdateCharacterStreamStringReaderLong() throws Exception {
        try {
            delegate.updateCharacterStream("foo", null, 1L);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateCharacterStream("foo", null, 1L);
    }

    @Test
    public void testUpdateClobIntegerClob() throws Exception {
        try {
            delegate.updateClob(1, (java.sql.Clob) null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateClob(1, (java.sql.Clob) null);
    }

    @Test
    public void testUpdateClobIntegerReader() throws Exception {
        try {
            delegate.updateClob(1, (java.io.StringReader) null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateClob(1, (java.io.StringReader) null);
    }

    @Test
    public void testUpdateClobIntegerReaderLong() throws Exception {
        try {
            delegate.updateClob(1, null, 1L);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateClob(1, null, 1L);
    }

    @Test
    public void testUpdateClobStringClob() throws Exception {
        try {
            delegate.updateClob("foo", (java.sql.Clob) null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateClob("foo", (java.sql.Clob) null);
    }

    @Test
    public void testUpdateClobStringReader() throws Exception {
        try {
            delegate.updateClob("foo", (java.io.StringReader) null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateClob("foo", (java.io.StringReader) null);
    }

    @Test
    public void testUpdateClobStringReaderLong() throws Exception {
        try {
            delegate.updateClob("foo", null, 1L);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateClob("foo", null, 1L);
    }

    @Test
    public void testUpdateDateIntegerSqlDate() throws Exception {
        try {
            delegate.updateDate(1, new java.sql.Date(1529827548745L));
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateDate(1, new java.sql.Date(1529827548745L));
    }

    @Test
    public void testUpdateDateStringSqlDate() throws Exception {
        try {
            delegate.updateDate("foo", new java.sql.Date(1529827548745L));
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateDate("foo", new java.sql.Date(1529827548745L));
    }

    @Test
    public void testUpdateDoubleIntegerDouble() throws Exception {
        try {
            delegate.updateDouble(1, 1.0d);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateDouble(1, 1.0d);
    }

    @Test
    public void testUpdateDoubleStringDouble() throws Exception {
        try {
            delegate.updateDouble("foo", 1.0d);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateDouble("foo", 1.0d);
    }

    @Test
    public void testUpdateFloatIntegerFloat() throws Exception {
        try {
            delegate.updateFloat(1, 1.0f);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateFloat(1, 1.0f);
    }

    @Test
    public void testUpdateFloatStringFloat() throws Exception {
        try {
            delegate.updateFloat("foo", 1.0f);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateFloat("foo", 1.0f);
    }

    @Test
    public void testUpdateIntIntegerInteger() throws Exception {
        try {
            delegate.updateInt(1, 1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateInt(1, 1);
    }

    @Test
    public void testUpdateIntStringInteger() throws Exception {
        try {
            delegate.updateInt("foo", 1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateInt("foo", 1);
    }

    @Test
    public void testUpdateLongIntegerLong() throws Exception {
        try {
            delegate.updateLong(1, 1L);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateLong(1, 1L);
    }

    @Test
    public void testUpdateLongStringLong() throws Exception {
        try {
            delegate.updateLong("foo", 1L);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateLong("foo", 1L);
    }

    @Test
    public void testUpdateNCharacterStreamIntegerReader() throws Exception {
        try {
            delegate.updateNCharacterStream(1, null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateNCharacterStream(1, null);
    }

    @Test
    public void testUpdateNCharacterStreamIntegerReaderLong() throws Exception {
        try {
            delegate.updateNCharacterStream(1, null, 1L);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateNCharacterStream(1, null, 1L);
    }

    @Test
    public void testUpdateNCharacterStreamStringReader() throws Exception {
        try {
            delegate.updateNCharacterStream("foo", null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateNCharacterStream("foo", null);
    }

    @Test
    public void testUpdateNCharacterStreamStringReaderLong() throws Exception {
        try {
            delegate.updateNCharacterStream("foo", null, 1L);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateNCharacterStream("foo", null, 1L);
    }

    @Test
    public void testUpdateNClobIntegerNClob() throws Exception {
        try {
            delegate.updateNClob(1, (java.sql.NClob) null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateNClob(1, (java.sql.NClob) null);
    }

    @Test
    public void testUpdateNClobIntegerReader() throws Exception {
        try {
            delegate.updateNClob(1, (java.io.StringReader) null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateNClob(1, (java.io.StringReader) null);
    }

    @Test
    public void testUpdateNClobIntegerReaderLong() throws Exception {
        try {
            delegate.updateNClob(1, null, 1L);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateNClob(1, null, 1L);
    }

    @Test
    public void testUpdateNClobStringNClob() throws Exception {
        try {
            delegate.updateNClob("foo", (java.sql.NClob) null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateNClob("foo", (java.sql.NClob) null);
    }

    @Test
    public void testUpdateNClobStringReader() throws Exception {
        try {
            delegate.updateNClob("foo", (java.io.StringReader) null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateNClob("foo", (java.io.StringReader) null);
    }

    @Test
    public void testUpdateNClobStringReaderLong() throws Exception {
        try {
            delegate.updateNClob("foo", null, 1L);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateNClob("foo", null, 1L);
    }

    @Test
    public void testUpdateNStringIntegerString() throws Exception {
        try {
            delegate.updateNString(1, "foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateNString(1, "foo");
    }

    @Test
    public void testUpdateNStringStringString() throws Exception {
        try {
            delegate.updateNString("foo", "foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateNString("foo", "foo");
    }

    @Test
    public void testUpdateNullInteger() throws Exception {
        try {
            delegate.updateNull(1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateNull(1);
    }

    @Test
    public void testUpdateNullString() throws Exception {
        try {
            delegate.updateNull("foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateNull("foo");
    }

    @Test
    public void testUpdateObjectIntegerObject() throws Exception {
        try {
            delegate.updateObject(1, System.err);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateObject(1, System.err);
    }

    @Test
    public void testUpdateObjectIntegerObjectSQLType() throws Exception {
        try {
            delegate.updateObject(1, System.err, null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateObject(1, System.err, null);
    }

    @Test
    public void testUpdateObjectIntegerObjectSQLTypeInteger() throws Exception {
        try {
            delegate.updateObject(1, System.err, null, 1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateObject(1, System.err, null, 1);
    }

    @Test
    public void testUpdateObjectStringObject() throws Exception {
        try {
            delegate.updateObject("foo", System.err);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateObject("foo", System.err);
    }

    @Test
    public void testUpdateObjectStringObjectSQLType() throws Exception {
        try {
            delegate.updateObject("foo", System.err, null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateObject("foo", System.err, null);
    }

    @Test
    public void testUpdateObjectStringObjectSQLTypeInteger() throws Exception {
        try {
            delegate.updateObject("foo", System.err, null, 1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateObject("foo", System.err, null, 1);
    }

    @Test
    public void testUpdateRefIntegerRef() throws Exception {
        try {
            delegate.updateRef(1, null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateRef(1, null);
    }

    @Test
    public void testUpdateRefStringRef() throws Exception {
        try {
            delegate.updateRef("foo", null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateRef("foo", null);
    }

    @Test
    public void testUpdateRow() throws Exception {
        try {
            delegate.updateRow();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateRow();
    }

    @Test
    public void testUpdateRowIdIntegerRowId() throws Exception {
        try {
            delegate.updateRowId(1, null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateRowId(1, null);
    }

    @Test
    public void testUpdateRowIdStringRowId() throws Exception {
        try {
            delegate.updateRowId("foo", null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateRowId("foo", null);
    }

    @Test
    public void testUpdateShortIntegerShort() throws Exception {
        try {
            delegate.updateShort(1, (short) 1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateShort(1, (short) 1);
    }

    @Test
    public void testUpdateShortStringShort() throws Exception {
        try {
            delegate.updateShort("foo", (short) 1);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateShort("foo", (short) 1);
    }

    @Test
    public void testUpdateSQLXMLIntegerSQLXML() throws Exception {
        try {
            delegate.updateSQLXML(1, null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateSQLXML(1, null);
    }

    @Test
    public void testUpdateSQLXMLStringSQLXML() throws Exception {
        try {
            delegate.updateSQLXML("foo", null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateSQLXML("foo", null);
    }

    @Test
    public void testUpdateStringIntegerString() throws Exception {
        try {
            delegate.updateString(1, "foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateString(1, "foo");
    }

    @Test
    public void testUpdateStringStringString() throws Exception {
        try {
            delegate.updateString("foo", "foo");
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateString("foo", "foo");
    }

    @Test
    public void testUpdateTimeIntegerTime() throws Exception {
        try {
            delegate.updateTime(1, null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateTime(1, null);
    }

    @Test
    public void testUpdateTimestampIntegerTimestamp() throws Exception {
        try {
            delegate.updateTimestamp(1, null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateTimestamp(1, null);
    }

    @Test
    public void testUpdateTimestampStringTimestamp() throws Exception {
        try {
            delegate.updateTimestamp("foo", null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateTimestamp("foo", null);
    }

    @Test
    public void testUpdateTimeStringTime() throws Exception {
        try {
            delegate.updateTime("foo", null);
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).updateTime("foo", null);
    }

    @Test
    public void testWasNull() throws Exception {
        try {
            delegate.wasNull();
        } catch (final SQLException e) {
        }
        verify(rs, times(1)).wasNull();
    }

    @Test
    public void testWrap() throws SQLException {
        final DelegatingResultSet delegate = (DelegatingResultSet) DelegatingResultSet.wrapResultSet(conn, rs);
        Assertions.assertEquals(delegate, delegate.unwrap(ResultSet.class));
        Assertions.assertEquals(delegate, delegate.unwrap(DelegatingResultSet.class));
        Assertions.assertEquals(rs, delegate.unwrap(rs.getClass()));
        Assertions.assertNull(delegate.unwrap(String.class));
        Assertions.assertTrue(delegate.isWrapperFor(ResultSet.class));
        Assertions.assertTrue(delegate.isWrapperFor(DelegatingResultSet.class));
        Assertions.assertTrue(delegate.isWrapperFor(rs.getClass()));
        Assertions.assertFalse(delegate.isWrapperFor(String.class));
    }
}
