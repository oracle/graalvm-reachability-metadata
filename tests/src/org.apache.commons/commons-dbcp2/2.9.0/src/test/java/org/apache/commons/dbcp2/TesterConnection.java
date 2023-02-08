/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.commons.dbcp2;

import org_apache_commons.commons_dbcp2.TesterDatabaseMetaData;
import org_apache_commons.commons_dbcp2.TesterPreparedStatement;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

@SuppressWarnings({"unused", "MagicConstant"})
public class TesterConnection extends AbandonedTrace implements Connection {

    protected boolean _open = true;
    protected boolean _aborted;
    protected boolean _autoCommit = true;
    protected int _transactionIsolation = 1;
    protected final DatabaseMetaData _metaData = new TesterDatabaseMetaData();
    protected String _catalog;
    protected String schema;
    protected Map<String, Class<?>> _typeMap;
    protected boolean _readOnly;
    protected SQLWarning warnings;
    protected final String userName;
    protected Exception failure;
    protected boolean sqlExceptionOnClose;

    TesterConnection(final String userName,
                     @SuppressWarnings("unused") final String password) {
        this.userName = userName;
    }

    @Override
    public void abort(final Executor executor) throws SQLException {
        checkFailure();
        _aborted = true;
        _open = false;
    }

    protected void checkFailure() throws SQLException {
        if (failure != null) {
            if (failure instanceof SQLException) {
                throw (SQLException) failure;
            }
            throw new SQLException("TesterConnection failure", failure);
        }
    }

    protected void checkOpen() throws SQLException {
        if (!_open) {
            throw new SQLException("Connection is closed.");
        }
        checkFailure();
    }

    @Override
    public void clearWarnings() throws SQLException {
        checkOpen();
        warnings = null;
    }

    @Override
    public void close() throws SQLException {
        checkFailure();
        _open = false;
    }

    @Override
    public void commit() throws SQLException {
        checkOpen();
        if (isReadOnly()) {
            throw new SQLException("Cannot commit a readonly connection");
        }
    }

    @Override
    public Array createArrayOf(final String typeName, final Object[] elements) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public Blob createBlob() throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public Clob createClob() throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public NClob createNClob() throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public Statement createStatement() throws SQLException {
        checkOpen();
        return new TesterStatement(this);
    }

    @Override
    public Statement createStatement(final int resultSetType, final int resultSetConcurrency) throws SQLException {
        checkOpen();
        return new TesterStatement(this);
    }

    @Override
    public Statement createStatement(final int resultSetType,
                                     final int resultSetConcurrency,
                                     final int resultSetHoldability)
            throws SQLException {
        return createStatement();
    }

    @Override
    public Struct createStruct(final String typeName, final Object[] attributes) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        checkOpen();
        return _autoCommit;
    }

    @Override
    public String getCatalog() throws SQLException {
        checkOpen();
        return _catalog;
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public String getClientInfo(final String name) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public int getHoldability() throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        checkOpen();
        return _metaData;
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public String getSchema() throws SQLException {
        checkOpen();
        return schema;
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        checkOpen();
        return _transactionIsolation;
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        checkOpen();
        return _typeMap;
    }

    public String getUserName() {
        return this.userName;
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        checkOpen();
        return warnings;
    }

    public boolean isAborted() throws SQLException {
        checkFailure();
        return _aborted;
    }

    @Override
    public boolean isClosed() throws SQLException {
        checkFailure();
        return !_open;
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        checkOpen();
        return _readOnly;
    }

    public boolean isSqlExceptionOnClose() {
        return sqlExceptionOnClose;
    }

    @Override
    public boolean isValid(final int timeout) {
        return _open;
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public String nativeSQL(final String sql) throws SQLException {
        checkOpen();
        return sql;
    }

    @Override
    public CallableStatement prepareCall(final String sql) throws SQLException {
        checkOpen();
        if ("warning".equals(sql)) {
            setWarnings(new SQLWarning("warning in prepareCall"));
        }
        return new TesterCallableStatement(this, sql);
    }

    @Override
    public CallableStatement prepareCall(final String sql, final int resultSetType, final int resultSetConcurrency) throws SQLException {
        checkOpen();
        return new TesterCallableStatement(this, sql, resultSetType, resultSetConcurrency);
    }

    @Override
    public CallableStatement prepareCall(final String sql, final int resultSetType,
                                         final int resultSetConcurrency,
                                         final int resultSetHoldability)
            throws SQLException {
        checkOpen();
        return new TesterCallableStatement(this, sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql) throws SQLException {
        checkOpen();
        if ("null".equals(sql)) {
            return null;
        }
        if ("invalid".equals(sql)) {
            throw new SQLException("invalid query");
        }
        if ("broken".equals(sql)) {
            throw new SQLException("broken connection");
        }
        return new TesterPreparedStatement(this, sql);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int autoGeneratedKeys)
            throws SQLException {
        checkOpen();
        return new TesterPreparedStatement(this, sql, autoGeneratedKeys);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency) throws SQLException {
        checkOpen();
        return new TesterPreparedStatement(this, sql, resultSetType, resultSetConcurrency);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType,
                                              final int resultSetConcurrency,
                                              final int resultSetHoldability)
            throws SQLException {
        checkOpen();
        return new TesterPreparedStatement(this, sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int[] columnIndexes)
            throws SQLException {
        return new TesterPreparedStatement(this, sql, columnIndexes);
    }


    @Override
    public PreparedStatement prepareStatement(final String sql, final String[] columnNames)
            throws SQLException {
        return new TesterPreparedStatement(this, sql, columnNames);
    }

    @Override
    public void releaseSavepoint(final java.sql.Savepoint savepoint) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void rollback() throws SQLException {
        checkOpen();
        if (isReadOnly()) {
            throw new SQLException("Cannot rollback a readonly connection");
        }
        if (getAutoCommit()) {
            throw new SQLException("Cannot rollback a connection in auto-commit");
        }
    }

    @Override
    public void rollback(final java.sql.Savepoint savepoint) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void setAutoCommit(final boolean autoCommit) throws SQLException {
        checkOpen();
        _autoCommit = autoCommit;
    }

    @Override
    public void setCatalog(final String catalog) throws SQLException {
        checkOpen();
        _catalog = catalog;
    }

    @Override
    public void setClientInfo(final Properties properties) throws SQLClientInfoException {
        throw new SQLClientInfoException();
    }

    @Override
    public void setClientInfo(final String name, final String value) throws SQLClientInfoException {
        throw new SQLClientInfoException();
    }

    public void setFailure(final Exception failure) {
        this.failure = failure;
    }

    @Override
    public void setHoldability(final int holdability) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void setNetworkTimeout(final Executor executor, final int milliseconds)
            throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void setReadOnly(final boolean readOnly) throws SQLException {
        checkOpen();
        _readOnly = readOnly;
    }

    @Override
    public java.sql.Savepoint setSavepoint() throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public java.sql.Savepoint setSavepoint(final String name) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void setSchema(final String schema) throws SQLException {
        checkOpen();
        this.schema = schema;
    }

    public void setSqlExceptionOnClose(final boolean sqlExceptionOnClose) {
        this.sqlExceptionOnClose = sqlExceptionOnClose;
    }

    @Override
    public void setTransactionIsolation(final int level) throws SQLException {
        checkOpen();
        _transactionIsolation = level;
    }

    @Override
    public void setTypeMap(final Map<String, Class<?>> map) throws SQLException {
        checkOpen();
        _typeMap = map;
    }

    public void setWarnings(final SQLWarning warning) {
        this.warnings = warning;
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        throw new SQLException("Not implemented.");
    }
}
