/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import static org.assertj.core.api.Assertions.assertThat;

import com.mchange.v2.c3p0.AbstractConnectionCustomizer;
import com.mchange.v2.c3p0.AbstractConnectionTester;
import com.mchange.v2.c3p0.C3P0Registry;
import com.mchange.v2.c3p0.ConnectionCustomizer;
import com.mchange.v2.c3p0.ConnectionTester;
import java.sql.Connection;
import org.junit.jupiter.api.Test;

public class C3P0RegistryTest {
    @Test
    void createsAndCachesConnectionTesterByConfiguredClassName() {
        ConnectionTester tester = C3P0Registry.getConnectionTester(RegistryConnectionTester.class.getName());
        ConnectionTester cachedTester = C3P0Registry.getConnectionTester(RegistryConnectionTester.class.getName());

        assertThat(tester).isInstanceOf(RegistryConnectionTester.class);
        assertThat(cachedTester).isSameAs(tester);
        assertThat(tester.activeCheckConnection(null)).isEqualTo(ConnectionTester.CONNECTION_IS_OKAY);
    }

    @Test
    void createsAndCachesConnectionCustomizerByConfiguredClassName() throws Exception {
        String customizerClassName = RegistryConnectionCustomizer.class.getName();
        ConnectionCustomizer customizer = C3P0Registry.getConnectionCustomizer(customizerClassName);
        ConnectionCustomizer cachedCustomizer = C3P0Registry.getConnectionCustomizer(customizerClassName);

        assertThat(customizer).isInstanceOf(RegistryConnectionCustomizer.class);
        assertThat(cachedCustomizer).isSameAs(customizer);

        customizer.onAcquire(null, "registry-test-token");

        assertThat(RegistryConnectionCustomizer.lastParentDataSourceIdentityToken).isEqualTo("registry-test-token");
    }

    @Test
    void initializesRegistryManagementCoordinator() {
        ConnectionTester defaultTester = C3P0Registry.getDefaultConnectionTester();

        assertThat(defaultTester).isNotNull();
    }

    public static final class RegistryConnectionTester extends AbstractConnectionTester {
        public RegistryConnectionTester() {
        }

        @Override
        public int activeCheckConnection(
                Connection connection, String preferredTestQuery, Throwable[] rootCauseOutParamHolder) {
            return ConnectionTester.CONNECTION_IS_OKAY;
        }

        @Override
        public int statusOnException(
                Connection connection,
                Throwable throwable,
                String preferredTestQuery,
                Throwable[] rootCauseOutParamHolder) {
            return ConnectionTester.CONNECTION_IS_INVALID;
        }
    }

    public static final class RegistryConnectionCustomizer extends AbstractConnectionCustomizer {
        private static String lastParentDataSourceIdentityToken;

        public RegistryConnectionCustomizer() {
        }

        @Override
        public void onAcquire(Connection connection, String parentDataSourceIdentityToken) {
            lastParentDataSourceIdentityToken = parentDataSourceIdentityToken;
        }
    }
}
