/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.HikariCP;

import java.io.PrintWriter;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.logging.Logger;
import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ProxyConnectionClosedConnectionTest {

    @Test
    void closeSwitchesPooledConnectionToClosedConnectionProxy() throws SQLException {
        try (HikariDataSource dataSource = new HikariDataSource()) {
            dataSource.setDataSource(new StubDataSource());
            dataSource.setMaximumPoolSize(1);
            dataSource.setMinimumIdle(0);

            Connection connection = dataSource.getConnection();

            assertThat(connection.isClosed()).isFalse();

            connection.close();

            assertThat(connection.isClosed()).isTrue();
            assertThat(connection.isValid(1)).isFalse();
            assertThatThrownBy(() -> connection.prepareStatement("select 1"))
                    .isInstanceOf(SQLException.class)
                    .hasMessage("Connection is closed");
        }
    }

    private static final class StubDataSource implements DataSource {
        private int loginTimeout;

        @Override
        public Connection getConnection() {
            return new StubConnection();
        }

        @Override
        public Connection getConnection(String username, String password) {
            return getConnection();
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
            loginTimeout = seconds;
        }

        @Override
        public int getLoginTimeout() {
            return loginTimeout;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }

            throw new SQLException("Not a wrapper for " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }

    private static final class StubConnection implements Connection {
        private boolean autoCommit = true;
        private boolean readOnly;
        private boolean closed;
        private int networkTimeout;
        private int transactionIsolation = Connection.TRANSACTION_READ_COMMITTED;

        @Override
        public Statement createStatement() {
            return null;
        }

        @Override
        public PreparedStatement prepareStatement(String sql) {
            return null;
        }

        @Override
        public CallableStatement prepareCall(String sql) {
            return null;
        }

        @Override
        public String nativeSQL(String sql) {
            return sql;
        }

        @Override
        public void setAutoCommit(boolean autoCommit) {
            this.autoCommit = autoCommit;
        }

        @Override
        public boolean getAutoCommit() {
            return autoCommit;
        }

        @Override
        public void commit() {
        }

        @Override
        public void rollback() {
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public DatabaseMetaData getMetaData() {
            return null;
        }

        @Override
        public void setReadOnly(boolean readOnly) {
            this.readOnly = readOnly;
        }

        @Override
        public boolean isReadOnly() {
            return readOnly;
        }

        @Override
        public void setCatalog(String catalog) {
        }

        @Override
        public String getCatalog() {
            return null;
        }

        @Override
        public void setTransactionIsolation(int level) {
            transactionIsolation = level;
        }

        @Override
        public int getTransactionIsolation() {
            return transactionIsolation;
        }

        @Override
        public SQLWarning getWarnings() {
            return null;
        }

        @Override
        public void clearWarnings() {
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency) {
            return null;
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) {
            return null;
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) {
            return null;
        }

        @Override
        public Map<String, Class<?>> getTypeMap() {
            return null;
        }

        @Override
        public void setTypeMap(Map<String, Class<?>> map) {
        }

        @Override
        public void setHoldability(int holdability) {
        }

        @Override
        public int getHoldability() {
            return 0;
        }

        @Override
        public Savepoint setSavepoint() {
            return null;
        }

        @Override
        public Savepoint setSavepoint(String name) {
            return null;
        }

        @Override
        public void rollback(Savepoint savepoint) {
        }

        @Override
        public void releaseSavepoint(Savepoint savepoint) {
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) {
            return null;
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) {
            return null;
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) {
            return null;
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) {
            return null;
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int[] columnIndexes) {
            return null;
        }

        @Override
        public PreparedStatement prepareStatement(String sql, String[] columnNames) {
            return null;
        }

        @Override
        public Clob createClob() {
            return null;
        }

        @Override
        public Blob createBlob() {
            return null;
        }

        @Override
        public NClob createNClob() {
            return null;
        }

        @Override
        public SQLXML createSQLXML() {
            return null;
        }

        @Override
        public boolean isValid(int timeout) {
            return !closed;
        }

        @Override
        public void setClientInfo(String name, String value) {
        }

        @Override
        public void setClientInfo(Properties properties) {
        }

        @Override
        public String getClientInfo(String name) {
            return null;
        }

        @Override
        public Properties getClientInfo() {
            return new Properties();
        }

        @Override
        public Array createArrayOf(String typeName, Object[] elements) {
            return null;
        }

        @Override
        public Struct createStruct(String typeName, Object[] attributes) {
            return null;
        }

        @Override
        public void setSchema(String schema) {
        }

        @Override
        public String getSchema() {
            return null;
        }

        @Override
        public void abort(Executor executor) {
            closed = true;
        }

        @Override
        public void setNetworkTimeout(Executor executor, int milliseconds) {
            networkTimeout = milliseconds;
        }

        @Override
        public int getNetworkTimeout() {
            return networkTimeout;
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }

            throw new SQLException("Not a wrapper for " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }

        @Override
        public String toString() {
            return "StubConnection";
        }
    }
}
