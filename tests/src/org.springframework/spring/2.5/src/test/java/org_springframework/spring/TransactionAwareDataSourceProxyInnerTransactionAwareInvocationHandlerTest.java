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
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

import static org.junit.jupiter.api.Assertions.*;

public class TransactionAwareDataSourceProxyInnerTransactionAwareInvocationHandlerTest {
    @Test
    void delegatesStatementsAndClosesNonTransactionalConnection() throws Exception {
        TransactionAwareDataSourceProxy source = new TransactionAwareDataSourceProxy(TestDatabase.source());
        Connection physical;
        try (Connection connection = source.getConnection()) {
            physical = ((ConnectionProxy) connection).getTargetConnection();
            assertEquals(42, TestDatabase.query(connection));
            assertFalse(connection.isClosed());
        }
        assertTrue(physical.isClosed());
    }
}
