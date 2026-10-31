/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.DriverManagerDataSource;
import com.mchange.v2.c3p0.WrapperConnectionPoolDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.PooledConnection;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class C3P0ImplUtilsTest {
    @Test
    void wrapperDiscoversNestedCredentialsAndJdbcCapabilities() throws Exception {
        DriverManagerDataSource nested = C3p0Support.unpooled();
        nested.setPassword("pool-password");
        WrapperConnectionPoolDataSource source = C3p0Support.wrapper(true);
        source.setNestedDataSource(nested);
        assertEquals("sa", source.getUser());
        assertEquals("pool-password", source.getPassword());
        PooledConnection pooled = source.getPooledConnection();
        try {
            try (Connection connection = pooled.getConnection()) {
                C3p0Support.assertQuery(connection, 59);
                connection.setHoldability(connection.getHoldability());
            }
        } finally {
            pooled.close();
        }
    }
}
