/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see http://creativecommons.org/publicdomain/zero/1.0/.
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

import java.util.concurrent.atomic.AtomicReference;

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
    void shouldResumeOnDifferentThreadAndCommit() throws Exception {
        UserTransactionManager utm = new UserTransactionManager();
        try {
            utm.init();

            TransactionManager tm = utm;

            // Begin and suspend in the main thread
            tm.begin();
            assertThat(tm.getStatus()).isEqualTo(Status.STATUS_ACTIVE);
            Transaction suspended = tm.suspend();
            assertThat(suspended).isNotNull();
            assertThat(tm.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);

            // Resume and commit on another thread
            AtomicReference<Throwable> error = new AtomicReference<>();
            Thread t = new Thread(() -> {
                try {
                    tm.resume(suspended);
                    assertThat(tm.getStatus()).isEqualTo(Status.STATUS_ACTIVE);
                    tm.commit();
                    assertThat(tm.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
                } catch (Throwable th) {
                    error.set(th);
                }
            });
            t.start();
            t.join();

            assertThat(error.get()).isNull();
            assertThat(tm.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
        } finally {
            utm.close();
        }
    }

    @Test
    void shouldEnlistTwoXaResourcesAndTwoPhaseCommit() throws Exception {
        // Allow Atomikos to auto-register arbitrary XAResources for recovery so they can be enlisted
        enableAutoResourceRegistration();

        UserTransactionManager utm = new UserTransactionManager();
        try {
            utm.init();

            TransactionManager tm = utm;
            tm.begin();
            Transaction tx = tm.getTransaction();

            RecordingXAResource r1 = new RecordingXAResource("r1");
            RecordingXAResource r2 = new RecordingXAResource("r2");

            assertThat(tx.enlistResource(r1)).isTrue();
            assertThat(tx.enlistResource(r2)).isTrue();

            tm.commit();

            // With 2 enlisted XA resources, Atomikos should execute a 2PC: prepare then commit(onePhase=false)
            assertThat(r1.starts).isEqualTo(1);
            assertThat(r1.ends).isEqualTo(1);
            assertThat(r1.prepares).isEqualTo(1);
            assertThat(r1.commits).isEqualTo(1);
            assertThat(r1.lastOnePhaseCommit).isFalse();
            assertThat(r1.rollbacks).isEqualTo(0);

            assertThat(r2.starts).isEqualTo(1);
            assertThat(r2.ends).isEqualTo(1);
            assertThat(r2.prepares).isEqualTo(1);
            assertThat(r2.commits).isEqualTo(1);
            assertThat(r2.lastOnePhaseCommit).isFalse();
            assertThat(r2.rollbacks).isEqualTo(0);

            assertThat(tm.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
        } finally {
            utm.close();
        }
    }

    @Test
    void shouldRollbackEnlistedXaResourceOnRollbackOnly() throws Exception {
        // Allow Atomikos to auto-register arbitrary XAResources for recovery so they can be enlisted
        enableAutoResourceRegistration();

        UserTransactionManager utm = new UserTransactionManager();
        try {
            utm.init();

            TransactionManager tm = utm;
            tm.begin();
            Transaction tx = tm.getTransaction();

            RecordingXAResource resource = new RecordingXAResource("res");
            assertThat(tx.enlistResource(resource)).isTrue();

            tm.setRollbackOnly();
            assertThatThrownBy(tm::commit)
                .isInstanceOf(RollbackException.class);

            assertThat(resource.rollbacks).isEqualTo(1);
            assertThat(resource.commits).isEqualTo(0);
            assertThat(tm.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
        } finally {
            utm.close();
        }
    }

    private static void enableAutoResourceRegistration() {
        System.setProperty("com.atomikos.icatch.automatic_resource_registration", "true");
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
     * Simple XAResource implementation to observe XA interactions.
     */
    private static final class RecordingXAResource implements XAResource {
        final String name;
        int starts;
        int startFlags;
        int ends;
        int endFlags;
        int prepares;
        int commits;
        boolean lastOnePhaseCommit;
        int rollbacks;
        int timeoutSeconds;

        RecordingXAResource(String name) {
            this.name = name;
        }

        @Override
        public void commit(Xid xid, boolean onePhase) throws XAException {
            commits++;
            lastOnePhaseCommit = onePhase;
        }

        @Override
        public void end(Xid xid, int flags) throws XAException {
            ends++;
            endFlags = flags;
        }

        @Override
        public void forget(Xid xid) throws XAException {
            // not used
        }

        @Override
        public int getTransactionTimeout() throws XAException {
            return timeoutSeconds;
        }

        @Override
        public boolean isSameRM(XAResource xares) throws XAException {
            // Same RM only if it's the exact same instance
            return this == xares;
        }

        @Override
        public int prepare(Xid xid) throws XAException {
            prepares++;
            return XA_OK;
        }

        @Override
        public Xid[] recover(int flag) throws XAException {
            return new Xid[0];
        }

        @Override
        public void rollback(Xid xid) throws XAException {
            rollbacks++;
        }

        @Override
        public boolean setTransactionTimeout(int seconds) throws XAException {
            timeoutSeconds = seconds;
            return true;
        }

        @Override
        public void start(Xid xid, int flags) throws XAException {
            starts++;
            startFlags = flags;
        }

        @Override
        public String toString() {
            return "RecordingXAResource{" +
                "name='" + name + '\'' +
                ", starts=" + starts +
                ", ends=" + ends +
                ", prepares=" + prepares +
                ", commits=" + commits +
                ", rollbacks=" + rollbacks +
                ", lastOnePhaseCommit=" + lastOnePhaseCommit +
                '}';
        }
    }
}
