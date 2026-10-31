/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.WrapperConnectionPoolDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.PooledConnection;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class WrapperConnectionPoolDataSourceBaseTest {
    @Test
    void serializedWrapperPreservesItsNestedSourceAndPoolSettings() throws Exception {
        WrapperConnectionPoolDataSource original = C3p0Support.wrapper(false);
        original.setMaxPoolSize(4);
        original.setCheckoutTimeout(30000);
        original.setPreferredTestQuery("SELECT 1");
        original.setOverrideDefaultUser("sa");
        original.setOverrideDefaultPassword("");
        WrapperConnectionPoolDataSource restored = C3p0Support.roundTrip(original, WrapperConnectionPoolDataSource.class);
        assertNotSame(original, restored);
        assertEquals(4, restored.getMaxPoolSize());
        assertEquals(30000, restored.getCheckoutTimeout());
        assertEquals("SELECT 1", restored.getPreferredTestQuery());
        assertEquals("sa", restored.getOverrideDefaultUser());
        PooledConnection pooled = restored.getPooledConnection();
        try {
            try (Connection connection = pooled.getConnection()) {
                C3p0Support.assertQuery(connection, 44);
            }
        } finally {
            pooled.close();
        }
    }
}
