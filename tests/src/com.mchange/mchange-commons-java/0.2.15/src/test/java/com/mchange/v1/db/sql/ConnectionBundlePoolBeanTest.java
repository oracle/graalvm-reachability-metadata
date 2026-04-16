/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.mchange.v1.db.sql;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
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

import com.mchange.v1.util.BrokenObjectException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectionBundlePoolBeanTest {
    private static final String DRIVER_CLASS_NAME =
        "com.mchange.v1.db.sql.ConnectionBundlePoolBeanTest$AutoRegisteringDriver";

    @Test
    void initLoadsDriverClassAndCreatesBundlesFromTheRegisteredDriver()
        throws SQLException, ClassNotFoundException, BrokenObjectException, InterruptedException {
        ConnectionBundlePoolBean poolBean = new ConnectionBundlePoolBean();
        ConnectionBundle bundle = null;
        boolean initialized = false;
        DriverLoadState.reset();
        try {
            poolBean.init(DRIVER_CLASS_NAME, "jdbc:connectionbundlepool:test", "db-user", "db-password", 1, 1, 1);
            initialized = true;

            bundle = poolBean.checkoutBundle();
            TrackingConnection connection = DriverLoadState.lastConnection;
            Properties properties = DriverLoadState.lastConnectProperties;

            assertThat(DriverLoadState.registeredDriver).isNotNull();
            assertThat(DriverLoadState.connectCalls).isEqualTo(1);
            assertThat(connection).isNotNull();
            assertThat(bundle.getConnection()).isSameAs(connection);
            assertThat(connection.isAutoCommitDisabled()).isTrue();
            assertThat(properties.getProperty("user")).isEqualTo("db-user");
            assertThat(properties.getProperty("password")).isEqualTo("db-password");
        } finally {
            if (bundle != null) {
                poolBean.checkinBundle(bundle);
            }
            if (initialized) {
                poolBean.close();
                assertThat(DriverLoadState.lastConnection).isNotNull();
                assertThat(DriverLoadState.lastConnection.isClosed()).isTrue();
            }
            if (DriverLoadState.registeredDriver != null) {
                DriverManager.deregisterDriver(DriverLoadState.registeredDriver);
            }
        }
    }

    private static final class DriverLoadState {
        private static Driver registeredDriver;
        private static TrackingConnection lastConnection;
        private static Properties lastConnectProperties;
        private static int connectCalls;

        private static void reset() {
            registeredDriver = null;
            lastConnection = null;
            lastConnectProperties = null;
            connectCalls = 0;
        }
    }

    public static final class AutoRegisteringDriver implements Driver {
        static {
            register();
        }

        private static void register() {
            AutoRegisteringDriver driver = new AutoRegisteringDriver();
            try {
                DriverManager.registerDriver(driver);
            } catch (SQLException e) {
                throw new ExceptionInInitializerError(e);
            }
            DriverLoadState.registeredDriver = driver;
        }

        @Override
        public Connection connect(String url, Properties info) {
            if (!acceptsURL(url)) {
                return null;
            }
            DriverLoadState.connectCalls++;
            Properties copiedInfo = new Properties();
            if (info != null) {
                copiedInfo.putAll(info);
            }
            DriverLoadState.lastConnectProperties = copiedInfo;
            DriverLoadState.lastConnection = new TrackingConnection(url, copiedInfo);
            return DriverLoadState.lastConnection;
        }

        @Override
        public boolean acceptsURL(String url) {
            return url.startsWith("jdbc:connectionbundlepool:");
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
    }

    private static final class TrackingConnection implements Connection {
        private final String url;
        private final Properties properties;
        private boolean autoCommit = true;
        private boolean closed;

        private TrackingConnection(String url, Properties properties) {
            this.url = url;
            this.properties = properties;
        }

        boolean isAutoCommitDisabled() {
            return !autoCommit;
        }

        @Override
        public Statement createStatement() throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public PreparedStatement prepareStatement(String sql) throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public CallableStatement prepareCall(String sql) throws SQLException {
            throw new SQLFeatureNotSupportedException();
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
        public DatabaseMetaData getMetaData() throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public void setReadOnly(boolean readOnly) {
        }

        @Override
        public boolean isReadOnly() {
            return false;
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
        }

        @Override
        public int getTransactionIsolation() {
            return Connection.TRANSACTION_NONE;
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
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            throw new SQLFeatureNotSupportedException();
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
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public Savepoint setSavepoint(String name) throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public void rollback(Savepoint savepoint) {
        }

        @Override
        public void releaseSavepoint(Savepoint savepoint) {
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
            throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
            throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public Clob createClob() throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public Blob createBlob() throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public NClob createNClob() throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public SQLXML createSQLXML() throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public boolean isValid(int timeout) {
            return !closed;
        }

        @Override
        public void setClientInfo(String name, String value) throws SQLClientInfoException {
        }

        @Override
        public void setClientInfo(Properties properties) throws SQLClientInfoException {
        }

        @Override
        public String getClientInfo(String name) {
            return properties.getProperty(name);
        }

        @Override
        public Properties getClientInfo() {
            return properties;
        }

        @Override
        public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
            throw new SQLFeatureNotSupportedException();
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
        }

        @Override
        public int getNetworkTimeout() {
            return 0;
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }

        @Override
        public String toString() {
            return "TrackingConnection{" +
                "url='" + url + '\'' +
                ", properties=" + properties +
                '}';
        }
    }
}
