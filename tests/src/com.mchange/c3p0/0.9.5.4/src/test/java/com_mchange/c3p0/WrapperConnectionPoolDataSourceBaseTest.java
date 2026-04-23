/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.WrapperConnectionPoolDataSource;
import com.mchange.v2.c3p0.impl.C3P0ImplUtils;
import com.mchange.v2.c3p0.impl.DefaultConnectionTester;
import org.junit.jupiter.api.Test;

import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;
import javax.naming.spi.ObjectFactory;
import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

public class WrapperConnectionPoolDataSourceBaseTest {
    @Test
    void roundTripsConfiguredSerializableNestedDataSource() throws Exception {
        WrapperConnectionPoolDataSource dataSource = newConfiguredDataSource(
            "wrapper-base-direct",
            C3p0TestSupport.newDriverManagerDataSource("wrapper-base-direct")
        );

        WrapperConnectionPoolDataSource restored = C3p0TestSupport.roundTrip(dataSource);

        assertSharedProperties(restored, "wrapper-base-direct");
        assertThat(restored.getNestedDataSource()).isInstanceOf(DataSource.class);

        try (Connection connection = restored.getNestedDataSource().getConnection()) {
            assertThat(connection.isValid(1)).isTrue();
        }
    }

    @Test
    void roundTripsReferenceableNestedDataSourceThroughIndirectSerialization() throws Exception {
        WrapperConnectionPoolDataSource dataSource = newConfiguredDataSource(
            "wrapper-base-indirect",
            new NonSerializableReferenceableDataSource(C3p0TestSupport.jdbcUrl("wrapper-base-indirect"), C3p0TestSupport.USER, C3p0TestSupport.PASSWORD)
        );

        WrapperConnectionPoolDataSource restored = C3p0TestSupport.roundTrip(dataSource);

        assertSharedProperties(restored, "wrapper-base-indirect");
        assertThat(restored.getNestedDataSource()).isInstanceOf(NonSerializableReferenceableDataSource.class);

        try (Connection connection = restored.getNestedDataSource().getConnection()) {
            assertThat(connection.isValid(1)).isTrue();
        }
    }

    private static WrapperConnectionPoolDataSource newConfiguredDataSource(String name, DataSource nestedDataSource) throws Exception {
        WrapperConnectionPoolDataSource dataSource = new WrapperConnectionPoolDataSource();
        Map<String, Map<String, String>> overrides = new HashMap<>();
        Map<String, String> userOverrides = new HashMap<>();
        userOverrides.put("maxPoolSize", "6");
        overrides.put(C3p0TestSupport.USER, userOverrides);

        dataSource.setAcquireIncrement(2);
        dataSource.setAcquireRetryAttempts(3);
        dataSource.setAcquireRetryDelay(4);
        dataSource.setAutoCommitOnClose(false);
        dataSource.setAutomaticTestTable("C3P0_SERIALIZATION_TEST");
        dataSource.setBreakAfterAcquireFailure(true);
        dataSource.setCheckoutTimeout(5);
        dataSource.setConnectionCustomizerClassName(TrackingConnectionCustomizer.class.getName());
        dataSource.setConnectionTesterClassName(DefaultConnectionTester.class.getName());
        dataSource.setContextClassLoaderSource("library");
        dataSource.setDebugUnreturnedConnectionStackTraces(true);
        dataSource.setFactoryClassLocation("factory-location");
        dataSource.setForceIgnoreUnresolvedTransactions(true);
        dataSource.setForceSynchronousCheckins(true);
        dataSource.setIdentityToken(name + "-token");
        dataSource.setIdleConnectionTestPeriod(6);
        dataSource.setInitialPoolSize(1);
        dataSource.setMaxAdministrativeTaskTime(7);
        dataSource.setMaxConnectionAge(8);
        dataSource.setMaxIdleTime(9);
        dataSource.setMaxIdleTimeExcessConnections(10);
        dataSource.setMaxPoolSize(11);
        dataSource.setMaxStatements(12);
        dataSource.setMaxStatementsPerConnection(13);
        dataSource.setMinPoolSize(1);
        dataSource.setNestedDataSource(nestedDataSource);
        dataSource.setOverrideDefaultPassword("override-password");
        dataSource.setOverrideDefaultUser("override-user");
        dataSource.setPreferredTestQuery("SELECT 1");
        dataSource.setPrivilegeSpawnedThreads(true);
        dataSource.setPropertyCycle(14);
        dataSource.setStatementCacheNumDeferredCloseThreads(1);
        dataSource.setTestConnectionOnCheckin(true);
        dataSource.setTestConnectionOnCheckout(true);
        dataSource.setUnreturnedConnectionTimeout(15);
        dataSource.setUserOverridesAsString(C3P0ImplUtils.createUserOverridesAsString(overrides));
        dataSource.setUsesTraditionalReflectiveProxies(true);
        return dataSource;
    }

    private static void assertSharedProperties(WrapperConnectionPoolDataSource restored, String name) {
        assertThat(restored.getAutomaticTestTable()).isEqualTo("C3P0_SERIALIZATION_TEST");
        assertThat(restored.getConnectionCustomizerClassName()).isEqualTo(TrackingConnectionCustomizer.class.getName());
        assertThat(restored.getConnectionTesterClassName()).isEqualTo(DefaultConnectionTester.class.getName());
        assertThat(restored.getContextClassLoaderSource()).isEqualTo("library");
        assertThat(restored.getFactoryClassLocation()).isEqualTo("factory-location");
        assertThat(restored.getIdentityToken()).isEqualTo(name + "-token");
        assertThat(restored.getOverrideDefaultPassword()).isEqualTo("override-password");
        assertThat(restored.getOverrideDefaultUser()).isEqualTo("override-user");
        assertThat(restored.getPreferredTestQuery()).isEqualTo("SELECT 1");
        assertThat(restored.getUserOverridesAsString()).isNotBlank();
        assertThat(restored.isUsesTraditionalReflectiveProxies()).isTrue();
    }

    private static final class NonSerializableReferenceableDataSource implements DataSource, Referenceable {
        private final String jdbcUrl;
        private final String user;
        private final String password;
        private PrintWriter logWriter;
        private int loginTimeout;

        private NonSerializableReferenceableDataSource(String jdbcUrl, String user, String password) {
            this.jdbcUrl = jdbcUrl;
            this.user = user;
            this.password = password;
        }

        @Override
        public Reference getReference() {
            Reference reference = new Reference(
                NonSerializableReferenceableDataSource.class.getName(),
                NonSerializableReferenceableDataSourceFactory.class.getName(),
                null
            );
            reference.add(new StringRefAddr("jdbcUrl", jdbcUrl));
            reference.add(new StringRefAddr("user", user));
            reference.add(new StringRefAddr("password", password));
            return reference;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(jdbcUrl, user, password);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(jdbcUrl, username, password);
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
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException("Parent logger is not supported.");
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

    public static final class NonSerializableReferenceableDataSourceFactory implements ObjectFactory {
        @Override
        public Object getObjectInstance(Object obj, javax.naming.Name name, javax.naming.Context nameCtx, Hashtable<?, ?> environment) {
            Reference reference = (Reference) obj;
            return new NonSerializableReferenceableDataSource(
                (String) reference.get("jdbcUrl").getContent(),
                (String) reference.get("user").getContent(),
                (String) reference.get("password").getContent()
            );
        }
    }
}
