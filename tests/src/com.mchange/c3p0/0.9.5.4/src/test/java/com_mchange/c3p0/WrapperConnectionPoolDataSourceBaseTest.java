/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import static org.assertj.core.api.Assertions.assertThat;

import com.mchange.v2.c3p0.DriverManagerDataSource;
import com.mchange.v2.c3p0.WrapperConnectionPoolDataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

public class WrapperConnectionPoolDataSourceBaseTest {
    @Test
    void serializesAndDeserializesConfiguredWrapperDataSource() throws Exception {
        WrapperConnectionPoolDataSource source = configuredWrapper(newSerializableNestedDataSource());

        WrapperConnectionPoolDataSource restored = roundTrip(source);

        assertThat(restored.getAcquireIncrement()).isEqualTo(7);
        assertThat(restored.getAcquireRetryAttempts()).isEqualTo(8);
        assertThat(restored.getAcquireRetryDelay()).isEqualTo(9);
        assertThat(restored.isAutoCommitOnClose()).isTrue();
        assertThat(restored.getAutomaticTestTable()).isEqualTo("C3P0_TEST_TABLE");
        assertThat(restored.isBreakAfterAcquireFailure()).isTrue();
        assertThat(restored.getCheckoutTimeout()).isEqualTo(10);
        assertThat(restored.getConnectionCustomizerClassName()).isEqualTo("example.ConnectionCustomizer");
        assertThat(restored.getConnectionTesterClassName()).isEqualTo(source.getConnectionTesterClassName());
        assertThat(restored.getContextClassLoaderSource()).isEqualTo("library");
        assertThat(restored.isDebugUnreturnedConnectionStackTraces()).isTrue();
        assertThat(restored.getFactoryClassLocation()).isEqualTo("factory/location");
        assertThat(restored.isForceIgnoreUnresolvedTransactions()).isTrue();
        assertThat(restored.isForceSynchronousCheckins()).isTrue();
        assertThat(restored.getIdentityToken()).isEqualTo("wrapper-token");
        assertThat(restored.getIdleConnectionTestPeriod()).isEqualTo(11);
        assertThat(restored.getInitialPoolSize()).isEqualTo(12);
        assertThat(restored.getMaxAdministrativeTaskTime()).isEqualTo(13);
        assertThat(restored.getMaxConnectionAge()).isEqualTo(14);
        assertThat(restored.getMaxIdleTime()).isEqualTo(15);
        assertThat(restored.getMaxIdleTimeExcessConnections()).isEqualTo(16);
        assertThat(restored.getMaxPoolSize()).isEqualTo(17);
        assertThat(restored.getMaxStatements()).isEqualTo(18);
        assertThat(restored.getMaxStatementsPerConnection()).isEqualTo(19);
        assertThat(restored.getMinPoolSize()).isEqualTo(6);
        assertThat(restored.getOverrideDefaultPassword()).isEqualTo("override-password");
        assertThat(restored.getOverrideDefaultUser()).isEqualTo("override-user");
        assertThat(restored.getPreferredTestQuery()).isEqualTo("SELECT 1");
        assertThat(restored.isPrivilegeSpawnedThreads()).isTrue();
        assertThat(restored.getPropertyCycle()).isEqualTo(20);
        assertThat(restored.getStatementCacheNumDeferredCloseThreads()).isEqualTo(21);
        assertThat(restored.isTestConnectionOnCheckin()).isTrue();
        assertThat(restored.isTestConnectionOnCheckout()).isTrue();
        assertThat(restored.getUnreturnedConnectionTimeout()).isEqualTo(22);
        assertThat(restored.getUserOverridesAsString()).isEqualTo(source.getUserOverridesAsString());
        assertThat(restored.isUsesTraditionalReflectiveProxies()).isFalse();
        assertThat(restored.getNestedDataSource()).isInstanceOf(DriverManagerDataSource.class);
        assertThat(((DriverManagerDataSource) restored.getNestedDataSource()).getJdbcUrl()).isEqualTo("jdbc:test:direct");
    }

    @Test
    void serializesReferenceableNestedDataSourceIndirectly() throws Exception {
        WrapperConnectionPoolDataSource source = configuredWrapper(new NonSerializableReferenceableDataSource());

        byte[] serialized = serialize(source);

        assertThat(serialized).isNotEmpty();
    }

    private static WrapperConnectionPoolDataSource configuredWrapper(DataSource nestedDataSource) {
        WrapperConnectionPoolDataSource source = new WrapperConnectionPoolDataSource(false);
        source.setAcquireIncrement(7);
        source.setAcquireRetryAttempts(8);
        source.setAcquireRetryDelay(9);
        source.setAutoCommitOnClose(true);
        source.setAutomaticTestTable("C3P0_TEST_TABLE");
        source.setBreakAfterAcquireFailure(true);
        source.setCheckoutTimeout(10);
        source.setConnectionCustomizerClassName("example.ConnectionCustomizer");
        source.setContextClassLoaderSource("library");
        source.setDebugUnreturnedConnectionStackTraces(true);
        source.setFactoryClassLocation("factory/location");
        source.setForceIgnoreUnresolvedTransactions(true);
        source.setForceSynchronousCheckins(true);
        source.setIdentityToken("wrapper-token");
        source.setIdleConnectionTestPeriod(11);
        source.setInitialPoolSize(12);
        source.setMaxAdministrativeTaskTime(13);
        source.setMaxConnectionAge(14);
        source.setMaxIdleTime(15);
        source.setMaxIdleTimeExcessConnections(16);
        source.setMaxPoolSize(17);
        source.setMaxStatements(18);
        source.setMaxStatementsPerConnection(19);
        source.setMinPoolSize(6);
        source.setNestedDataSource(nestedDataSource);
        source.setOverrideDefaultPassword("override-password");
        source.setOverrideDefaultUser("override-user");
        source.setPreferredTestQuery("SELECT 1");
        source.setPrivilegeSpawnedThreads(true);
        source.setPropertyCycle(20);
        source.setStatementCacheNumDeferredCloseThreads(21);
        source.setTestConnectionOnCheckin(true);
        source.setTestConnectionOnCheckout(true);
        source.setUnreturnedConnectionTimeout(22);
        source.setUsesTraditionalReflectiveProxies(false);
        return source;
    }

    private static DriverManagerDataSource newSerializableNestedDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(false);
        dataSource.setJdbcUrl("jdbc:test:direct");
        dataSource.setDescription("nested serializable data source");
        return dataSource;
    }

    private static WrapperConnectionPoolDataSource roundTrip(WrapperConnectionPoolDataSource source) throws Exception {
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(serialize(source)))) {
            return (WrapperConnectionPoolDataSource) inputStream.readObject();
        }
    }

    private static byte[] serialize(WrapperConnectionPoolDataSource source) throws Exception {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(byteStream)) {
            outputStream.writeObject(source);
        }
        return byteStream.toByteArray();
    }

    private static final class NonSerializableReferenceableDataSource implements DataSource, Referenceable {
        @Override
        public Reference getReference() {
            Reference reference = new Reference(NonSerializableReferenceableDataSource.class.getName());
            reference.add(new StringRefAddr("name", "indirect-nested-data-source"));
            return reference;
        }

        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("Connection access is not required for serialization coverage.");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("Connection access is not required for serialization coverage.");
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
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("No wrapped implementation is available.");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
