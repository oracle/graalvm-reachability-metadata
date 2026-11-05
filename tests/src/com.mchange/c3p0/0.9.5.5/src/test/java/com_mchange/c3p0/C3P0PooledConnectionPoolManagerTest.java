/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class C3P0PooledConnectionPoolManagerTest {
    @Test
    void poolManagerReadsBeanSettingsAndTracksCheckedOutConnections() throws Exception {
        ComboPooledDataSource source = C3p0Support.pool();
        try {
            source.setTestConnectionOnCheckout(true);
            source.setPreferredTestQuery("SELECT 1");
            source.setNumHelperThreads(2);
            try (Connection connection = source.getConnection()) {
                C3p0Support.assertQuery(connection, 61);
                assertEquals(1, source.getNumBusyConnectionsDefaultUser());
                assertEquals(1, source.getNumUserPools());
                assertEquals(2, source.getThreadPoolSize());
            }
            assertEquals(0, source.getNumBusyConnectionsDefaultUser());
            assertTrue(source.getNumIdleConnectionsDefaultUser() > 0);
            try (Connection connection = source.getConnection()) {
                C3p0Support.assertQuery(connection, 62);
            }
        } finally {
            source.close();
        }
    }
}
