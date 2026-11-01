/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.sql.Connection;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.ConnectionProxy;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

public class TransactionAwareDataSourceProxyTest {
    @Test
    void reusesTransactionBoundConnectionAcrossClientHandles() {
        JdbcDataSource physical = TestDatabase.source();
        TransactionAwareDataSourceProxy source = new TransactionAwareDataSourceProxy(physical);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(physical));
        Object result = transaction.execute(status -> {
            try (Connection first = source.getConnection(); Connection second = source.getConnection()) {
                assertSame(((ConnectionProxy) first).getTargetConnection(), ((ConnectionProxy) second).getTargetConnection());
                first.close();
                assertFalse(second.isClosed());
                return TestDatabase.query(second);
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        });
        assertEquals(42, result);
    }
}
