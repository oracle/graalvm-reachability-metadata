/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import static org.assertj.core.api.Assertions.assertThat;

import com.mchange.v2.c3p0.WrapperConnectionPoolDataSource;
import com.mchange.v2.c3p0.impl.C3P0PooledConnectionPoolManager;
import org.junit.jupiter.api.Test;

public class C3P0PooledConnectionPoolManagerTest {
    @Test
    void readsConfigurationFromWrappedConnectionPoolDataSource() throws Exception {
        WrapperConnectionPoolDataSource source = new WrapperConnectionPoolDataSource(false);
        source.setContextClassLoaderSource("caller");
        source.setMaxAdministrativeTaskTime(0);
        source.setMinPoolSize(2);
        source.setPrivilegeSpawnedThreads(false);
        source.setStatementCacheNumDeferredCloseThreads(0);

        C3P0PooledConnectionPoolManager manager = new C3P0PooledConnectionPoolManager(
                source,
                null,
                null,
                1,
                "testIdentityToken",
                "testDataSource");

        try {
            assertThat(manager.getMinPoolSize(null)).isEqualTo(2);
            assertThat(manager.getNumManagedAuths()).isZero();
            assertThat(manager.getThreadPoolSize()).isEqualTo(1);
        } finally {
            manager.close();
        }
    }
}
