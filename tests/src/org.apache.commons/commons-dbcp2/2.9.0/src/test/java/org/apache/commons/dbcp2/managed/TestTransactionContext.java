/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.commons.dbcp2.managed;

import org.apache.geronimo.transaction.manager.TransactionImpl;
import org.apache.geronimo.transaction.manager.TransactionManagerImpl;
import org.junit.jupiter.api.Test;

import javax.transaction.xa.XAResource;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestTransactionContext {
    private static class UncooperativeTransaction extends TransactionImpl {
        UncooperativeTransaction() {
            super(null, null);
        }

        @Override
        public synchronized boolean enlistResource(final XAResource xaRes) {
            return false;
        }
    }

    @Test
    public void testSetSharedConnectionEnlistFailure() throws Exception {
        try (BasicManagedDataSource basicManagedDataSource = new BasicManagedDataSource()) {
            basicManagedDataSource.setTransactionManager(new TransactionManagerImpl());
            basicManagedDataSource.setDriverClassName("org.apache.commons.dbcp2.TesterDriver");
            basicManagedDataSource.setUrl("jdbc:apache:commons:testdriver");
            basicManagedDataSource.setUsername("userName");
            basicManagedDataSource.setPassword("password");
            basicManagedDataSource.setMaxIdle(1);
            try (ManagedConnection<?> conn = (ManagedConnection<?>) basicManagedDataSource.getConnection()) {
                final UncooperativeTransaction transaction = new UncooperativeTransaction();
                final TransactionContext transactionContext = new TransactionContext(
                        basicManagedDataSource.getTransactionRegistry(), transaction);
                assertThrows(SQLException.class, () -> transactionContext.setSharedConnection(conn));
            }
        }
    }

}

