/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.commons.dbcp2;

import org_apache_commons.commons_dbcp2.TesterPreparedStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SuppressWarnings({"deprecation", "rawtypes"})
public class TestDelegatingPreparedStatement {

    private TesterConnection testerConn;
    private DelegatingConnection connection;
    private PreparedStatement obj;
    private DelegatingPreparedStatement delegate;

    @BeforeEach
    public void setUp() {
        testerConn = new TesterConnection("test", "test");
        connection = new DelegatingConnection<>(testerConn);
        obj = mock(PreparedStatement.class);
        delegate = new DelegatingPreparedStatement(connection, obj);
    }

    @Test
    public void testAddBatch() throws Exception {
        try {
            delegate.addBatch();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).addBatch();
    }

    @Test
    public void testClearParameters() throws Exception {
        try {
            delegate.clearParameters();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).clearParameters();
    }

    @Test
    public void testExecute() throws Exception {
        try {
            delegate.execute();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).execute();
    }

    @Test
    public void testExecuteLargeUpdate() throws Exception {
        try {
            delegate.executeLargeUpdate();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).executeLargeUpdate();
    }

    @Test
    public void testExecuteQuery() throws Exception {
        try {
            delegate.executeQuery();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).executeQuery();
    }

    @Test
    public void testExecuteQueryReturnsNotNull() throws Exception {
        obj = new TesterPreparedStatement(testerConn, "select * from foo");
        delegate = new DelegatingPreparedStatement(connection, obj);
        assertNotNull(delegate.executeQuery());
    }

    @Test
    public void testExecuteQueryReturnsNull() throws Exception {
        obj = new TesterPreparedStatement(testerConn, "null");
        delegate = new DelegatingPreparedStatement(connection, obj);
        assertNull(delegate.executeQuery());
    }

    @Test
    public void testExecuteUpdate() throws Exception {
        try {
            delegate.executeUpdate();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).executeUpdate();
    }

    @Test
    public void testGetDelegate() {
        obj = new TesterPreparedStatement(testerConn, "select * from foo");
        delegate = new DelegatingPreparedStatement(connection, obj);
        assertEquals(obj, delegate.getDelegate());
    }

    @Test
    public void testGetMetaData() throws Exception {
        try {
            delegate.getMetaData();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getMetaData();
    }

    @Test
    public void testGetParameterMetaData() throws Exception {
        try {
            delegate.getParameterMetaData();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getParameterMetaData();
    }

    @Test
    public void testSetArrayIntegerArray() throws Exception {
        try {
            delegate.setArray(1, null);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setArray(1, null);
    }

    @Test
    public void testSetAsciiStreamIntegerInputStream() throws Exception {
        try {
            delegate.setAsciiStream(1, null);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setAsciiStream(1, null);
    }

    @Test
    public void testSetAsciiStreamIntegerInputStreamInteger() throws Exception {
        try {
            delegate.setAsciiStream(1, null, 1);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setAsciiStream(1, null, 1);
    }

    @Test
    public void testSetAsciiStreamIntegerInputStreamLong() throws Exception {
        try {
            delegate.setAsciiStream(1, null, 1L);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setAsciiStream(1, null, 1L);
    }

    @Test
    public void testSetBigDecimalIntegerBigDecimal() throws Exception {
        try {
            delegate.setBigDecimal(1, java.math.BigDecimal.valueOf(1.0d));
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setBigDecimal(1, java.math.BigDecimal.valueOf(1.0d));
    }

    @Test
    public void testSetBinaryStreamIntegerInputStream() throws Exception {
        try {
            delegate.setBinaryStream(1, null);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setBinaryStream(1, null);
    }

    @Test
    public void testSetBinaryStreamIntegerInputStreamInteger() throws Exception {
        try {
            delegate.setBinaryStream(1, null, 1);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setBinaryStream(1, null, 1);
    }

    @Test
    public void testSetBinaryStreamIntegerInputStreamLong() throws Exception {
        try {
            delegate.setBinaryStream(1, null, 1L);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setBinaryStream(1, null, 1L);
    }

    @Test
    public void testSetBlobIntegerBlob() throws Exception {
        try {
            delegate.setBlob(1, (java.sql.Blob) null);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setBlob(1, (java.sql.Blob) null);
    }

    @Test
    public void testSetBlobIntegerInputStream() throws Exception {
        try {
            delegate.setBlob(1, (java.io.InputStream) null);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setBlob(1, (java.io.InputStream) null);
    }

    @Test
    public void testSetBlobIntegerInputStreamLong() throws Exception {
        try {
            delegate.setBlob(1, null, 1L);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setBlob(1, null, 1L);
    }

    @Test
    public void testSetBooleanIntegerBoolean() throws Exception {
        try {
            delegate.setBoolean(1, Boolean.TRUE);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setBoolean(1, Boolean.TRUE);
    }

    @Test
    public void testSetByteIntegerByte() throws Exception {
        try {
            delegate.setByte(1, (byte) 1);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setByte(1, (byte) 1);
    }

    @Test
    public void testSetBytesIntegerByteArray() throws Exception {
        try {
            delegate.setBytes(1, new byte[]{1});
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setBytes(1, new byte[]{1});
    }

    @Test
    public void testSetCharacterStreamIntegerReader() throws Exception {
        try {
            delegate.setCharacterStream(1, null);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setCharacterStream(1, null);
    }

    @Test
    public void testSetCharacterStreamIntegerReaderInteger() throws Exception {
        try {
            delegate.setCharacterStream(1, null, 1);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setCharacterStream(1, null, 1);
    }

    @Test
    public void testSetCharacterStreamIntegerReaderLong() throws Exception {
        try {
            delegate.setCharacterStream(1, null, 1L);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setCharacterStream(1, null, 1L);
    }

    @Test
    public void testSetClobIntegerClob() throws Exception {
        try {
            delegate.setClob(1, (java.sql.Clob) null);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setClob(1, (java.sql.Clob) null);
    }

    @Test
    public void testSetClobIntegerReader() throws Exception {
        try {
            delegate.setClob(1, (java.io.StringReader) null);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setClob(1, (java.io.StringReader) null);
    }

    @Test
    public void testSetClobIntegerReaderLong() throws Exception {
        try {
            delegate.setClob(1, null, 1L);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setClob(1, null, 1L);
    }

    @Test
    public void testSetDateIntegerSqlDate() throws Exception {
        try {
            delegate.setDate(1, new java.sql.Date(1529827548745L));
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setDate(1, new java.sql.Date(1529827548745L));
    }

    @Test
    public void testSetDateIntegerSqlDateCalendar() throws Exception {
        try {
            delegate.setDate(1, new java.sql.Date(1529827548745L), null);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setDate(1, new java.sql.Date(1529827548745L), null);
    }

    @Test
    public void testSetDoubleIntegerDouble() throws Exception {
        try {
            delegate.setDouble(1, 1.0d);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setDouble(1, 1.0d);
    }

    @Test
    public void testSetFloatIntegerFloat() throws Exception {
        try {
            delegate.setFloat(1, 1.0f);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setFloat(1, 1.0f);
    }

    @Test
    public void testSetIntIntegerInteger() throws Exception {
        try {
            delegate.setInt(1, 1);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setInt(1, 1);
    }

    @Test
    public void testSetLongIntegerLong() throws Exception {
        try {
            delegate.setLong(1, 1L);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setLong(1, 1L);
    }

    @Test
    public void testSetNCharacterStreamIntegerReader() throws Exception {
        try {
            delegate.setNCharacterStream(1, null);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setNCharacterStream(1, null);
    }

    @Test
    public void testSetNCharacterStreamIntegerReaderLong() throws Exception {
        try {
            delegate.setNCharacterStream(1, null, 1L);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setNCharacterStream(1, null, 1L);
    }

    @Test
    public void testSetNClobIntegerNClob() throws Exception {
        try {
            delegate.setNClob(1, (java.sql.NClob) null);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setNClob(1, (java.sql.NClob) null);
    }

    @Test
    public void testSetNClobIntegerReader() throws Exception {
        try {
            delegate.setNClob(1, (java.io.StringReader) null);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setNClob(1, (java.io.StringReader) null);
    }

    @Test
    public void testSetNClobIntegerReaderLong() throws Exception {
        try {
            delegate.setNClob(1, null, 1L);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setNClob(1, null, 1L);
    }

    @Test
    public void testSetNStringIntegerString() throws Exception {
        try {
            delegate.setNString(1, "foo");
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setNString(1, "foo");
    }

    @Test
    public void testSetNullIntegerInteger() throws Exception {
        try {
            delegate.setNull(1, 1);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setNull(1, 1);
    }

    @Test
    public void testSetNullIntegerIntegerString() throws Exception {
        try {
            delegate.setNull(1, 1, "foo");
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setNull(1, 1, "foo");
    }

    @Test
    public void testSetObjectIntegerObject() throws Exception {
        try {
            delegate.setObject(1, System.err);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setObject(1, System.err);
    }

    @Test
    public void testSetObjectIntegerObjectInteger() throws Exception {
        try {
            delegate.setObject(1, System.err, 1);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setObject(1, System.err, 1);
    }

    @Test
    public void testSetObjectIntegerObjectIntegerInteger() throws Exception {
        try {
            delegate.setObject(1, System.err, 1, 1);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setObject(1, System.err, 1, 1);
    }

    @Test
    public void testSetObjectIntegerObjectSQLType() throws Exception {
        try {
            delegate.setObject(1, System.err, null);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setObject(1, System.err, null);
    }

    @Test
    public void testSetObjectIntegerObjectSQLTypeInteger() throws Exception {
        try {
            delegate.setObject(1, System.err, null, 1);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setObject(1, System.err, null, 1);
    }

    @Test
    public void testSetRefIntegerRef() throws Exception {
        try {
            delegate.setRef(1, null);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setRef(1, null);
    }

    @Test
    public void testSetRowIdIntegerRowId() throws Exception {
        try {
            delegate.setRowId(1, null);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setRowId(1, null);
    }

    @Test
    public void testSetShortIntegerShort() throws Exception {
        try {
            delegate.setShort(1, (short) 1);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setShort(1, (short) 1);
    }

    @Test
    public void testSetSQLXMLIntegerSQLXML() throws Exception {
        try {
            delegate.setSQLXML(1, null);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setSQLXML(1, null);
    }

    @Test
    public void testSetStringIntegerString() throws Exception {
        try {
            delegate.setString(1, "foo");
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setString(1, "foo");
    }

    @Test
    public void testSetTimeIntegerTime() throws Exception {
        try {
            delegate.setTime(1, null);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setTime(1, null);
    }

    @Test
    public void testSetTimeIntegerTimeCalendar() throws Exception {
        try {
            delegate.setTime(1, null, null);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setTime(1, null, null);
    }

    @Test
    public void testSetTimestampIntegerTimestamp() throws Exception {
        try {
            delegate.setTimestamp(1, null);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setTimestamp(1, null);
    }

    @Test
    public void testSetTimestampIntegerTimestampCalendar() throws Exception {
        try {
            delegate.setTimestamp(1, null, null);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setTimestamp(1, null, null);
    }

    @Test
    public void testSetUnicodeStreamIntegerInputStreamInteger() throws Exception {
        try {
            delegate.setUnicodeStream(1, null, 1);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setUnicodeStream(1, null, 1);
    }

    @Test
    public void testSetURLIntegerUrl() throws Exception {
        try {
            delegate.setURL(1, null);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).setURL(1, null);
    }

}
