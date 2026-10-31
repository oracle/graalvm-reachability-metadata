/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.PoolBackedDataSource;
import com.mchange.v2.c3p0.WrapperConnectionPoolDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class PoolBackedDataSourceBaseTest {
    @Test
    void serializedPoolReopensConnectionsWithItsConfiguration() throws Exception {
        PoolBackedDataSource original = new PoolBackedDataSource(false);
        original.setIdentityToken(UUID.randomUUID().toString());
        WrapperConnectionPoolDataSource wrapper = C3p0Support.wrapper(false);
        wrapper.setMinPoolSize(1);
        wrapper.setInitialPoolSize(1);
        wrapper.setMaxPoolSize(1);
        wrapper.setCheckoutTimeout(30000);
        wrapper.setAcquireRetryAttempts(1);
        original.setConnectionPoolDataSource(wrapper);
        original.setDataSourceName("serialized-reporting-pool");
        original.setNumHelperThreads(2);
        original.setExtensions(new HashMap<>(Map.of("purpose", "reporting")));
        try {
            PoolBackedDataSource restored = C3p0Support.roundTrip(original, PoolBackedDataSource.class);
            try {
                assertNotSame(original, restored);
                assertEquals("serialized-reporting-pool", restored.getDataSourceName());
                assertEquals("reporting", restored.getExtensions().get("purpose"));
                assertEquals(2, restored.getNumHelperThreads());
                try (Connection connection = restored.getConnection()) {
                    C3p0Support.assertQuery(connection, 45);
                }
            } finally {
                restored.close();
            }
        } finally {
            original.close();
        }
    }
}
