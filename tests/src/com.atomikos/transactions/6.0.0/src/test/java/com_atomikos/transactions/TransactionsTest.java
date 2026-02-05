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
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
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

    @Test
    void shouldPerformTwoPhaseCommitWithMultipleXaResources() throws Exception {
        UserTransactionManager utm = new UserTransactionManager();
        try {
            utm.init();

            TransactionManager tm = utm;
            tm.begin();
            Transaction tx = tm.getTransaction();
            assertThat(tm.getStatus()).isEqualTo(Status.STATUS_ACTIVE);

            RecordingXaResource r1 = new RecordingXaResource("r1");
            RecordingXaResource r2 = new RecordingXaResource("r2");

            assertThat(tx.enlistResource(r1)).isTrue();
            assertThat(tx.enlistResource(r2)).isTrue();

            // Explicitly delist to close the work on the resources
            assertThat(tx.delistResource(r1, XAResource.TMSUCCESS)).isTrue();
            assertThat(tx.delistResource(r2, XAResource.TMSUCCESS)).isTrue();

            tm.commit();

            // With two distinct XA resources, Atomikos should perform a 2PC (no one-phase optimization)
            assertThat(r1.startCalled).isTrue();
            assertThat(r1.endCalled).isTrue();
            assertThat(r1.prepareCalled).isTrue();
            assertThat(r1.commitCalled).isTrue();
            assertThat(r1.committedOnePhase).isFalse();
            assertThat(r1.rollbackCalled).isFalse();
            assertThat(r1.seenXid).isNotNull();

            assertThat(r2.startCalled).isTrue();
            assertThat(r2.endCalled).isTrue();
            assertThat(r2.prepareCalled).isTrue();
            assertThat(r2.commitCalled).isTrue();
            assertThat(r2.committedOnePhase).isFalse();
            assertThat(r2.rollbackCalled).isFalse();
            assertThat(r2.seenXid).isNotNull();

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

    /**
     * Recording XAResource for asserting enlistment and 2PC behavior.
     */
    private static final class RecordingXaResource implements XAResource {
        final String name;
        boolean startCalled;
        boolean endCalled;
        boolean prepareCalled;
        boolean commitCalled;
        boolean rollbackCalled;
        boolean committedOnePhase;
        Xid seenXid;

        RecordingXaResource(String name) {
            this.name = name;
        }

        @Override
        public void start(Xid xid, int flags) throws XAException {
            this.startCalled = true;
            this.seenXid = xid;
        }

        @Override
        public void end(Xid xid, int flags) throws XAException {
            this.endCalled = true;
        }

        @Override
        public int prepare(Xid xid) throws XAException {
            this.prepareCalled = true;
            return XAResource.XA_OK;
        }

        @Override
        public void commit(Xid xid, boolean onePhase) throws XAException {
            this.commitCalled = true;
            this.committedOnePhase = onePhase;
        }

        @Override
        public void rollback(Xid xid) throws XAException {
            this.rollbackCalled = true;
        }

        @Override
        public void forget(Xid xid) throws XAException {
            // no-op for test
        }

        @Override
        public Xid[] recover(int flag) throws XAException {
            return new Xid[0];
        }

        @Override
        public boolean isSameRM(XAResource xaResource) throws XAException {
            // Treat every instance as a distinct resource manager
            return false;
        }

        @Override
        public int getTransactionTimeout() throws XAException {
            return 0;
        }

        @Override
        public boolean setTransactionTimeout(int seconds) throws XAException {
            return true;
        }
    }
}
