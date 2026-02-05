/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_atomikos.transactions;

import com.atomikos.icatch.jta.UserTransactionImp;
import com.atomikos.icatch.jta.UserTransactionManager;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionsTest {

    @Test
    void shouldBeginAndCommitWithUserTransaction() throws Exception {
        UserTransactionManager utm = new UserTransactionManager();
        UserTransactionImp ut = new UserTransactionImp();
        try {
            utm.init();

            ut.begin();
            assertThat(ut.getStatus()).isEqualTo(Status.STATUS_ACTIVE);

            ut.commit();
            assertThat(ut.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
        } finally {
            utm.close();
        }
    }

    @Test
    void shouldRollbackOnlyOnCommit() throws Exception {
        UserTransactionManager utm = new UserTransactionManager();
        UserTransactionImp ut = new UserTransactionImp();
        try {
            utm.init();

            ut.begin();
            assertThat(ut.getStatus()).isEqualTo(Status.STATUS_ACTIVE);

            ut.setRollbackOnly();
            assertThatThrownBy(ut::commit)
                .isInstanceOf(RollbackException.class);

            assertThat(ut.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
        } finally {
            utm.close();
        }
    }

    @Test
    void shouldRegisterSynchronizationAndCommit() throws Exception {
        UserTransactionManager utm = new UserTransactionManager();
        try {
            utm.init();

            TransactionManager tm = utm; // UserTransactionManager implements TransactionManager
            tm.begin();
            Transaction tx = tm.getTransaction();
            assertThat(tm.getStatus()).isEqualTo(Status.STATUS_ACTIVE);

            RecordingSynchronization sync = new RecordingSynchronization();
            tx.registerSynchronization(sync);

            tm.commit();

            assertThat(sync.beforeCalled).isTrue();
            assertThat(sync.afterCalled).isTrue();
            assertThat(sync.afterStatus).isEqualTo(Status.STATUS_COMMITTED);
            assertThat(tm.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
        } finally {
            utm.close();
        }
    }

    @Test
    void shouldInvokeSynchronizationOnRollback() throws Exception {
        UserTransactionManager utm = new UserTransactionManager();
        try {
            utm.init();

            TransactionManager tm = utm;
            tm.begin();
            Transaction tx = tm.getTransaction();

            RecordingSynchronization sync = new RecordingSynchronization();
            tx.registerSynchronization(sync);

            // Mark the transaction for rollback and attempt to commit
            tm.setRollbackOnly();
            assertThatThrownBy(tm::commit)
                .isInstanceOf(RollbackException.class);

            // JTA does not guarantee beforeCompletion on rollback-only paths across implementations.
            // Verify afterCompletion is invoked with ROLLEDBACK and transaction is cleared.
            assertThat(sync.afterCalled).isTrue();
            assertThat(sync.afterStatus).isEqualTo(Status.STATUS_ROLLEDBACK);
            assertThat(tm.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
        } finally {
            utm.close();
        }
    }

    @Test
    void shouldEnforceTransactionTimeout() throws Exception {
        UserTransactionManager utm = new UserTransactionManager();
        UserTransactionImp ut = new UserTransactionImp();
        try {
            utm.init();

            ut.setTransactionTimeout(1);
            ut.begin();
            assertThat(ut.getStatus()).isEqualTo(Status.STATUS_ACTIVE);

            // Sleep beyond the timeout threshold
            Thread.sleep(1500);

            assertThatThrownBy(ut::commit)
                .isInstanceOf(RollbackException.class);

            assertThat(ut.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
        } finally {
            utm.close();
        }
    }

    @Test
    void shouldSuspendAndResumeTransaction() throws Exception {
        UserTransactionManager utm = new UserTransactionManager();
        try {
            utm.init();

            TransactionManager tm = utm;

            tm.begin();
            assertThat(tm.getStatus()).isEqualTo(Status.STATUS_ACTIVE);

            Transaction suspended = tm.suspend();
            assertThat(tm.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
            assertThat(suspended).isNotNull();

            // Work in a different transaction
            tm.begin();
            assertThat(tm.getStatus()).isEqualTo(Status.STATUS_ACTIVE);
            tm.commit();
            assertThat(tm.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);

            // Resume original transaction and commit
            tm.resume(suspended);
            assertThat(tm.getStatus()).isEqualTo(Status.STATUS_ACTIVE);
            tm.commit();

            assertThat(tm.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
        } finally {
            utm.close();
        }
    }

    /**
     * Minimal Synchronization implementation that records callbacks.
     */
    private static final class RecordingSynchronization implements Synchronization {
        boolean beforeCalled;
        boolean afterCalled;
        Integer afterStatus;

        @Override
        public void beforeCompletion() {
            beforeCalled = true;
        }

        @Override
        public void afterCompletion(int status) {
            afterCalled = true;
            afterStatus = status;
        }
    }
}
