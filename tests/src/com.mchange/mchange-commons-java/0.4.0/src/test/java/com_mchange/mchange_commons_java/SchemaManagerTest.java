/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v1.db.sql.SchemaManager;
import com.mchange.v1.db.sql.XmlSchema;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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

import static org.assertj.core.api.Assertions.assertThat;

public class SchemaManagerTest {
    private static final String JDBC_URL = "jdbc:schema-manager:test";

    @Test
    void mainCreatesSchemaUsingReflectedSchemaClass() throws SQLException {
        TrackingDriver driver = new TrackingDriver();
        DriverManager.registerDriver(driver);
        try {
            String output = invokeMainAndCaptureStdout(new String[]{"-create", JDBC_URL, XmlSchema.class.getName()});

            assertThat(output).contains("Schema created.");
            assertThat(driver.connection.autoCommit).isFalse();
            assertThat(driver.connection.closed).isTrue();
            assertThat(driver.lastUrl).isEqualTo(JDBC_URL);
            assertThat(driver.lastProperties).doesNotContainKeys("user", "password");
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    @Test
    void mainDropsSchemaUsingCredentialsAndReflectedSchemaClass() throws SQLException {
        TrackingDriver driver = new TrackingDriver();
        DriverManager.registerDriver(driver);
        try {
            String output = invokeMainAndCaptureStdout(
                    new String[]{"-drop", JDBC_URL, "scott", "tiger", XmlSchema.class.getName()}
            );

            assertThat(output).contains("Schema dropped.");
            assertThat(driver.connection.autoCommit).isFalse();
            assertThat(driver.connection.closed).isTrue();
            assertThat(driver.lastUrl).isEqualTo(JDBC_URL);
            assertThat(driver.lastProperties)
                    .containsEntry("user", "scott")
                    .containsEntry("password", "tiger");
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    private static String invokeMainAndCaptureStdout(String[] arguments) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            SchemaManager.main(arguments);
        } finally {
            System.setOut(originalOut);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static final class TrackingDriver implements Driver {
        private final TrackingConnection connection = new TrackingConnection();
        private String lastUrl;
        private Properties lastProperties;

        @Override
        public Connection connect(String url, Properties info) {
            if (!acceptsUrl(url)) {
                return null;
            }
            this.lastUrl = url;
            this.lastProperties = copyOf(info);
            return connection;
        }

        @Override
        public boolean acceptsURL(String url) {
            return acceptsUrl(url);
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

        private static boolean acceptsUrl(String url) {
            return JDBC_URL.equals(url);
        }

        private static Properties copyOf(Properties original) {
            Properties copy = new Properties();
            if (original != null) {
                copy.putAll(original);
            }
            return copy;
        }
    }

    private static final class TrackingConnection implements Connection {
        private boolean autoCommit = true;
        private boolean closed;
        private final Properties clientInfo = new Properties();
        private String schema;
        private String catalog;
        private int transactionIsolation = Connection.TRANSACTION_NONE;
        private int holdability;
        private int networkTimeout;
        private boolean readOnly;
        private Map<String, Class<?>> typeMap;

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
            return typeMap;
        }

        @Override
        public void setTypeMap(Map<String, Class<?>> map) {
            this.typeMap = map;
        }

        @Override
        public void setHoldability(int holdability) {
            this.holdability = holdability;
        }

        @Override
        public int getHoldability() {
            return holdability;
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
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            throw unsupported();
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
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
            clientInfo.setProperty(name, value);
        }

        @Override
        public void setClientInfo(Properties properties) {
            clientInfo.clear();
            clientInfo.putAll(properties);
        }

        @Override
        public String getClientInfo(String name) {
            return clientInfo.getProperty(name);
        }

        @Override
        public Properties getClientInfo() {
            Properties copy = new Properties();
            copy.putAll(clientInfo);
            return copy;
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
            throw unsupported();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }

        private static SQLException unsupported() {
            return new SQLFeatureNotSupportedException("Not needed for SchemaManager coverage tests.");
        }
    }
}
