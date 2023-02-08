/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.commons.dbcp2;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLType;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Map;

@SuppressWarnings({"unused", "MagicConstant"})
public class TesterResultSet extends AbandonedTrace implements ResultSet {

    protected int _type = ResultSet.TYPE_FORWARD_ONLY;
    protected int _concurrency = ResultSet.CONCUR_READ_ONLY;
    protected Object[][] _data;
    protected int _currentRow = -1;
    protected Statement _statement;
    protected int _rowsLeft = 2;
    protected boolean _open = true;
    protected boolean _sqlExceptionOnClose;

    public TesterResultSet(final Statement stmt) {
        _statement = stmt;
    }

    public TesterResultSet(final Statement stmt, final int type, final int concurrency) {
        _statement = stmt;
        _data = null;
        _type = type;
        _concurrency = concurrency;
    }

    public TesterResultSet(final Statement stmt, final Object[][] data) {
        _statement = stmt;
        _data = data;
    }

    @Override
    public boolean absolute(final int row) throws SQLException {
        checkOpen();
        return false;
    }

    @Override
    public void afterLast() throws SQLException {
        checkOpen();
    }

    @Override
    public void beforeFirst() throws SQLException {
        checkOpen();
    }

    @Override
    public void cancelRowUpdates() throws SQLException {
        checkOpen();
    }

    protected void checkOpen() throws SQLException {
        if (!_open) {
            throw new SQLException("ResultSet is closed.");
        }
    }

    @Override
    public void clearWarnings() throws SQLException {
        checkOpen();
    }

    @Override
    public void close() throws SQLException {
        if (_sqlExceptionOnClose) {
            throw new SQLException("TestSQLExceptionOnClose");
        }
        if (!_open) {
            return;
        }
        if (_statement != null) {
            ((TesterStatement) _statement)._resultSet = null;
        }

        _open = false;
    }

    @Override
    public void deleteRow() throws SQLException {
        checkOpen();
    }

    @Override
    public int findColumn(final String columnName) throws SQLException {
        checkOpen();
        return 1;
    }

    @Override
    public boolean first() throws SQLException {
        checkOpen();
        return false;
    }

    @Override
    public Array getArray(final int i) throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public Array getArray(final String colName) throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public InputStream getAsciiStream(final int columnIndex) throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public InputStream getAsciiStream(final String columnName) throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public BigDecimal getBigDecimal(final int columnIndex) throws SQLException {
        checkOpen();
        return new BigDecimal(columnIndex);
    }

    @Deprecated
    @Override
    public BigDecimal getBigDecimal(final int columnIndex, final int scale) throws SQLException {
        checkOpen();
        return new BigDecimal(columnIndex);
    }

    @Override
    public BigDecimal getBigDecimal(final String columnName) throws SQLException {
        checkOpen();
        return new BigDecimal(columnName.hashCode());
    }

    @Deprecated
    @Override
    public BigDecimal getBigDecimal(final String columnName, final int scale) throws SQLException {
        checkOpen();
        return new BigDecimal(columnName.hashCode());
    }

    @Override
    public InputStream getBinaryStream(final int columnIndex) throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public InputStream getBinaryStream(final String columnName) throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public Blob getBlob(final int i) throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public Blob getBlob(final String colName) throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public boolean getBoolean(final int columnIndex) throws SQLException {
        checkOpen();
        return true;
    }

    @Override
    public boolean getBoolean(final String columnName) throws SQLException {
        checkOpen();
        return true;
    }

    @Override
    public byte getByte(final int columnIndex) throws SQLException {
        checkOpen();
        return (byte) columnIndex;
    }

    @Override
    public byte getByte(final String columnName) throws SQLException {
        checkOpen();
        return (byte) columnName.hashCode();
    }

    @Override
    public byte[] getBytes(final int columnIndex) throws SQLException {
        checkOpen();
        return new byte[]{(byte) columnIndex};
    }

    @Override
    public byte[] getBytes(final String columnName) throws SQLException {
        checkOpen();
        return columnName.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Reader getCharacterStream(final int columnIndex) throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public Reader getCharacterStream(final String columnName) throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public Clob getClob(final int i) throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public Clob getClob(final String colName) throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public int getConcurrency() {
        return this._concurrency;
    }

    @Override
    public String getCursorName() throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public Date getDate(final int columnIndex) throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public Date getDate(final int columnIndex, final Calendar cal) throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public Date getDate(final String columnName) throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public Date getDate(final String columnName, final Calendar cal) throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public double getDouble(final int columnIndex) throws SQLException {
        checkOpen();
        return columnIndex;
    }

    @Override
    public double getDouble(final String columnName) throws SQLException {
        checkOpen();
        return columnName.hashCode();
    }

    @Override
    public int getFetchDirection() throws SQLException {
        checkOpen();
        return 1;
    }

    @Override
    public int getFetchSize() throws SQLException {
        checkOpen();
        return 2;
    }


    @Override
    public float getFloat(final int columnIndex) throws SQLException {
        checkOpen();
        return columnIndex;
    }

    @Override
    public float getFloat(final String columnName) throws SQLException {
        checkOpen();
        return columnName.hashCode();
    }

    @Override
    public int getHoldability() throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public int getInt(final int columnIndex) throws SQLException {
        checkOpen();
        return (short) columnIndex;
    }

    @Override
    public int getInt(final String columnName) throws SQLException {
        checkOpen();
        return columnName.hashCode();
    }

    @Override
    public long getLong(final int columnIndex) throws SQLException {
        checkOpen();
        return columnIndex;
    }

    @Override
    public long getLong(final String columnName) throws SQLException {
        checkOpen();
        return columnName.hashCode();
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public Reader getNCharacterStream(final int columnIndex) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public Reader getNCharacterStream(final String columnLabel) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public NClob getNClob(final int columnIndex) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public NClob getNClob(final String columnLabel) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public String getNString(final int columnIndex) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public String getNString(final String columnLabel) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public Object getObject(final int columnIndex) throws SQLException {
        checkOpen();
        if (_data != null) {
            return _data[_currentRow][columnIndex - 1];
        }
        return new Object();
    }

    @Override
    public <T> T getObject(final int columnIndex, final Class<T> type) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public Object getObject(final int i, final Map<String, Class<?>> map) throws SQLException {
        checkOpen();
        return new Object();
    }

    @Override
    public Object getObject(final String columnName) throws SQLException {
        checkOpen();
        return columnName;
    }

    @Override
    public <T> T getObject(final String columnLabel, final Class<T> type)
            throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public Object getObject(final String colName, final Map<String, Class<?>> map) throws SQLException {
        checkOpen();
        return colName;
    }

    @Override
    public Ref getRef(final int i) throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public Ref getRef(final String colName) throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public int getRow() throws SQLException {
        checkOpen();
        return 3 - _rowsLeft;
    }

    @Override
    public RowId getRowId(final int columnIndex) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public RowId getRowId(final String columnLabel) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public short getShort(final int columnIndex) throws SQLException {
        checkOpen();
        return (short) columnIndex;
    }

    @Override
    public short getShort(final String columnName) throws SQLException {
        checkOpen();
        return (short) columnName.hashCode();
    }

    @Override
    public SQLXML getSQLXML(final int columnIndex) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public SQLXML getSQLXML(final String columnLabel) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public Statement getStatement() throws SQLException {
        checkOpen();
        return _statement;
    }

    @Override
    public String getString(final int columnIndex) throws SQLException {
        checkOpen();
        if (columnIndex == -1) {
            throw new SQLException("broken connection");
        }
        if (_data != null) {
            return (String) getObject(columnIndex);
        }
        return "String" + columnIndex;
    }

    @Override
    public String getString(final String columnName) throws SQLException {
        checkOpen();
        return columnName;
    }

    @Override
    public Time getTime(final int columnIndex) throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public Time getTime(final int columnIndex, final Calendar cal) throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public Time getTime(final String columnName) throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public Time getTime(final String columnName, final Calendar cal) throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public Timestamp getTimestamp(final int columnIndex) throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public Timestamp getTimestamp(final int columnIndex, final Calendar cal) throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public Timestamp getTimestamp(final String columnName) throws SQLException {
        checkOpen();
        return null;
    }


    @Override
    public Timestamp getTimestamp(final String columnName, final Calendar cal)
            throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public int getType() {
        return this._type;
    }

    @Deprecated
    @Override
    public InputStream getUnicodeStream(final int columnIndex) throws SQLException {
        checkOpen();
        return null;
    }

    @Deprecated
    @Override
    public InputStream getUnicodeStream(final String columnName) throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public java.net.URL getURL(final int columnIndex) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public java.net.URL getURL(final String columnName) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        checkOpen();
        return null;
    }

    @Override
    public void insertRow() throws SQLException {
        checkOpen();
    }

    @Override
    public boolean isAfterLast() throws SQLException {
        checkOpen();
        return _rowsLeft < 0;
    }

    @Override
    public boolean isBeforeFirst() throws SQLException {
        checkOpen();
        return _rowsLeft == 2;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return !_open;
    }

    @Override
    public boolean isFirst() throws SQLException {
        checkOpen();
        return _rowsLeft == 1;
    }

    @Override
    public boolean isLast() throws SQLException {
        checkOpen();
        return _rowsLeft == 0;
    }

    public boolean isSqlExceptionOnClose() {
        return _sqlExceptionOnClose;
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public boolean last() throws SQLException {
        checkOpen();
        return false;
    }

    @Override
    public void moveToCurrentRow() throws SQLException {
        checkOpen();
    }

    @Override
    public void moveToInsertRow() throws SQLException {
        checkOpen();
    }

    @Override
    public boolean next() throws SQLException {
        checkOpen();
        if (_data != null) {
            _currentRow++;
            return _currentRow < _data.length;
        }
        return --_rowsLeft > 0;
    }

    @Override
    public boolean previous() throws SQLException {
        checkOpen();
        return false;
    }

    @Override
    public void refreshRow() throws SQLException {
        checkOpen();
    }

    @Override
    public boolean relative(final int rows) throws SQLException {
        checkOpen();
        return false;
    }

    @Override
    public boolean rowDeleted() throws SQLException {
        checkOpen();
        return false;
    }

    @Override
    public boolean rowInserted() throws SQLException {
        checkOpen();
        return false;
    }

    @Override
    public boolean rowUpdated() throws SQLException {
        checkOpen();
        return false;
    }

    @Override
    public void setFetchDirection(final int direction) throws SQLException {
        checkOpen();
    }

    @Override
    public void setFetchSize(final int rows) throws SQLException {
        checkOpen();
    }

    public void setSqlExceptionOnClose(final boolean sqlExceptionOnClose) {
        this._sqlExceptionOnClose = sqlExceptionOnClose;
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateArray(final int columnIndex, final Array x)
            throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateArray(final String columnName, final Array x)
            throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateAsciiStream(final int columnIndex, final InputStream inputStream) throws SQLException {
        throw new SQLException("Not implemented.");
    }


    @Override
    public void updateAsciiStream(final int columnIndex, final InputStream inputStream, final long length) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateAsciiStream(final int columnIndex,
                                  final InputStream x,
                                  final int length) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateAsciiStream(final String columnLabel, final InputStream inputStream) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateAsciiStream(final String columnLabel, final InputStream inputStream, final long length) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateAsciiStream(final String columnName,
                                  final InputStream x,
                                  final int length) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateBigDecimal(final int columnIndex, final BigDecimal x) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateBigDecimal(final String columnName, final BigDecimal x) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateBinaryStream(final int columnIndex, final InputStream inputStream) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateBinaryStream(final int columnIndex, final InputStream inputStream, final long length) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateBinaryStream(final int columnIndex,
                                   final InputStream x,
                                   final int length) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateBinaryStream(final String columnLabel, final InputStream inputStream) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateBinaryStream(final String columnLabel, final InputStream inputStream, final long length) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateBinaryStream(final String columnName,
                                   final InputStream x,
                                   final int length) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateBlob(final int columnIndex, final InputStream inputStream) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateBlob(final int columnIndex, final InputStream inputStream, final long length) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateBlob(final int columnIndex, final Blob x)
            throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateBlob(final String columnLabel, final InputStream inputStream) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateBlob(final String columnLabel, final InputStream inputStream, final long length) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateBlob(final String columnName, final Blob x)
            throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateBoolean(final int columnIndex, final boolean x) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateBoolean(final String columnName, final boolean x) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateByte(final int columnIndex, final byte x) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateByte(final String columnName, final byte x) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateBytes(final int columnIndex, final byte[] x) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateBytes(final String columnName, final byte[] x) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateCharacterStream(final int columnIndex,
                                      final Reader x,
                                      final int length) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateCharacterStream(final int columnIndex, final Reader reader) throws SQLException {
        throw new SQLException("Not implemented.");
    }


    @Override
    public void updateCharacterStream(final int columnIndex, final Reader reader, final long length) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateCharacterStream(final String columnName,
                                      final Reader reader,
                                      final int length) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateCharacterStream(final String columnLabel, final Reader reader) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateCharacterStream(final String columnLabel, final Reader reader, final long length) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateClob(final int columnIndex, final Clob x)
            throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateClob(final int columnIndex, final Reader reader) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateClob(final int columnIndex, final Reader reader, final long length) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateClob(final String columnName, final Clob x)
            throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateClob(final String columnLabel, final Reader reader) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateClob(final String columnLabel, final Reader reader, final long length) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateDate(final int columnIndex, final Date x) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateDate(final String columnName, final Date x) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateDouble(final int columnIndex, final double x) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateDouble(final String columnName, final double x) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateFloat(final int columnIndex, final float x) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateFloat(final String columnName, final float x) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateInt(final int columnIndex, final int x) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateInt(final String columnName, final int x) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateLong(final int columnIndex, final long x) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateLong(final String columnName, final long x) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateNCharacterStream(final int columnIndex, final Reader reader) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateNCharacterStream(final int columnIndex, final Reader reader, final long length) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateNCharacterStream(final String columnLabel, final Reader reader) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateNCharacterStream(final String columnLabel, final Reader reader, final long length) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateNClob(final int columnIndex, final NClob value) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateNClob(final int columnIndex, final Reader reader) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateNClob(final int columnIndex, final Reader reader, final long length) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateNClob(final String columnLabel, final NClob value) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateNClob(final String columnLabel, final Reader reader) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateNClob(final String columnLabel, final Reader reader, final long length) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateNString(final int columnIndex, final String value) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateNString(final String columnLabel, final String value) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateNull(final int columnIndex) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateNull(final String columnName) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateObject(final int columnIndex, final Object x) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateObject(final int columnIndex, final Object x, final int scale) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateObject(final int columnIndex, final Object x, final SQLType targetSqlType) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateObject(final int columnIndex, final Object x, final SQLType targetSqlType, final int scaleOrLength) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateObject(final String columnName, final Object x) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateObject(final String columnName, final Object x, final int scale) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateObject(final String columnLabel, final Object x, final SQLType targetSqlType) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateObject(final String columnLabel, final Object x, final SQLType targetSqlType, final int scaleOrLength)
            throws SQLException {
        checkOpen();
    }

    @Override
    public void updateRef(final int columnIndex, final Ref x) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateRef(final String columnName, final Ref x) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateRow() throws SQLException {
        checkOpen();
    }

    @Override
    public void updateRowId(final int columnIndex, final RowId value) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateRowId(final String columnLabel, final RowId value) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateShort(final int columnIndex, final short x) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateShort(final String columnName, final short x) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateSQLXML(final int columnIndex, final SQLXML value) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateSQLXML(final String columnLabel, final SQLXML value) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void updateString(final int columnIndex, final String x) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateString(final String columnName, final String x) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateTime(final int columnIndex, final Time x) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateTime(final String columnName, final Time x) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateTimestamp(final int columnIndex, final Timestamp x) throws SQLException {
        checkOpen();
    }

    @Override
    public void updateTimestamp(final String columnName, final Timestamp x)
            throws SQLException {
        checkOpen();
    }

    @Override
    public boolean wasNull() throws SQLException {
        checkOpen();
        return false;
    }
}
