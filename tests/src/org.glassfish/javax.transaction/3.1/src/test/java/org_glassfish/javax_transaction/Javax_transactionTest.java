/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish.javax_transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.transaction.HeuristicCommitException;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.InvalidTransactionException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionRequiredException;
import javax.transaction.TransactionRolledbackException;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.UserTransaction;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.junit.jupiter.api.Test;

public class Javax_transactionTest {
    @Test
    void transactionManagerCoordinatesXaResourceAndSynchronizationCallbacks() throws Exception {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        RecordingXaResource resource = new RecordingXaResource("inventory");
        RecordingSynchronization synchronization = new RecordingSynchronization();

        transactionManager.begin();
        Transaction transaction = transactionManager.getTransaction();

        assertThat(transactionManager.getStatus()).isEqualTo(Status.STATUS_ACTIVE);
        assertThat(transaction.enlistResource(resource)).isTrue();
        transaction.registerSynchronization(synchronization);
        assertThat(transaction.delistResource(resource, XAResource.TMSUCCESS)).isTrue();

        transactionManager.commit();

        assertThat(transactionManager.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
        assertThat(resource.operations()).containsExactly(
                "inventory:start:4660:0",
                "inventory:end:4660:67108864",
                "inventory:prepare:4660",
                "inventory:commit:4660:false");
        assertThat(synchronization.events()).containsExactly("beforeCompletion", "afterCompletion:3");
    }

    @Test
    void transactionRollbackRollsBackXaResourceAndSkipsBeforeCompletion() throws Exception {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        RecordingXaResource resource = new RecordingXaResource("orders");
        RecordingSynchronization synchronization = new RecordingSynchronization();

        transactionManager.begin();
        Transaction transaction = transactionManager.getTransaction();

        assertThat(transaction.enlistResource(resource)).isTrue();
        transaction.registerSynchronization(synchronization);
        assertThat(transaction.delistResource(resource, XAResource.TMFAIL)).isTrue();

        transactionManager.rollback();

        assertThat(transactionManager.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_ROLLEDBACK);
        assertThat(resource.operations()).containsExactly(
                "orders:start:4660:0",
                "orders:end:4660:536870912",
                "orders:rollback:4660");
        assertThat(synchronization.events()).containsExactly("afterCompletion:4");
    }

    @Test
    void userTransactionRollbackOnlyTurnsCommitIntoRollbackException() throws Exception {
        UserTransaction userTransaction = new RecordingTransactionManager();

        userTransaction.begin();
        assertThat(userTransaction.getStatus()).isEqualTo(Status.STATUS_ACTIVE);

        userTransaction.setRollbackOnly();
        assertThat(userTransaction.getStatus()).isEqualTo(Status.STATUS_MARKED_ROLLBACK);

        assertThatExceptionOfType(RollbackException.class)
                .isThrownBy(userTransaction::commit)
                .withMessage("Transaction was marked rollback-only");
        assertThat(userTransaction.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
    }

    @Test
    void transactionSynchronizationRegistryStoresResourcesAndInterposedSynchronizations() throws Exception {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        TransactionSynchronizationRegistry registry = transactionManager;
        RecordingSynchronization synchronization = new RecordingSynchronization();

        transactionManager.begin();
        Object key = registry.getTransactionKey();

        registry.putResource("tenant", "north");
        registry.registerInterposedSynchronization(synchronization);

        assertThat(key).isSameAs(registry.getTransactionKey());
        assertThat(registry.getResource("tenant")).isEqualTo("north");
        assertThat(registry.getTransactionStatus()).isEqualTo(Status.STATUS_ACTIVE);
        assertThat(registry.getRollbackOnly()).isFalse();

        registry.setRollbackOnly();

        assertThat(registry.getRollbackOnly()).isTrue();
        assertThat(registry.getTransactionStatus()).isEqualTo(Status.STATUS_MARKED_ROLLBACK);
        assertThatExceptionOfType(RollbackException.class).isThrownBy(transactionManager::commit);
        assertThat(synchronization.events()).containsExactly("afterCompletion:4");
    }

    @Test
    void transactionSynchronizationRegistryInterposedSynchronizationsRunAfterRegularSynchronizationsOnCommit()
            throws Exception {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        TransactionSynchronizationRegistry registry = transactionManager;
        List<String> events = new ArrayList<>();

        transactionManager.begin();

        transactionManager.getTransaction().registerSynchronization(new OrderedSynchronization("regular", events));
        registry.registerInterposedSynchronization(new OrderedSynchronization("interposed", events));
        transactionManager.commit();

        assertThat(events).containsExactly(
                "regular:beforeCompletion",
                "interposed:beforeCompletion",
                "regular:afterCompletion:3",
                "interposed:afterCompletion:3");
    }

    @Test
    void transactionManagerSupportsSuspendResumeAndTimeoutConfiguration() throws Exception {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();

        transactionManager.setTransactionTimeout(45);
        transactionManager.begin();
        Transaction transaction = transactionManager.getTransaction();

        assertThat(((SimpleTransaction) transaction).timeoutSeconds()).isEqualTo(45);
        assertThat(transactionManager.suspend()).isSameAs(transaction);
        assertThat(transactionManager.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);

        transactionManager.resume(transaction);
        assertThat(transactionManager.getTransaction()).isSameAs(transaction);
        assertThat(transactionManager.getStatus()).isEqualTo(Status.STATUS_ACTIVE);

        assertThatExceptionOfType(NotSupportedException.class).isThrownBy(transactionManager::begin);
        transactionManager.rollback();
        assertThat(transactionManager.timeoutSeconds()).isEqualTo(45);
    }

    @Test
    void xaResourceContractUsesStandardFlagsReturnCodesAndXidValues() throws Exception {
        RecordingXaResource first = new RecordingXaResource("first");
        RecordingXaResource second = new RecordingXaResource("second");
        SimpleXid xid = new SimpleXid(0xCAFE, new byte[] {1, 2, 3}, new byte[] {4, 5});

        first.start(xid, XAResource.TMNOFLAGS);
        first.end(xid, XAResource.TMSUSPEND);
        assertThat(first.prepare(xid)).isEqualTo(XAResource.XA_OK);
        first.commit(xid, true);
        first.rollback(xid);
        first.forget(xid);

        assertThat(first.isSameRM(first)).isTrue();
        assertThat(first.isSameRM(second)).isFalse();
        assertThat(first.setTransactionTimeout(30)).isTrue();
        assertThat(first.getTransactionTimeout()).isEqualTo(30);
        assertThat(first.recover(XAResource.TMSTARTRSCAN | XAResource.TMENDRSCAN)).containsExactly(xid);
        assertThat(first.operations()).containsExactly(
                "first:start:51966:0",
                "first:end:51966:33554432",
                "first:prepare:51966",
                "first:commit:51966:true",
                "first:rollback:51966",
                "first:forget:51966",
                "first:recover:25165824");
        assertThat(xid.getFormatId()).isEqualTo(0xCAFE);
        assertThat(xid.getGlobalTransactionId()).containsExactly(1, 2, 3);
        assertThat(xid.getBranchQualifier()).containsExactly(4, 5);
    }

    @Test
    void publicConstantsAndExceptionConstructorsExposeExpectedTransactionApiValues() {
        assertThat(new int[] {
                Status.STATUS_ACTIVE,
                Status.STATUS_MARKED_ROLLBACK,
                Status.STATUS_PREPARED,
                Status.STATUS_COMMITTED,
                Status.STATUS_ROLLEDBACK,
                Status.STATUS_UNKNOWN,
                Status.STATUS_NO_TRANSACTION,
                Status.STATUS_PREPARING,
                Status.STATUS_COMMITTING,
                Status.STATUS_ROLLING_BACK
        }).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

        assertThat(XAException.XA_RBBASE).isEqualTo(XAException.XA_RBROLLBACK);
        assertThat(XAException.XA_RBEND).isGreaterThan(XAException.XA_RBBASE);
        assertThat(XAException.XA_RDONLY).isEqualTo(XAResource.XA_RDONLY);
        assertThat(XAResource.XA_OK).isZero();
        assertThat(Xid.MAXGTRIDSIZE).isEqualTo(64);
        assertThat(Xid.MAXBQUALSIZE).isEqualTo(64);

        assertThat(new RollbackException("rollback requested")).hasMessage("rollback requested");
        assertThat(new HeuristicCommitException("heuristic commit")).hasMessage("heuristic commit");
        assertThat(new HeuristicMixedException("heuristic mixed")).hasMessage("heuristic mixed");
        assertThat(new HeuristicRollbackException("heuristic rollback")).hasMessage("heuristic rollback");
        assertThat(new InvalidTransactionException("invalid transaction")).hasMessage("invalid transaction");
        assertThat(new NotSupportedException("not supported")).hasMessage("not supported");
        assertThat(new TransactionRequiredException("required")).hasMessage("required");
        assertThat(new TransactionRolledbackException("rolled back")).hasMessage("rolled back");

        SystemException systemException = new SystemException(99);
        XAException xaException = new XAException(XAException.XAER_RMERR);
        assertThat(systemException.errorCode).isEqualTo(99);
        assertThat(xaException.errorCode).isEqualTo(XAException.XAER_RMERR);
        assertThat(new SystemException("system failure")).hasMessage("system failure");
        assertThat(new XAException("xa failure")).hasMessage("xa failure");
    }

    private static final class RecordingTransactionManager implements UserTransaction, TransactionManager,
            TransactionSynchronizationRegistry {
        private int timeoutSeconds;
        private SimpleTransaction currentTransaction;

        @Override
        public void begin() throws NotSupportedException {
            if (currentTransaction != null) {
                throw new NotSupportedException("Nested transactions are not supported");
            }
            currentTransaction = new SimpleTransaction();
            currentTransaction.timeoutSeconds = timeoutSeconds;
        }

        @Override
        public void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException,
                SecurityException, SystemException {
            try {
                requireCurrentTransaction().commit();
            } finally {
                currentTransaction = null;
            }
        }

        @Override
        public void rollback() throws SystemException {
            requireCurrentTransaction().rollback();
            currentTransaction = null;
        }

        @Override
        public void setRollbackOnly() {
            if (currentTransaction == null) {
                throw new IllegalStateException("No transaction is associated with the current thread");
            }
            currentTransaction.setRollbackOnly();
        }

        @Override
        public int getStatus() {
            return currentTransaction == null ? Status.STATUS_NO_TRANSACTION : currentTransaction.status;
        }

        @Override
        public void setTransactionTimeout(int seconds) {
            timeoutSeconds = seconds;
            if (currentTransaction != null) {
                currentTransaction.timeoutSeconds = seconds;
            }
        }

        @Override
        public Transaction getTransaction() {
            return currentTransaction;
        }

        @Override
        public void resume(Transaction transaction) throws InvalidTransactionException {
            if (!(transaction instanceof SimpleTransaction)) {
                throw new InvalidTransactionException("Unsupported transaction implementation");
            }
            currentTransaction = (SimpleTransaction) transaction;
        }

        @Override
        public Transaction suspend() {
            SimpleTransaction transaction = currentTransaction;
            currentTransaction = null;
            return transaction;
        }

        @Override
        public Object getTransactionKey() {
            return currentTransaction;
        }

        @Override
        public void putResource(Object key, Object value) {
            currentTransaction.resources.put(key, value);
        }

        @Override
        public Object getResource(Object key) {
            return currentTransaction.resources.get(key);
        }

        @Override
        public void registerInterposedSynchronization(Synchronization synchronization) {
            currentTransaction.interposedSynchronizations.add(synchronization);
        }

        @Override
        public int getTransactionStatus() {
            return getStatus();
        }

        @Override
        public boolean getRollbackOnly() {
            return currentTransaction != null && currentTransaction.status == Status.STATUS_MARKED_ROLLBACK;
        }

        int timeoutSeconds() {
            return timeoutSeconds;
        }

        private SimpleTransaction requireCurrentTransaction() throws SystemException {
            if (currentTransaction == null) {
                throw new SystemException("No transaction is associated with the current thread");
            }
            return currentTransaction;
        }
    }

    private static final class SimpleTransaction implements Transaction {
        private final SimpleXid xid = new SimpleXid(0x1234, new byte[] {10, 20}, new byte[] {30});
        private final List<XAResource> xaResources = new ArrayList<>();
        private final List<Synchronization> synchronizations = new ArrayList<>();
        private final List<Synchronization> interposedSynchronizations = new ArrayList<>();
        private final Map<Object, Object> resources = new HashMap<>();
        private int status = Status.STATUS_ACTIVE;
        private int timeoutSeconds;

        @Override
        public void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException,
                SystemException {
            if (status == Status.STATUS_MARKED_ROLLBACK) {
                rollback();
                throw new RollbackException("Transaction was marked rollback-only");
            }
            try {
                synchronizations.forEach(Synchronization::beforeCompletion);
                interposedSynchronizations.forEach(Synchronization::beforeCompletion);
                for (XAResource resource : xaResources) {
                    resource.prepare(xid);
                }
                status = Status.STATUS_COMMITTING;
                for (XAResource resource : xaResources) {
                    resource.commit(xid, false);
                }
                status = Status.STATUS_COMMITTED;
                notifyAfterCompletion(Status.STATUS_COMMITTED);
            } catch (XAException exception) {
                SystemException systemException = new SystemException(exception.errorCode);
                systemException.initCause(exception);
                throw systemException;
            }
        }

        @Override
        public boolean delistResource(XAResource xaResource, int flag) throws SystemException {
            try {
                xaResource.end(xid, flag);
                return true;
            } catch (XAException exception) {
                SystemException systemException = new SystemException(exception.errorCode);
                systemException.initCause(exception);
                throw systemException;
            }
        }

        @Override
        public boolean enlistResource(XAResource xaResource) throws RollbackException, SystemException {
            if (status == Status.STATUS_MARKED_ROLLBACK) {
                throw new RollbackException("Cannot enlist a resource in a rollback-only transaction");
            }
            try {
                xaResource.start(xid, XAResource.TMNOFLAGS);
                xaResources.add(xaResource);
                return true;
            } catch (XAException exception) {
                SystemException systemException = new SystemException(exception.errorCode);
                systemException.initCause(exception);
                throw systemException;
            }
        }

        @Override
        public int getStatus() {
            return status;
        }

        @Override
        public void registerSynchronization(Synchronization synchronization) throws RollbackException {
            if (status == Status.STATUS_MARKED_ROLLBACK) {
                throw new RollbackException("Cannot register synchronization in a rollback-only transaction");
            }
            synchronizations.add(synchronization);
        }

        @Override
        public void rollback() throws SystemException {
            try {
                status = Status.STATUS_ROLLING_BACK;
                for (XAResource resource : xaResources) {
                    resource.rollback(xid);
                }
                status = Status.STATUS_ROLLEDBACK;
                notifyAfterCompletion(Status.STATUS_ROLLEDBACK);
            } catch (XAException exception) {
                SystemException systemException = new SystemException(exception.errorCode);
                systemException.initCause(exception);
                throw systemException;
            }
        }

        @Override
        public void setRollbackOnly() {
            status = Status.STATUS_MARKED_ROLLBACK;
        }

        int timeoutSeconds() {
            return timeoutSeconds;
        }

        private void notifyAfterCompletion(int completionStatus) {
            synchronizations.forEach(synchronization -> synchronization.afterCompletion(completionStatus));
            interposedSynchronizations.forEach(synchronization -> synchronization.afterCompletion(completionStatus));
        }
    }

    private static final class RecordingSynchronization implements Synchronization {
        private final List<String> events = new ArrayList<>();

        @Override
        public void beforeCompletion() {
            events.add("beforeCompletion");
        }

        @Override
        public void afterCompletion(int status) {
            events.add("afterCompletion:" + status);
        }

        List<String> events() {
            return events;
        }
    }

    private static final class OrderedSynchronization implements Synchronization {
        private final String name;
        private final List<String> events;

        private OrderedSynchronization(String name, List<String> events) {
            this.name = name;
            this.events = events;
        }

        @Override
        public void beforeCompletion() {
            events.add(name + ":beforeCompletion");
        }

        @Override
        public void afterCompletion(int status) {
            events.add(name + ":afterCompletion:" + status);
        }
    }

    private static final class RecordingXaResource implements XAResource {
        private final String name;
        private final List<String> operations = new ArrayList<>();
        private int timeoutSeconds;
        private Xid recoveredXid;

        private RecordingXaResource(String name) {
            this.name = name;
        }

        @Override
        public void commit(Xid xid, boolean onePhase) {
            operations.add(name + ":commit:" + xid.getFormatId() + ":" + onePhase);
        }

        @Override
        public void end(Xid xid, int flags) {
            operations.add(name + ":end:" + xid.getFormatId() + ":" + flags);
        }

        @Override
        public void forget(Xid xid) {
            operations.add(name + ":forget:" + xid.getFormatId());
        }

        @Override
        public int getTransactionTimeout() {
            return timeoutSeconds;
        }

        @Override
        public boolean isSameRM(XAResource xaResource) {
            return this == xaResource;
        }

        @Override
        public int prepare(Xid xid) {
            operations.add(name + ":prepare:" + xid.getFormatId());
            return XA_OK;
        }

        @Override
        public Xid[] recover(int flag) {
            operations.add(name + ":recover:" + flag);
            return recoveredXid == null ? new Xid[0] : new Xid[] {recoveredXid};
        }

        @Override
        public void rollback(Xid xid) {
            operations.add(name + ":rollback:" + xid.getFormatId());
        }

        @Override
        public boolean setTransactionTimeout(int seconds) {
            timeoutSeconds = seconds;
            return true;
        }

        @Override
        public void start(Xid xid, int flags) {
            recoveredXid = xid;
            operations.add(name + ":start:" + xid.getFormatId() + ":" + flags);
        }

        List<String> operations() {
            return operations;
        }
    }

    private static final class SimpleXid implements Xid {
        private final int formatId;
        private final byte[] globalTransactionId;
        private final byte[] branchQualifier;

        private SimpleXid(int formatId, byte[] globalTransactionId, byte[] branchQualifier) {
            this.formatId = formatId;
            this.globalTransactionId = Arrays.copyOf(globalTransactionId, globalTransactionId.length);
            this.branchQualifier = Arrays.copyOf(branchQualifier, branchQualifier.length);
        }

        @Override
        public int getFormatId() {
            return formatId;
        }

        @Override
        public byte[] getGlobalTransactionId() {
            return Arrays.copyOf(globalTransactionId, globalTransactionId.length);
        }

        @Override
        public byte[] getBranchQualifier() {
            return Arrays.copyOf(branchQualifier, branchQualifier.length);
        }
    }
}
