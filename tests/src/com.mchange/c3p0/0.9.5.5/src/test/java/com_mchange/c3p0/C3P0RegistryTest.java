/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.AbstractConnectionCustomizer;
import com.mchange.v2.c3p0.C3P0Registry;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.ConnectionTester;
import com.mchange.v2.c3p0.example.IsValidOnlyConnectionTester30;
import org.junit.jupiter.api.Test;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class C3P0RegistryTest {
    @Test
    void configuredManagementCoordinatorRegistersAndUnregistersPools() throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ComboPooledDataSource source = C3p0Support.pool();
        ObjectName poolName = new ObjectName("com.mchange.v2.c3p0:type=PooledDataSource,identityToken="
                        + source.getIdentityToken() + ",name=" + source.getDataSourceName());
        try {
            assertTrue(server.isRegistered(poolName));
            assertEquals(1, server.getAttribute(poolName, "maxPoolSize"));
            assertTrue(C3P0Registry.getPooledDataSources().contains(source));
            try (Connection connection = source.getConnection()) {
                C3p0Support.assertQuery(connection, 73);
                assertEquals(1, server.getAttribute(poolName, "numBusyConnectionsDefaultUser"));
            }
        } finally {
            source.close();
        }
        assertFalse(server.isRegistered(poolName));
        assertFalse(C3P0Registry.getPooledDataSources().contains(source));
    }

    @Test
    void registryCachesConfiguredExtensionsAndRefreshesDefaultTester() throws Exception {
        ConnectionTester tester = C3P0Registry.getConnectionTester(IsValidOnlyConnectionTester30.class.getName());
        assertSame(tester, C3P0Registry.getConnectionTester(IsValidOnlyConnectionTester30.class.getName()));
        try (Connection connection = C3p0Support.unpooled().getConnection()) {
            assertEquals(ConnectionTester.CONNECTION_IS_OKAY, tester.activeCheckConnection(connection));
            C3P0Registry.markConfigRefreshed();
            assertEquals(ConnectionTester.CONNECTION_IS_OKAY,
                            C3P0Registry.getDefaultConnectionTester().activeCheckConnection(connection));
        }
        assertSame(C3P0Registry.getConnectionCustomizer(SessionCustomizer.class.getName()),
                        C3P0Registry.getConnectionCustomizer(SessionCustomizer.class.getName()));
        ComboPooledDataSource source = C3p0Support.pool();
        try {
            source.setConnectionCustomizerClassName(SessionCustomizer.class.getName());
            try (Connection connection = source.getConnection(); Statement statement = connection.createStatement();
                            var rows = statement.executeQuery("SELECT @checkout_label")) {
                assertTrue(rows.next());
                assertEquals("customized", rows.getString(1));
            }
        } finally {
            source.close();
        }
    }

    public static class SessionCustomizer extends AbstractConnectionCustomizer {
        @Override
        public void onCheckOut(Connection connection, String identityToken) throws Exception {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET @checkout_label = 'customized'");
            }
        }
    }
}
