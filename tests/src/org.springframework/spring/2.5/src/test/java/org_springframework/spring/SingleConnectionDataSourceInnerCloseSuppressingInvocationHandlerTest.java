/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.sql.Connection;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.ConnectionProxy;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.junit.jupiter.api.Assertions.*;

public class SingleConnectionDataSourceInnerCloseSuppressingInvocationHandlerTest {
    @Test
    void delegatesToPhysicalConnectionWithoutClosingIt() throws Exception {
        try (Connection physical = TestDatabase.source().getConnection()) {
            SingleConnectionDataSource source = new SingleConnectionDataSource(physical, true);
            try {
                Connection connection = source.getConnection();
                assertSame(physical, ((ConnectionProxy) connection).getTargetConnection());
                connection.setAutoCommit(false);
                assertFalse(physical.getAutoCommit());
                assertEquals(42, TestDatabase.query(connection));
                connection.close();
                assertFalse(physical.isClosed());
            } finally {
                source.destroy();
            }
        }
    }
}
