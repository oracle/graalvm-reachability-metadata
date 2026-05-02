/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.zaxxer.hikaricp.test;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariConfigMXBean;
import com.zaxxer.hikari.HikariCredentialsProvider;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import com.zaxxer.hikari.SQLExceptionOverride;
import com.zaxxer.hikari.metrics.IMetricsTracker;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import com.zaxxer.hikari.metrics.PoolStats;
import com.zaxxer.hikari.util.Credentials;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.DriverManager;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLTransientConnectionException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HikariCPIntegrationTest {
    private static final AtomicInteger POOL_COUNTER = new AtomicInteger();

    @Test
    void configurationValidationAndCopyStatePreservePublicSettings() {
        HikariConfig config = new HikariConfig();
        config.setPoolName(nextPoolName("config"));
        config.setJdbcUrl("jdbc:hikari-recording:config");
        config.setUsername("config-user");
        config.setPassword("config-password");
        config.setAutoCommit(false);
        config.setReadOnly(true);
        config.setCatalog("test_catalog");
        config.setSchema("test_schema");
        config.setTransactionIsolation("TRANSACTION_REPEATABLE_READ");
        config.setConnectionTimeout(250);
        config.setValidationTimeout(250);
        config.setIdleTimeout(10_000);
        config.setMaxLifetime(30_000);
        config.setLeakDetectionThreshold(2_000);
        config.setKeepaliveTime(0);
        config.setMinimumIdle(0);
        config.setMaximumPoolSize(3);
        config.setInitializationFailTimeout(1);
        config.setIsolateInternalQueries(true);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", 250);
        config.addHealthCheckProperty("connectivityCheckTimeoutMs", "100");

        config.validate();

        HikariConfig copy = new HikariConfig();
        config.copyStateTo(copy);

        assertEquals(config.getPoolName(), copy.getPoolName());
        assertEquals("jdbc:hikari-recording:config", copy.getJdbcUrl());
        assertEquals("config-user", copy.getUsername());
        assertEquals("config-password", copy.getPassword());
        assertFalse(copy.isAutoCommit());
        assertTrue(copy.isReadOnly());
        assertEquals("test_catalog", copy.getCatalog());
        assertEquals("test_schema", copy.getSchema());
        assertEquals("TRANSACTION_REPEATABLE_READ", copy.getTransactionIsolation());
        assertEquals(3, copy.getMaximumPoolSize());
        assertEquals(0, copy.getMinimumIdle());
        assertEquals("true", copy.getDataSourceProperties().getProperty("cachePrepStmts"));
        assertEquals("250", copy.getDataSourceProperties().getProperty("prepStmtCacheSize"));
        assertEquals("100", copy.getHealthCheckProperties().getProperty("connectivityCheckTimeoutMs"));
    }

    @Test
    void dataSourceBackedPoolAppliesDefaultsAndReusesPhysicalConnection() throws SQLException {
        RecordingDataSource recordingDataSource = new RecordingDataSource();
        HikariConfig config = baseDataSourceConfig("reuse", recordingDataSource);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setAutoCommit(false);
        config.setReadOnly(true);
        config.setCatalog("catalog_a");
        config.setSchema("schema_a");
        config.setTransactionIsolation("TRANSACTION_SERIALIZABLE");

        long firstId;
        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            try (Connection connection = dataSource.getConnection()) {
                assertFalse(connection.getAutoCommit());
                assertTrue(connection.isReadOnly());
                assertEquals("catalog_a", connection.getCatalog());
                assertEquals("schema_a", connection.getSchema());
                assertEquals(Connection.TRANSACTION_SERIALIZABLE, connection.getTransactionIsolation());
                RecordingConnection unwrapped = connection.unwrap(RecordingConnection.class);
                assertFalse(unwrapped.isPhysicallyClosed());
                firstId = unwrapped.getId();
            }

            try (Connection connection = dataSource.getConnection()) {
                RecordingConnection unwrapped = connection.unwrap(RecordingConnection.class);
                assertEquals(firstId, unwrapped.getId());
                assertFalse(unwrapped.isPhysicallyClosed());
            }
        }

        assertEquals(1, recordingDataSource.getConnections().size());
        assertTrue(recordingDataSource.getConnections().get(0).isPhysicallyClosed());
    }

    @Test
    void driverBackedPoolPassesJdbcUrlCredentialsAndDataSourceProperties() throws SQLException {
        RecordingDriver.reset();
        HikariConfig config = baseDriverConfig("driver");
        config.setUsername("driver-user");
        config.setPassword("driver-password");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "64");

        RecordingDriver driver = new RecordingDriver();
        DriverManager.registerDriver(driver);
        try {
            try (HikariDataSource dataSource = new HikariDataSource(config);
                 Connection connection = dataSource.getConnection()) {
                assertNotNull(connection.unwrap(RecordingConnection.class));
            }
        } finally {
            DriverManager.deregisterDriver(driver);
        }

        assertEquals("jdbc:hikari-recording:driver", RecordingDriver.LAST_URL.get());
        Properties properties = RecordingDriver.LAST_PROPERTIES.get();
        assertNotNull(properties);
        assertEquals("driver-user", properties.getProperty("user"));
        assertEquals("driver-password", properties.getProperty("password"));
        assertEquals("true", properties.getProperty("cachePrepStmts"));
        assertEquals("64", properties.getProperty("prepStmtCacheSize"));
        assertTrue(RecordingDriver.CONNECT_COUNT.get() >= 1);
    }

    @Test
    void poolMxBeanTracksActivityTimeoutAndSoftEviction() throws SQLException {
        RecordingDataSource recordingDataSource = new RecordingDataSource();
        HikariConfig config = baseDataSourceConfig("mxbean", recordingDataSource);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(250);

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            HikariPoolMXBean poolMxBean = dataSource.getHikariPoolMXBean();
            assertNotNull(poolMxBean);

            long firstId;
            try (Connection connection = dataSource.getConnection()) {
                firstId = connection.unwrap(RecordingConnection.class).getId();
                assertEquals(1, poolMxBean.getActiveConnections());
                assertEquals(0, poolMxBean.getIdleConnections());
                assertEquals(1, poolMxBean.getTotalConnections());
                assertEquals(0, poolMxBean.getThreadsAwaitingConnection());

                assertThrows(SQLTransientConnectionException.class, dataSource::getConnection);
            }

            assertEquals(0, poolMxBean.getActiveConnections());
            assertEquals(1, poolMxBean.getIdleConnections());

            poolMxBean.softEvictConnections();

            try (Connection connection = dataSource.getConnection()) {
                long secondId = connection.unwrap(RecordingConnection.class).getId();
                assertTrue(secondId > firstId);
                assertEquals(1, poolMxBean.getActiveConnections());
            }
        }
    }

    @Test
    void poolMxBeanSuspendsAndResumesConnectionAcquisition() throws Exception {
        RecordingDataSource recordingDataSource = new RecordingDataSource();
        HikariConfig config = baseDataSourceConfig("suspension", recordingDataSource);
        config.setAllowPoolSuspension(true);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            HikariPoolMXBean poolMxBean = dataSource.getHikariPoolMXBean();
            assertNotNull(poolMxBean);

            poolMxBean.suspendPool();
            Future<Connection> pendingConnection = executor.submit((Callable<Connection>) dataSource::getConnection);
            try {
                assertThrows(TimeoutException.class, () -> pendingConnection.get(150, TimeUnit.MILLISECONDS));
                assertFalse(pendingConnection.isDone());
            } finally {
                poolMxBean.resumePool();
            }

            try (Connection connection = pendingConnection.get(2, TimeUnit.SECONDS)) {
                assertNotNull(connection.unwrap(RecordingConnection.class));
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void configMxBeanMutatesRuntimePoolLimitsAndCredentials() throws SQLException {
        RecordingDataSource recordingDataSource = new RecordingDataSource();
        HikariConfig config = baseDataSourceConfig("config-mxbean", recordingDataSource);
        config.setUsername("before-user");
        config.setPassword("before-password");

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            HikariConfigMXBean configMxBean = dataSource.getHikariConfigMXBean();
            assertNotNull(configMxBean);
            assertEquals(config.getPoolName(), configMxBean.getPoolName());

            configMxBean.setMaximumPoolSize(2);
            configMxBean.setMinimumIdle(0);
            configMxBean.setConnectionTimeout(500);
            configMxBean.setValidationTimeout(500);
            configMxBean.setIdleTimeout(10_000);
            configMxBean.setMaxLifetime(30_000);
            configMxBean.setLeakDetectionThreshold(2_000);
            configMxBean.setCatalog("runtime_catalog");
            configMxBean.setUsername("after-user");
            configMxBean.setPassword("after-password");

            assertEquals(2, configMxBean.getMaximumPoolSize());
            assertEquals(0, configMxBean.getMinimumIdle());
            assertEquals(500, configMxBean.getConnectionTimeout());
            assertEquals(500, configMxBean.getValidationTimeout());
            assertEquals("runtime_catalog", configMxBean.getCatalog());

            try (Connection first = dataSource.getConnection();
                 Connection second = dataSource.getConnection()) {
                assertNotNull(first.unwrap(RecordingConnection.class));
                assertNotNull(second.unwrap(RecordingConnection.class));
                assertEquals(2, dataSource.getHikariPoolMXBean().getActiveConnections());
            }
        }
    }

    @Test
    void credentialsProviderSuppliesCredentialsForEachPhysicalConnection() throws SQLException {
        RecordingDataSource recordingDataSource = new RecordingDataSource();
        SequencedCredentialsProvider credentialsProvider = new SequencedCredentialsProvider();
        HikariConfig config = baseDataSourceConfig("credentials-provider", recordingDataSource);
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        config.setInitializationFailTimeout(-1);
        config.setCredentialsProvider(credentialsProvider);

        try (HikariDataSource dataSource = new HikariDataSource(config);
             Connection first = dataSource.getConnection();
             Connection second = dataSource.getConnection()) {
            RecordingConnection firstPhysicalConnection = first.unwrap(RecordingConnection.class);
            RecordingConnection secondPhysicalConnection = second.unwrap(RecordingConnection.class);

            assertProviderCredentials(firstPhysicalConnection);
            assertProviderCredentials(secondPhysicalConnection);
            assertFalse(firstPhysicalConnection.getUsername().equals(secondPhysicalConnection.getUsername()));
        }

        assertTrue(credentialsProvider.getInvocationCount() >= 2);
        assertTrue(recordingDataSource.getConnections().size() >= 2);
    }

    @Test
    void metricsTrackerRecordsAcquireUsageCreationAndTimeout() throws SQLException {
        RecordingDataSource recordingDataSource = new RecordingDataSource();
        RecordingMetricsFactory metricsFactory = new RecordingMetricsFactory();
        HikariConfig config = baseDataSourceConfig("metrics", recordingDataSource);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(250);
        config.setMetricsTrackerFactory(metricsFactory);

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            try (Connection connection = dataSource.getConnection()) {
                assertNotNull(connection.unwrap(RecordingConnection.class));
                assertThrows(SQLTransientConnectionException.class, dataSource::getConnection);
            }

            RecordingMetricsTracker tracker = metricsFactory.getTracker();
            assertNotNull(tracker);
            assertEquals(config.getPoolName(), metricsFactory.getPoolName());
            assertTrue(tracker.getConnectionCreatedCount() >= 1);
            assertTrue(tracker.getConnectionAcquiredCount() >= 1);
            assertTrue(tracker.getConnectionUsageCount() >= 1);
            assertTrue(tracker.getConnectionTimeoutCount() >= 1);
            assertTrue(metricsFactory.getPoolStats().getTotalConnections() >= 1);
        }

        assertTrue(metricsFactory.getTracker().isClosed());
    }

    @Test
    void exceptionOverrideCanKeepApplicationSqlExceptionFromEvictingConnection() throws SQLException {
        RecordingDataSource recordingDataSource = new RecordingDataSource();
        NonEvictingExceptionOverride exceptionOverride = new NonEvictingExceptionOverride();
        HikariConfig config = baseDataSourceConfig("exception-override", recordingDataSource);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setExceptionOverride(exceptionOverride);

        long firstId;
        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            try (Connection connection = dataSource.getConnection()) {
                firstId = connection.unwrap(RecordingConnection.class).getId();
                SQLException exception = assertThrows(SQLException.class,
                        () -> connection.prepareStatement("select broken"));
                assertEquals("42000", exception.getSQLState());
            }

            try (Connection connection = dataSource.getConnection()) {
                long secondId = connection.unwrap(RecordingConnection.class).getId();
                assertEquals(firstId, secondId);
            }
        }

        assertTrue(exceptionOverride.getInvocationCount() >= 1);
    }

    private static HikariConfig baseDataSourceConfig(String name, RecordingDataSource recordingDataSource) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(nextPoolName(name));
        config.setDataSource(recordingDataSource);
        config.setConnectionTimeout(250);
        config.setValidationTimeout(250);
        config.setInitializationFailTimeout(1);
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        return config;
    }

    private static HikariConfig baseDriverConfig(String name) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(nextPoolName(name));
        config.setJdbcUrl("jdbc:hikari-recording:" + name);
        config.setConnectionTimeout(250);
        config.setValidationTimeout(250);
        config.setInitializationFailTimeout(1);
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        return config;
    }

    private static String nextPoolName(String prefix) {
        return "hikari-" + prefix + '-' + POOL_COUNTER.incrementAndGet();
    }

    private static void assertProviderCredentials(RecordingConnection connection) {
        String username = connection.getUsername();
        assertNotNull(username);
        assertTrue(username.startsWith("provider-user-"));
        assertEquals(username.replace("provider-user-", "provider-password-"), connection.getPassword());
    }

    public static final class RecordingDataSource implements DataSource {
        private final AtomicLong nextId = new AtomicLong();
        private final List<RecordingConnection> connections = new ArrayList<>();
        private volatile PrintWriter logWriter;
        private volatile int loginTimeout;
        private volatile String lastUsername;
        private volatile String lastPassword;

        @Override
        public Connection getConnection() {
            return createConnection(null, null);
        }

        @Override
        public Connection getConnection(String username, String password) {
            this.lastUsername = username;
            this.lastPassword = password;
            return createConnection(username, password);
        }

        private synchronized Connection createConnection(String username, String password) {
            RecordingConnection connection = new RecordingConnection(this.nextId.incrementAndGet(), username, password);
            this.connections.add(connection);
            return connection;
        }

        @Override
        public PrintWriter getLogWriter() {
            return this.logWriter;
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
            return this.loginTimeout;
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

        public List<RecordingConnection> getConnections() {
            return this.connections;
        }

        public String getLastUsername() {
            return this.lastUsername;
        }

        public String getLastPassword() {
            return this.lastPassword;
        }
    }

    public static final class RecordingDriver implements Driver {
        private static final AtomicLong NEXT_ID = new AtomicLong();
        private static final AtomicInteger CONNECT_COUNT = new AtomicInteger();
        private static final AtomicReference<String> LAST_URL = new AtomicReference<>();
        private static final AtomicReference<Properties> LAST_PROPERTIES = new AtomicReference<>();

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            if (!acceptsURL(url)) {
                return null;
            }
            Properties copy = new Properties();
            copy.putAll(info);
            LAST_URL.set(url);
            LAST_PROPERTIES.set(copy);
            CONNECT_COUNT.incrementAndGet();
            return new RecordingConnection(NEXT_ID.incrementAndGet(),
                    info.getProperty("user"), info.getProperty("password"));
        }

        @Override
        public boolean acceptsURL(String url) {
            return url != null && url.startsWith("jdbc:hikari-recording:");
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

        static void reset() {
            NEXT_ID.set(0);
            CONNECT_COUNT.set(0);
            LAST_URL.set(null);
            LAST_PROPERTIES.set(null);
        }
    }

    public static final class RecordingConnection implements Connection {
        private final long id;
        private final String username;
        private final String password;
        private final Properties clientInfo = new Properties();
        private volatile boolean physicallyClosed;
        private volatile boolean autoCommit = true;
        private volatile boolean readOnly;
        private volatile String catalog;
        private volatile String schema;
        private volatile int transactionIsolation = Connection.TRANSACTION_READ_COMMITTED;
        private volatile int networkTimeout;

        RecordingConnection(long id, String username, String password) {
            this.id = id;
            this.username = username;
            this.password = password;
        }

        long getId() {
            return this.id;
        }

        boolean isPhysicallyClosed() {
            return this.physicallyClosed;
        }

        String getUsername() {
            return this.username;
        }

        String getPassword() {
            return this.password;
        }

        @Override
        public Statement createStatement() throws SQLException {
            assertOpen();
            throw new SQLFeatureNotSupportedException("Statements are not supported by the recording connection");
        }

        @Override
        public PreparedStatement prepareStatement(String sql) throws SQLException {
            assertOpen();
            throw new SQLException("Application SQL failure", "42000");
        }

        @Override
        public CallableStatement prepareCall(String sql) throws SQLException {
            assertOpen();
            throw new SQLFeatureNotSupportedException("Callable statements are not supported");
        }

        @Override
        public String nativeSQL(String sql) throws SQLException {
            assertOpen();
            return sql;
        }

        @Override
        public void setAutoCommit(boolean autoCommit) throws SQLException {
            assertOpen();
            this.autoCommit = autoCommit;
        }

        @Override
        public boolean getAutoCommit() throws SQLException {
            assertOpen();
            return this.autoCommit;
        }

        @Override
        public void commit() throws SQLException {
            assertOpen();
        }

        @Override
        public void rollback() throws SQLException {
            assertOpen();
        }

        @Override
        public void close() {
            this.physicallyClosed = true;
        }

        @Override
        public boolean isClosed() {
            return this.physicallyClosed;
        }

        @Override
        public DatabaseMetaData getMetaData() throws SQLException {
            assertOpen();
            throw new SQLFeatureNotSupportedException("Metadata is not supported");
        }

        @Override
        public void setReadOnly(boolean readOnly) throws SQLException {
            assertOpen();
            this.readOnly = readOnly;
        }

        @Override
        public boolean isReadOnly() throws SQLException {
            assertOpen();
            return this.readOnly;
        }

        @Override
        public void setCatalog(String catalog) throws SQLException {
            assertOpen();
            this.catalog = catalog;
        }

        @Override
        public String getCatalog() throws SQLException {
            assertOpen();
            return this.catalog;
        }

        @Override
        public void setTransactionIsolation(int level) throws SQLException {
            assertOpen();
            this.transactionIsolation = level;
        }

        @Override
        public int getTransactionIsolation() throws SQLException {
            assertOpen();
            return this.transactionIsolation;
        }

        @Override
        public SQLWarning getWarnings() throws SQLException {
            assertOpen();
            return null;
        }

        @Override
        public void clearWarnings() throws SQLException {
            assertOpen();
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
            return createStatement();
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType,
                int resultSetConcurrency) throws SQLException {
            return prepareStatement(sql);
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType,
                int resultSetConcurrency) throws SQLException {
            return prepareCall(sql);
        }

        @Override
        public Map<String, Class<?>> getTypeMap() throws SQLException {
            assertOpen();
            throw new SQLFeatureNotSupportedException("Type maps are not supported");
        }

        @Override
        public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
            assertOpen();
            throw new SQLFeatureNotSupportedException("Type maps are not supported");
        }

        @Override
        public void setHoldability(int holdability) throws SQLException {
            assertOpen();
        }

        @Override
        public int getHoldability() throws SQLException {
            assertOpen();
            return 0;
        }

        @Override
        public Savepoint setSavepoint() throws SQLException {
            assertOpen();
            throw new SQLFeatureNotSupportedException("Savepoints are not supported");
        }

        @Override
        public Savepoint setSavepoint(String name) throws SQLException {
            assertOpen();
            throw new SQLFeatureNotSupportedException("Savepoints are not supported");
        }

        @Override
        public void rollback(Savepoint savepoint) throws SQLException {
            rollback();
        }

        @Override
        public void releaseSavepoint(Savepoint savepoint) throws SQLException {
            assertOpen();
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency,
                int resultSetHoldability) throws SQLException {
            return createStatement();
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType,
                int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            return prepareStatement(sql);
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType,
                int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            return prepareCall(sql);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
            return prepareStatement(sql);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
            return prepareStatement(sql);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
            return prepareStatement(sql);
        }

        @Override
        public Clob createClob() throws SQLException {
            assertOpen();
            throw new SQLFeatureNotSupportedException("CLOBs are not supported");
        }

        @Override
        public Blob createBlob() throws SQLException {
            assertOpen();
            throw new SQLFeatureNotSupportedException("BLOBs are not supported");
        }

        @Override
        public NClob createNClob() throws SQLException {
            assertOpen();
            throw new SQLFeatureNotSupportedException("NCLOBs are not supported");
        }

        @Override
        public SQLXML createSQLXML() throws SQLException {
            assertOpen();
            throw new SQLFeatureNotSupportedException("SQLXML is not supported");
        }

        @Override
        public boolean isValid(int timeout) {
            return !this.physicallyClosed;
        }

        @Override
        public void setClientInfo(String name, String value) throws SQLClientInfoException {
            this.clientInfo.setProperty(name, value);
        }

        @Override
        public void setClientInfo(Properties properties) throws SQLClientInfoException {
            this.clientInfo.clear();
            this.clientInfo.putAll(properties);
        }

        @Override
        public String getClientInfo(String name) throws SQLException {
            assertOpen();
            return this.clientInfo.getProperty(name);
        }

        @Override
        public Properties getClientInfo() throws SQLException {
            assertOpen();
            Properties copy = new Properties();
            copy.putAll(this.clientInfo);
            return copy;
        }

        @Override
        public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
            assertOpen();
            throw new SQLFeatureNotSupportedException("Arrays are not supported");
        }

        @Override
        public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
            assertOpen();
            throw new SQLFeatureNotSupportedException("Structs are not supported");
        }

        @Override
        public void setSchema(String schema) throws SQLException {
            assertOpen();
            this.schema = schema;
        }

        @Override
        public String getSchema() throws SQLException {
            assertOpen();
            return this.schema;
        }

        @Override
        public void abort(Executor executor) {
            this.physicallyClosed = true;
        }

        @Override
        public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
            assertOpen();
            this.networkTimeout = milliseconds;
        }

        @Override
        public int getNetworkTimeout() throws SQLException {
            assertOpen();
            return this.networkTimeout;
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

        private void assertOpen() throws SQLException {
            if (this.physicallyClosed) {
                throw new SQLException("Connection is closed", "08003");
            }
        }
    }

    public static final class SequencedCredentialsProvider implements HikariCredentialsProvider {
        private final AtomicInteger invocationCount = new AtomicInteger();

        @Override
        public Credentials getCredentials() {
            int credentialsNumber = this.invocationCount.incrementAndGet();
            return Credentials.of("provider-user-" + credentialsNumber, "provider-password-" + credentialsNumber);
        }

        int getInvocationCount() {
            return this.invocationCount.get();
        }
    }

    public static final class RecordingMetricsFactory implements MetricsTrackerFactory {
        private final AtomicReference<String> poolName = new AtomicReference<>();
        private final AtomicReference<PoolStats> poolStats = new AtomicReference<>();
        private final AtomicReference<RecordingMetricsTracker> tracker = new AtomicReference<>();

        @Override
        public IMetricsTracker create(String poolName, PoolStats poolStats) {
            RecordingMetricsTracker created = new RecordingMetricsTracker();
            this.poolName.set(poolName);
            this.poolStats.set(poolStats);
            this.tracker.set(created);
            return created;
        }

        String getPoolName() {
            return this.poolName.get();
        }

        PoolStats getPoolStats() {
            return this.poolStats.get();
        }

        RecordingMetricsTracker getTracker() {
            return this.tracker.get();
        }
    }

    public static final class RecordingMetricsTracker implements IMetricsTracker {
        private final AtomicInteger connectionCreatedCount = new AtomicInteger();
        private final AtomicInteger connectionAcquiredCount = new AtomicInteger();
        private final AtomicInteger connectionUsageCount = new AtomicInteger();
        private final AtomicInteger connectionTimeoutCount = new AtomicInteger();
        private volatile boolean closed;

        @Override
        public void recordConnectionCreatedMillis(long connectionCreatedMillis) {
            this.connectionCreatedCount.incrementAndGet();
        }

        @Override
        public void recordConnectionAcquiredNanos(long elapsedAcquiredNanos) {
            this.connectionAcquiredCount.incrementAndGet();
        }

        @Override
        public void recordConnectionUsageMillis(long elapsedBorrowedMillis) {
            this.connectionUsageCount.incrementAndGet();
        }

        @Override
        public void recordConnectionTimeout() {
            this.connectionTimeoutCount.incrementAndGet();
        }

        @Override
        public void close() {
            this.closed = true;
        }

        int getConnectionCreatedCount() {
            return this.connectionCreatedCount.get();
        }

        int getConnectionAcquiredCount() {
            return this.connectionAcquiredCount.get();
        }

        int getConnectionUsageCount() {
            return this.connectionUsageCount.get();
        }

        int getConnectionTimeoutCount() {
            return this.connectionTimeoutCount.get();
        }

        boolean isClosed() {
            return this.closed;
        }
    }

    public static final class NonEvictingExceptionOverride implements SQLExceptionOverride {
        private final AtomicInteger invocationCount = new AtomicInteger();

        @java.lang.Override
        public Override adjudicate(SQLException sqlException) {
            this.invocationCount.incrementAndGet();
            if ("42000".equals(sqlException.getSQLState())) {
                return Override.DO_NOT_EVICT;
            }
            return Override.CONTINUE_EVICT;
        }

        int getInvocationCount() {
            return this.invocationCount.get();
        }
    }
}
