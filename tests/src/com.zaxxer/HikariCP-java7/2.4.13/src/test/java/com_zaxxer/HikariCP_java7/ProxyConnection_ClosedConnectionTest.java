/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.HikariCP_java7;

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
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ProxyConnection_ClosedConnectionTest {
    @Test
    public void closeReplacesDelegateWithClosedConnectionProxy() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setDataSource(new TestDataSource());
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            Connection connection = dataSource.getConnection();

            assertThat(connection.isValid(1)).isTrue();

            connection.close();

            assertThat(connection.isClosed()).isTrue();
            assertThat(connection.isValid(1)).isFalse();
            assertThat(connection.toString()).contains("ClosedConnection");
            assertThatThrownBy(connection::createStatement)
                    .isInstanceOf(SQLException.class)
                    .hasMessage("Connection is closed");
        }
    }

    private static final class TestDataSource implements DataSource {
        private final AtomicLong nextId;
        private PrintWriter logWriter;
        private int loginTimeout;

        private TestDataSource() {
            this.nextId = new AtomicLong();
        }

        @Override
        public Connection getConnection() {
            return new TestConnection(nextId.incrementAndGet());
        }

        @Override
        public Connection getConnection(String username, String password) {
            return getConnection();
        }

        @Override
        public PrintWriter getLogWriter() {
            return logWriter;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            this.logWriter = out;
        }

        @Override
        public void setLoginTimeout(int seconds) {
            this.loginTimeout = seconds;
        }

        @Override
        public int getLoginTimeout() {
            return loginTimeout;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("Unsupported unwrap type: " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }

    private static final class TestConnection implements Connection {
        private final long id;
        private boolean autoCommit;
        private boolean closed;
        private boolean readOnly;
        private int networkTimeout;
        private int transactionIsolation;
        private String catalog;
        private String schema;

        private TestConnection(long id) {
            this.id = id;
            this.autoCommit = true;
            this.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED;
        }

        @Override
        public Statement createStatement() throws SQLException {
            throw unsupported();
        }

        @Override
        public PreparedStatement prepareStatement(String sql) throws SQLException {
            throw unsupported();
        }

        @Override
        public CallableStatement prepareCall(String sql) throws SQLException {
            throw unsupported();
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
            this.closed = true;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public DatabaseMetaData getMetaData() throws SQLException {
            throw unsupported();
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
            this.catalog = catalog;
        }

        @Override
        public String getCatalog() {
            return catalog;
        }

        @Override
        public void setTransactionIsolation(int level) {
            this.transactionIsolation = level;
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
        public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
            throw unsupported();
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            throw unsupported();
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            throw unsupported();
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
        public Savepoint setSavepoint() throws SQLException {
            throw unsupported();
        }

        @Override
        public Savepoint setSavepoint(String name) throws SQLException {
            throw unsupported();
        }

        @Override
        public void rollback(Savepoint savepoint) {
        }

        @Override
        public void releaseSavepoint(Savepoint savepoint) {
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            throw unsupported();
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
                throws SQLException {
            throw unsupported();
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
                throws SQLException {
            throw unsupported();
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
            throw unsupported();
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
            throw unsupported();
        }

        @Override
        public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
            throw unsupported();
        }

        @Override
        public Clob createClob() throws SQLException {
            throw unsupported();
        }

        @Override
        public Blob createBlob() throws SQLException {
            throw unsupported();
        }

        @Override
        public NClob createNClob() throws SQLException {
            throw unsupported();
        }

        @Override
        public SQLXML createSQLXML() throws SQLException {
            throw unsupported();
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
        public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
            throw unsupported();
        }

        @Override
        public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
            throw unsupported();
        }

        @Override
        public void setSchema(String schema) {
            this.schema = schema;
        }

        @Override
        public String getSchema() {
            return schema;
        }

        @Override
        public void abort(Executor executor) {
            this.closed = true;
        }

        @Override
        public void setNetworkTimeout(Executor executor, int milliseconds) {
            this.networkTimeout = milliseconds;
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
            throw new SQLException("Unsupported unwrap type: " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }

        @Override
        public String toString() {
            return "TestConnection{" +
                    "id=" + id +
                    '}';
        }

        private SQLFeatureNotSupportedException unsupported() {
            return new SQLFeatureNotSupportedException("Not needed for this test");
        }
    }
}
