/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.WrapperConnectionPoolDataSource;
import com.mchange.v2.c3p0.example.IsValidOnlyConnectionTester30;
import org.junit.jupiter.api.Test;

import javax.sql.PooledConnection;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WrapperConnectionPoolDataSourceTest {
    @Test
    void configuredConnectionTesterIsLoadedWhenPropertyChanges() throws Exception {
        WrapperConnectionPoolDataSource source = C3p0Support.wrapper(false);
        source.setConnectionTesterClassName(IsValidOnlyConnectionTester30.class.getName());
        assertEquals(IsValidOnlyConnectionTester30.class.getName(), source.getConnectionTesterClassName());
        PooledConnection pooled = source.getPooledConnection("sa", "");
        try {
            try (Connection connection = pooled.getConnection()) {
                C3p0Support.assertQuery(connection, 56);
            }
        } finally {
            pooled.close();
        }
    }
}
