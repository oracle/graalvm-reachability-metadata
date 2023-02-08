/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.commons.dbcp2;

import org_apache_commons.commons_dbcp2.TesterPreparedStatement;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.RowId;
import java.sql.SQLType;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Map;


@SuppressWarnings("unused")
public class TesterCallableStatement extends TesterPreparedStatement implements CallableStatement {

    public TesterCallableStatement(final Connection conn) {
        super(conn);
    }

    public TesterCallableStatement(final Connection conn, final String sql) {
        super(conn, sql);
    }

    public TesterCallableStatement(final Connection conn, final String sql, final int resultSetType, final int resultSetConcurrency) {
        super(conn, sql, resultSetType, resultSetConcurrency);
    }

    public TesterCallableStatement(final Connection conn, final String sql, final int resultSetType, final int resultSetConcurrency,
                                   final int resultSetHoldability) {
        super(conn, sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public Array getArray(final int i) {
        return null;
    }

    @Override
    public Array getArray(final String parameterName) {
        return null;
    }

    @Override
    public BigDecimal getBigDecimal(final int parameterIndex) {
        return null;
    }

    @Deprecated
    @Override
    public BigDecimal getBigDecimal(final int parameterIndex, final int scale) {
        return null;
    }

    @Override
    public BigDecimal getBigDecimal(final String parameterName) {
        return null;
    }

    @Override
    public Blob getBlob(final int i) {
        return null;
    }

    @Override
    public Blob getBlob(final String parameterName) {
        return null;
    }

    @Override
    public boolean getBoolean(final int parameterIndex) {
        return false;
    }

    @Override
    public boolean getBoolean(final String parameterName) {
        return false;
    }

    @Override
    public byte getByte(final int parameterIndex) {
        return 0;
    }

    @Override
    public byte getByte(final String parameterName) {
        return 0;
    }

    @Override
    public byte[] getBytes(final int parameterIndex) {
        return new byte[0];
    }

    @Override
    public byte[] getBytes(final String parameterName) {
        return new byte[0];
    }

    @Override
    public Reader getCharacterStream(final int parameterIndex) {
        return null;
    }

    @Override
    public Reader getCharacterStream(final String parameterName) {
        return null;
    }

    @Override
    public Clob getClob(final int i) {
        return null;
    }

    @Override
    public Clob getClob(final String parameterName) {
        return null;
    }

    @Override
    public Date getDate(final int parameterIndex) {
        return null;
    }

    @Override
    public Date getDate(final int parameterIndex, final Calendar cal) {
        return null;
    }

    @Override
    public Date getDate(final String parameterName) {
        return null;
    }

    @Override
    public Date getDate(final String parameterName, final Calendar cal) {
        return null;
    }

    @Override
    public double getDouble(final int parameterIndex) {
        return 0;
    }

    @Override
    public double getDouble(final String parameterName) {
        return 0;
    }

    @Override
    public float getFloat(final int parameterIndex) {
        return 0;
    }

    @Override
    public float getFloat(final String parameterName) {
        return 0;
    }

    @Override
    public int getInt(final int parameterIndex) {
        return 0;
    }

    @Override
    public int getInt(final String parameterName) {
        return 0;
    }

    @Override
    public long getLong(final int parameterIndex) {
        return 0;
    }

    @Override
    public long getLong(final String parameterName) {
        return 0;
    }

    @Override
    public Reader getNCharacterStream(final int parameterIndex) {
        return null;
    }

    @Override
    public Reader getNCharacterStream(final String parameterName) {
        return null;
    }

    @Override
    public NClob getNClob(final int parameterIndex) {
        return null;
    }

    @Override
    public NClob getNClob(final String parameterName) {
        return null;
    }

    @Override
    public String getNString(final int parameterIndex) {
        return null;
    }

    @Override
    public String getNString(final String parameterName) {
        return null;
    }

    @Override
    public Object getObject(final int parameterIndex) {
        return null;
    }

    @Override
    public <T> T getObject(final int parameterIndex, final Class<T> type) {
        return null;
    }

    @Override
    public Object getObject(final int i, final Map<String, Class<?>> map) {
        return null;
    }

    @Override
    public Object getObject(final String parameterName) {
        return null;
    }

    @Override
    public <T> T getObject(final String parameterName, final Class<T> type) {
        return null;
    }

    @Override
    public Object getObject(final String parameterName, final Map<String, Class<?>> map) {
        return null;
    }

    @Override
    public Ref getRef(final int i) {
        return null;
    }

    @Override
    public Ref getRef(final String parameterName) {
        return null;
    }

    @Override
    public RowId getRowId(final int parameterIndex) {
        return null;
    }

    @Override
    public RowId getRowId(final String parameterName) {
        return null;
    }

    @Override
    public short getShort(final int parameterIndex) {
        return 0;
    }

    @Override
    public short getShort(final String parameterName) {
        return 0;
    }

    @Override
    public SQLXML getSQLXML(final int parameterIndex) {
        return null;
    }

    @Override
    public SQLXML getSQLXML(final String parameterName) {
        return null;
    }

    @Override
    public String getString(final int parameterIndex) {
        return null;
    }

    @Override
    public String getString(final String parameterName) {
        return null;
    }

    @Override
    public Time getTime(final int parameterIndex) {
        return null;
    }

    @Override
    public Time getTime(final int parameterIndex, final Calendar cal) {
        return null;
    }

    @Override
    public Time getTime(final String parameterName) {
        return null;
    }

    @Override
    public Time getTime(final String parameterName, final Calendar cal) {
        return null;
    }

    @Override
    public Timestamp getTimestamp(final int parameterIndex) {
        return null;
    }

    @Override
    public Timestamp getTimestamp(final int parameterIndex, final Calendar cal) {
        return null;
    }

    @Override
    public Timestamp getTimestamp(final String parameterName) {
        return null;
    }

    @Override
    public Timestamp getTimestamp(final String parameterName, final Calendar cal) {
        return null;
    }

    @Override
    public URL getURL(final int parameterIndex) {
        return null;
    }

    @Override
    public URL getURL(final String parameterName) {
        return null;
    }

    @Override
    public void registerOutParameter(final int parameterIndex, final int sqlType) {
    }

    @Override
    public void registerOutParameter(final int parameterIndex, final int sqlType, final int scale) {
    }

    @Override
    public void registerOutParameter(final int paramIndex, final int sqlType, final String typeName) {
    }

    @Override
    public void registerOutParameter(final int parameterIndex, final SQLType sqlType) {
    }

    @Override
    public void registerOutParameter(final int parameterIndex, final SQLType sqlType, final int scale) {
    }

    @Override
    public void registerOutParameter(final int parameterIndex, final SQLType sqlType, final String typeName) {
    }

    @Override
    public void registerOutParameter(final String parameterName, final int sqlType) {
    }

    @Override
    public void registerOutParameter(final String parameterName, final int sqlType, final int scale) {
    }

    @Override
    public void registerOutParameter(final String parameterName, final int sqlType, final String typeName) {
    }

    @Override
    public void registerOutParameter(final String parameterName, final SQLType sqlType) {
    }

    @Override
    public void registerOutParameter(final String parameterName, final SQLType sqlType, final int scale) {
    }

    @Override
    public void registerOutParameter(final String parameterName, final SQLType sqlType, final String typeName) {
    }

    @Override
    public void setAsciiStream(final String parameterName, final InputStream inputStream) {
    }

    @Override
    public void setAsciiStream(final String parameterName, final InputStream x, final int length) {
    }

    @Override
    public void setAsciiStream(final String parameterName, final InputStream inputStream, final long length) {
    }

    @Override
    public void setBigDecimal(final String parameterName, final BigDecimal x) {
    }

    @Override
    public void setBinaryStream(final String parameterName, final InputStream inputStream) {
    }

    @Override
    public void setBinaryStream(final String parameterName, final InputStream x, final int length) {
    }

    @Override
    public void setBinaryStream(final String parameterName, final InputStream inputStream, final long length) {
    }

    @Override
    public void setBlob(final String parameterName, final Blob blob) {
    }

    @Override
    public void setBlob(final String parameterName, final InputStream inputStream) {
    }

    @Override
    public void setBlob(final String parameterName, final InputStream inputStream, final long length) {
    }

    @Override
    public void setBoolean(final String parameterName, final boolean x) {
    }

    @Override
    public void setByte(final String parameterName, final byte x) {
    }

    @Override
    public void setBytes(final String parameterName, final byte[] x) {
    }

    @Override
    public void setCharacterStream(final String parameterName, final Reader reader) {
    }

    @Override
    public void setCharacterStream(final String parameterName, final Reader reader, final int length) {
    }

    @Override
    public void setCharacterStream(final String parameterName, final Reader reader, final long length) {
    }

    @Override
    public void setClob(final String parameterName, final Clob clob) {
    }

    @Override
    public void setClob(final String parameterName, final Reader reader) {
    }

    @Override
    public void setClob(final String parameterName, final Reader reader, final long length) {
    }

    @Override
    public void setDate(final String parameterName, final Date x) {
    }

    @Override
    public void setDate(final String parameterName, final Date x, final Calendar cal) {
    }

    @Override
    public void setDouble(final String parameterName, final double x) {
    }

    @Override
    public void setFloat(final String parameterName, final float x) {
    }

    @Override
    public void setInt(final String parameterName, final int x) {
    }

    @Override
    public void setLong(final String parameterName, final long x) {
    }

    @Override
    public void setNCharacterStream(final String parameterName, final Reader reader) {
    }

    @Override
    public void setNCharacterStream(final String parameterName, final Reader reader, final long length) {
    }

    @Override
    public void setNClob(final String parameterName, final NClob value) {
    }

    @Override
    public void setNClob(final String parameterName, final Reader reader) {
    }

    @Override
    public void setNClob(final String parameterName, final Reader reader, final long length) {
    }

    @Override
    public void setNString(final String parameterName, final String value) {
    }

    @Override
    public void setNull(final String parameterName, final int sqlType) {
    }

    @Override
    public void setNull(final String parameterName, final int sqlType, final String typeName) {
    }

    @Override
    public void setObject(final String parameterName, final Object x) {
    }

    @Override
    public void setObject(final String parameterName, final Object x, final int targetSqlType) {
    }

    @Override
    public void setObject(final String parameterName, final Object x, final int targetSqlType, final int scale) {
    }

    @Override
    public void setObject(final String parameterName, final Object x, final SQLType targetSqlType) {

    }

    @Override
    public void setObject(final String parameterName, final Object x, final SQLType targetSqlType, final int scaleOrLength) {

    }

    @Override
    public void setRowId(final String parameterName, final RowId value) {
    }

    @Override
    public void setShort(final String parameterName, final short x) {
    }

    @Override
    public void setSQLXML(final String parameterName, final SQLXML value) {
    }

    @Override
    public void setString(final String parameterName, final String x) {
    }

    @Override
    public void setTime(final String parameterName, final Time x) {
    }

    @Override
    public void setTime(final String parameterName, final Time x, final Calendar cal) {
    }

    @Override
    public void setTimestamp(final String parameterName, final Timestamp x) {
    }

    @Override
    public void setTimestamp(final String parameterName, final Timestamp x, final Calendar cal) {
    }

    @Override
    public void setURL(final String parameterName, final URL val) {
    }

    @Override
    public boolean wasNull() {
        return false;
    }
}
