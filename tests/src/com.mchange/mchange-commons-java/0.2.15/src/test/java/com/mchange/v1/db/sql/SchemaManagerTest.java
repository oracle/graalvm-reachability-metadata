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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SchemaManagerTest {
    @Test
    void mainCreatesSchemaUsingSchemaClassName() throws SQLException {
        TrackingDriver trackingDriver = new TrackingDriver();
        DriverManager.registerDriver(trackingDriver);
        try {
            TrackingDriver.reset();
            TrackingSchema.reset();

            SchemaManager.main(new String[] {
                "-create",
                "jdbc:schemamanager:create",
                TrackingSchema.class.getName()
            });

            TrackingConnection connection = TrackingDriver.getLastConnection();

            assertThat(TrackingSchema.constructorCalls).isEqualTo(1);
            assertThat(TrackingSchema.createCalls).isEqualTo(1);
            assertThat(TrackingSchema.dropCalls).isZero();
            assertThat(TrackingSchema.lastConnection).isSameAs(connection);
            assertThat(connection).isNotNull();
            assertThat(connection.isAutoCommitDisabled()).isTrue();
            assertThat(connection.isClosed()).isTrue();
            assertThat(TrackingDriver.getLastConnectProperties()).isEmpty();
        } finally {
            DriverManager.deregisterDriver(trackingDriver);
        }
    }

    @Test
    void mainDropsSchemaWhenCredentialsAreProvided() throws SQLException {
        TrackingDriver trackingDriver = new TrackingDriver();
        DriverManager.registerDriver(trackingDriver);
        try {
            TrackingDriver.reset();
            TrackingSchema.reset();

            SchemaManager.main(new String[] {
                "-drop",
                "jdbc:schemamanager:drop",
                "db-user",
                "db-password",
                TrackingSchema.class.getName()
            });

            TrackingConnection connection = TrackingDriver.getLastConnection();
            Properties properties = TrackingDriver.getLastConnectProperties();

            assertThat(TrackingSchema.constructorCalls).isEqualTo(1);
            assertThat(TrackingSchema.createCalls).isZero();
            assertThat(TrackingSchema.dropCalls).isEqualTo(1);
            assertThat(TrackingSchema.lastConnection).isSameAs(connection);
            assertThat(connection).isNotNull();
            assertThat(connection.isAutoCommitDisabled()).isTrue();
            assertThat(connection.isClosed()).isTrue();
            assertThat(properties.getProperty("user")).isEqualTo("db-user");
            assertThat(properties.getProperty("password")).isEqualTo("db-password");
        } finally {
            DriverManager.deregisterDriver(trackingDriver);
        }
    }

    public static final class TrackingSchema implements Schema {
        private static int constructorCalls;
        private static int createCalls;
        private static int dropCalls;
        private static Connection lastConnection;

        public TrackingSchema() {
            constructorCalls++;
        }

        public static void reset() {
            constructorCalls = 0;
            createCalls = 0;
            dropCalls = 0;
            lastConnection = null;
        }

        @Override
        public void createSchema(Connection connection) {
            createCalls++;
            lastConnection = connection;
        }

        @Override
        public void dropSchema(Connection connection) {
            dropCalls++;
            lastConnection = connection;
        }

        @Override
        public String getStatementText(String appName, String stmtName) {
            return appName + ":" + stmtName;
        }
    }

    private static final class TrackingDriver implements Driver {
        private static TrackingConnection lastConnection;
        private static Properties lastConnectProperties;

        static void reset() {
            lastConnection = null;
            lastConnectProperties = null;
        }

        static TrackingConnection getLastConnection() {
            return lastConnection;
        }

        static Properties getLastConnectProperties() {
            return lastConnectProperties;
        }

        @Override
        public Connection connect(String url, Properties info) {
            if (!acceptsURL(url)) {
                return null;
            }
            Properties copiedInfo = new Properties();
            if (info != null) {
                copiedInfo.putAll(info);
            }
            lastConnectProperties = copiedInfo;
            lastConnection = new TrackingConnection(url, copiedInfo);
            return lastConnection;
        }

        @Override
        public boolean acceptsURL(String url) {
            return url.startsWith("jdbc:schemamanager:");
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
