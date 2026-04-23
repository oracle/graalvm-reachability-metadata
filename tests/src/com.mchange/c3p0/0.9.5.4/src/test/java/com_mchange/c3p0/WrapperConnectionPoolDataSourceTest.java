/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.WrapperConnectionPoolDataSource;
import com.mchange.v2.c3p0.impl.C3P0ImplUtils;
import org.junit.jupiter.api.Test;

import javax.sql.PooledConnection;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class WrapperConnectionPoolDataSourceTest {
    @Test
    void recreatesConnectionTesterAndCreatesPooledConnections() throws Exception {
        WrapperConnectionPoolDataSource dataSource = C3p0TestSupport.newWrapperConnectionPoolDataSource("wrapper", false);
        Map<String, Map<String, String>> overrides = new HashMap<>();
        Map<String, String> userOverrides = new HashMap<>();
        userOverrides.put("maxPoolSize", "4");
        overrides.put(C3p0TestSupport.USER, userOverrides);
        dataSource.setConnectionTesterClassName("com.mchange.v2.c3p0.impl.DefaultConnectionTester");
        dataSource.setAutomaticTestTable("TEST_TABLE");
        dataSource.setContextClassLoaderSource("library");
        dataSource.setPreferredTestQuery("SELECT 1");
        dataSource.setUserOverridesAsString(C3P0ImplUtils.createUserOverridesAsString(overrides));

        assertThat(dataSource.getUser()).isEqualTo(C3p0TestSupport.USER);
        assertThat(dataSource.getPassword()).isEqualTo(C3p0TestSupport.PASSWORD);

        assertThat(dataSource.getPreferredTestQuery()).isEqualTo("SELECT 1");
        assertThat(dataSource.getConnectionTesterClassName()).isEqualTo("com.mchange.v2.c3p0.impl.DefaultConnectionTester");

        PooledConnection pooledConnection = dataSource.getPooledConnection();
        try (Connection connection = pooledConnection.getConnection()) {
            assertThat(connection.isValid(1)).isTrue();
        } finally {
            pooledConnection.close();
        }
    }
}
