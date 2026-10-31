/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.util.TestUtils;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestUtilsInnerStupidDataSourceInvocationHandlerTest {
    @Test
    void obtainsAConnectionThroughTheWrappedDataSource() throws Exception {
        ComboPooledDataSource source = C3p0Support.pool();
        try {
            DataSource wrapped = TestUtils.unreliableCommitDataSource(source);
            assertEquals(source.getLoginTimeout(), wrapped.getLoginTimeout());
            try (Connection connection = wrapped.getConnection()) {
                assertNotNull(connection);
                assertEquals(1, source.getNumBusyConnectionsDefaultUser());
            }
        } finally {
            source.close();
        }
    }
}
