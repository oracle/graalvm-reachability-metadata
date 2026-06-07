/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_transaction.jakarta_transaction_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.transaction.HeuristicCommitException;
import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.InvalidTransactionException;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionRequiredException;
import jakarta.transaction.TransactionRolledbackException;
import jakarta.transaction.TransactionScoped;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import jakarta.transaction.TransactionalException;
import jakarta.transaction.UserTransaction;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import org.junit.jupiter.api.Test;

public class Jakarta_transaction_apiTest {
    @Test
    void statusConstantsExposeTheSpecifiedStableValues() {
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
    }

    @Test
    void checkedAndRuntimeExceptionsExposeMessagesCausesAndErrorCodes() {
        assertThat(new HeuristicCommitException()).hasMessage((String) null);
        assertThat(new HeuristicCommitException("heuristic commit")).hasMessage("heuristic commit");
        assertThat(new HeuristicMixedException()).hasMessage((String) null);
        assertThat(new HeuristicMixedException("heuristic mixed")).hasMessage("heuristic mixed");
        assertThat(new HeuristicRollbackException()).hasMessage((String) null);
        assertThat(new HeuristicRollbackException("heuristic rollback")).hasMessage("heuristic rollback");
        assertThat(new InvalidTransactionException()).hasMessage((String) null);
        assertThat(new InvalidTransactionException("invalid transaction")).hasMessage("invalid transaction");
        assertThat(new NotSupportedException()).hasMessage((String) null);
        assertThat(new NotSupportedException("nested transaction")).hasMessage("nested transaction");
        assertThat(new RollbackException()).hasMessage((String) null);
        assertThat(new RollbackException("rolled back")).hasMessage("rolled back");
        assertThat(new TransactionRequiredException()).hasMessage((String) null);
        assertThat(new TransactionRequiredException("transaction required")).hasMessage("transaction required");
        assertThat(new TransactionRolledbackException()).hasMessage((String) null);
        assertThat(new TransactionRolledbackException("remote rollback")).hasMessage("remote rollback");

        assertThat(new SystemException()).hasMessage((String) null);
        SystemException systemException = new SystemException(91);
        assertThat(systemException.errorCode).isEqualTo(91);
        assertThat(new SystemException("system failure")).hasMessage("system failure");

        IllegalStateException cause = new IllegalStateException("no transaction");
        TransactionalException transactionalException = new TransactionalException("transactional failure", cause);
        assertThat(transactionalException).hasMessage("transactional failure").hasCause(cause);
    }

    @Test
    void transactionalTxTypeEnumExposesAllDeclarativeModes() {
        assertThat(TxType.values()).containsExactly(
                TxType.REQUIRED,
                TxType.REQUIRES_NEW,
                TxType.MANDATORY,
                TxType.SUPPORTS,
                TxType.NOT_SUPPORTED,
                TxType.NEVER);
        assertThat(TxType.valueOf("REQUIRES_NEW")).isSameAs(TxType.REQUIRES_NEW);
    }

    @Test
    void transactionalAnnotationsCanDecorateApplicationTypes() {
        AnnotatedComponent component = new AnnotatedComponent();

        assertThat(component.required()).isEqualTo("required");
        assertThat(component.never()).isEqualTo("never");
    }

    @Test
    void userTransactionBeginsCommitsRollsBackAndRejectsInvalidStates() throws Exception {
        InMemoryTransactionManager transactionManager = new InMemoryTransactionManager();
        UserTransaction userTransaction = transactionManager;

        assertThat(userTransaction.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);

        userTransaction.begin();
        assertThat(userTransaction.getStatus()).isEqualTo(Status.STATUS_ACTIVE);
        assertThatThrownBy(userTransaction::begin).isInstanceOf(NotSupportedException.class)
                .hasMessageContaining("already associated");

        userTransaction.commit();
        assertThat(userTransaction.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
        assertThatThrownBy(userTransaction::commit).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No transaction");

        userTransaction.begin();
        userTransaction.setRollbackOnly();
        assertThat(userTransaction.getStatus()).isEqualTo(Status.STATUS_MARKED_ROLLBACK);
        assertThatThrownBy(userTransaction::commit).isInstanceOf(RollbackException.class)
                .hasMessageContaining("marked for rollback");
        assertThat(userTransaction.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);

        assertThatThrownBy(() -> userTransaction.setTransactionTimeout(-1)).isInstanceOf(SystemException.class)
                .hasMessageContaining("negative");
        userTransaction.setTransactionTimeout(3);
        assertThat(transactionManager.getTimeoutSeconds()).isEqualTo(3);
    }

    @Test
    void transactionCoordinatesResourcesAndSynchronizationCallbacks() throws Exception {
        InMemoryTransactionManager transactionManager = new InMemoryTransactionManager();
        RecordingSynchronization synchronization = new RecordingSynchronization();
        XAResource resource = new NoOpXaResource();

        transactionManager.begin();
        Transaction transaction = transactionManager.getTransaction();

        assertThat(transaction.enlistResource(resource)).isTrue();
        transaction.registerSynchronization(synchronization);
        assertThat(transaction.delistResource(resource, XAResource.TMSUCCESS)).isTrue();

        transactionManager.commit();

        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_COMMITTED);
        assertThat(synchronization.events).containsExactly("before", "after:3");
    }

    @Test
    void transactionManagerSuspendsResumesAndRollsBackTransactions() throws Exception {
        InMemoryTransactionManager transactionManager = new InMemoryTransactionManager();

        transactionManager.begin();
        Transaction suspended = transactionManager.suspend();

        assertThat(transactionManager.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
        assertThat(suspended.getStatus()).isEqualTo(Status.STATUS_ACTIVE);
        assertThatThrownBy(() -> transactionManager.resume(new ForeignTransaction()))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("not managed");

        transactionManager.resume(suspended);
        assertThat(transactionManager.getTransaction()).isSameAs(suspended);
        transactionManager.rollback();

        assertThat(suspended.getStatus()).isEqualTo(Status.STATUS_ROLLEDBACK);
        assertThat(transactionManager.getTransaction()).isNull();
    }

    @Test
    void transactionSynchronizationRegistryStoresResourcesAndInterposedCallbacks() throws Exception {
        InMemoryTransactionManager transactionManager = new InMemoryTransactionManager();
        TransactionSynchronizationRegistry registry = transactionManager;
        RecordingSynchronization synchronization = new RecordingSynchronization();
        Object resourceKey = new Object();

        transactionManager.begin();
        Object transactionKey = registry.getTransactionKey();

        registry.putResource(resourceKey, "resource-value");
        registry.registerInterposedSynchronization(synchronization);
        assertThat(registry.getTransactionKey()).isSameAs(transactionKey);
        assertThat(registry.getResource(resourceKey)).isEqualTo("resource-value");
        assertThatThrownBy(() -> registry.getResource(null)).isInstanceOf(NullPointerException.class);
        assertThat(registry.getRollbackOnly()).isFalse();
        assertThat(registry.getTransactionStatus()).isEqualTo(Status.STATUS_ACTIVE);

        registry.setRollbackOnly();
        assertThat(registry.getRollbackOnly()).isTrue();
        assertThat(registry.getTransactionStatus()).isEqualTo(Status.STATUS_MARKED_ROLLBACK);

        assertThatThrownBy(transactionManager::commit).isInstanceOf(RollbackException.class);
        assertThat(synchronization.events).containsExactly("after:4");
        assertThat(registry.getTransactionKey()).isNull();
        assertThatThrownBy(() -> registry.putResource(resourceKey, "late-value"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No transaction");
    }

    @Test
    void xaExceptionAndXidExposeDistributedTransactionFailureAndIdentifierContracts() {
        XAException rollbackException = new XAException(XAException.XA_RBDEADLOCK);
        XAException resourceManagerException = new XAException(XAException.XAER_RMERR);
        byte[] globalTransactionId = new byte[] {1, 2, 3};
        byte[] branchQualifier = new byte[] {4, 5};
        Xid xid = new FixedXid(12, globalTransactionId, branchQualifier);

        assertThat(rollbackException.errorCode).isEqualTo(XAException.XA_RBDEADLOCK);
        assertThat(resourceManagerException.errorCode).isEqualTo(XAException.XAER_RMERR);
        assertThat(XAException.XA_RBROLLBACK).isEqualTo(XAException.XA_RBBASE);
        assertThat(XAException.XA_RBTRANSIENT).isEqualTo(XAException.XA_RBEND);
        assertThat(xid.getFormatId()).isEqualTo(12);
        assertThat(xid.getGlobalTransactionId()).containsExactly(globalTransactionId);
        assertThat(xid.getBranchQualifier()).containsExactly(branchQualifier);
        assertThat(xid.getGlobalTransactionId().length).isLessThanOrEqualTo(Xid.MAXGTRIDSIZE);
        assertThat(xid.getBranchQualifier().length).isLessThanOrEqualTo(Xid.MAXBQUALSIZE);
    }

    @Test
    void xaResourceLifecycleSupportsTimeoutRecoveryAndResourceManagerIdentity() throws Exception {
        Xid xid = new FixedXid(77, new byte[] {7, 8, 9}, new byte[] {10});
        RecordingXaResource resource = new RecordingXaResource("resource-manager");
        RecordingXaResource sameResourceManager = new RecordingXaResource("resource-manager");
        RecordingXaResource differentResourceManager = new RecordingXaResource("other-resource-manager");

        assertThat(resource.setTransactionTimeout(15)).isTrue();
        assertThat(resource.getTransactionTimeout()).isEqualTo(15);
        resource.start(xid, XAResource.TMNOFLAGS);
        resource.end(xid, XAResource.TMSUSPEND);
        resource.start(xid, XAResource.TMRESUME);
        resource.end(xid, XAResource.TMSUCCESS);

        assertThat(resource.prepare(xid)).isEqualTo(XAResource.XA_OK);
        resource.commit(xid, false);
        resource.forget(xid);

        assertThat(resource.recover(XAResource.TMSTARTRSCAN | XAResource.TMENDRSCAN)).containsExactly(xid);
        assertThat(resource.isSameRM(sameResourceManager)).isTrue();
        assertThat(resource.isSameRM(differentResourceManager)).isFalse();
        assertThat(resource.events).containsExactly(
                "timeout:15",
                "get-timeout",
                "start:77:" + XAResource.TMNOFLAGS,
                "end:77:" + XAResource.TMSUSPEND,
                "start:77:" + XAResource.TMRESUME,
                "end:77:" + XAResource.TMSUCCESS,
                "prepare:77",
                "commit:77:false",
                "forget:77",
                "recover:" + (XAResource.TMSTARTRSCAN | XAResource.TMENDRSCAN));
    }

    @Transactional
    @TransactionScoped
    private static final class AnnotatedComponent {
        String required() {
            return "required";
        }

        @Transactional(value = TxType.NEVER, dontRollbackOn = IllegalArgumentException.class)
        String never() {
            return "never";
        }
    }

    private static final class FixedXid implements Xid {
        private final int formatId;
        private final byte[] globalTransactionId;
        private final byte[] branchQualifier;

        FixedXid(int formatId, byte[] globalTransactionId, byte[] branchQualifier) {
            this.formatId = formatId;
            this.globalTransactionId = globalTransactionId.clone();
            this.branchQualifier = branchQualifier.clone();
        }

        @Override
        public int getFormatId() {
            return formatId;
        }

        @Override
        public byte[] getGlobalTransactionId() {
            return globalTransactionId.clone();
        }

        @Override
        public byte[] getBranchQualifier() {
            return branchQualifier.clone();
        }
    }

    private static final class RecordingSynchronization implements Synchronization {
        private final List<String> events = new ArrayList<>();

        @Override
        public void beforeCompletion() {
            events.add("before");
        }

        @Override
        public void afterCompletion(int status) {
            events.add("after:" + status);
        }
    }

    private static final class InMemoryTransactionManager
            implements TransactionManager, UserTransaction, TransactionSynchronizationRegistry {
        private InMemoryTransaction current;
        private int timeoutSeconds;

        @Override
        public void begin() throws NotSupportedException {
            if (current != null) {
                throw new NotSupportedException("Thread already associated with a transaction");
            }
            current = new InMemoryTransaction();
        }

        @Override
        public void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException,
                SecurityException, IllegalStateException, SystemException {
            InMemoryTransaction transaction = requireCurrentTransaction();
            try {
                transaction.commit();
            } finally {
                current = null;
            }
        }

        @Override
        public int getStatus() throws SystemException {
            return current == null ? Status.STATUS_NO_TRANSACTION : current.getStatus();
        }

        @Override
        public Transaction getTransaction() {
            return current;
        }

        @Override
        public void resume(Transaction tobj) throws InvalidTransactionException {
            if (current != null) {
                throw new IllegalStateException("Thread already associated with a transaction");
            }
            if (!(tobj instanceof InMemoryTransaction)) {
                throw new InvalidTransactionException("Transaction is not managed by this manager");
            }
            InMemoryTransaction transaction = (InMemoryTransaction) tobj;
            if (transaction.isFinished()) {
                throw new InvalidTransactionException("Transaction is already complete");
            }
            current = transaction;
        }

        @Override
        public void rollback() throws SystemException {
            InMemoryTransaction transaction = requireCurrentTransaction();
            try {
                transaction.rollback();
            } finally {
                current = null;
            }
        }

        @Override
        public void setRollbackOnly() {
            requireCurrentTransaction().setRollbackOnly();
        }

        @Override
        public void setTransactionTimeout(int seconds) throws SystemException {
            if (seconds < 0) {
                throw new SystemException("Transaction timeout cannot be negative");
            }
            timeoutSeconds = seconds;
        }

        @Override
        public Transaction suspend() {
            InMemoryTransaction suspended = current;
            current = null;
            return suspended;
        }

        @Override
        public Object getTransactionKey() {
            return current == null ? null : current.transactionKey;
        }

        @Override
        public void putResource(Object key, Object value) {
            requireActiveRegistryTransaction().putResource(key, value);
        }

        @Override
        public Object getResource(Object key) {
            return requireActiveRegistryTransaction().getResource(key);
        }

        @Override
        public void registerInterposedSynchronization(Synchronization sync) {
            requireActiveRegistryTransaction().registerInterposedSynchronization(sync);
        }

        @Override
        public int getTransactionStatus() {
            return current == null ? Status.STATUS_NO_TRANSACTION : current.status;
        }

        @Override
        public boolean getRollbackOnly() {
            return requireActiveRegistryTransaction().status == Status.STATUS_MARKED_ROLLBACK;
        }

        int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        private InMemoryTransaction requireCurrentTransaction() {
            if (current == null) {
                throw new IllegalStateException("No transaction is associated with the current thread");
            }
            return current;
        }

        private InMemoryTransaction requireActiveRegistryTransaction() {
            InMemoryTransaction transaction = requireCurrentTransaction();
            if (transaction.isFinished()) {
                throw new IllegalStateException("No active transaction is associated with the current thread");
            }
            return transaction;
        }
    }

    private static final class InMemoryTransaction implements Transaction {
        private final Object transactionKey = new Object();
        private final Map<Object, Object> resources = new HashMap<>();
        private final List<XAResource> enlistedResources = new ArrayList<>();
        private final List<Synchronization> synchronizations = new ArrayList<>();
        private final List<Synchronization> interposedSynchronizations = new ArrayList<>();
        private int status = Status.STATUS_ACTIVE;

        @Override
        public void commit() throws RollbackException {
            ensureActive();
            if (status == Status.STATUS_MARKED_ROLLBACK) {
                rollback();
                throw new RollbackException("Transaction marked for rollback");
            }
            status = Status.STATUS_COMMITTING;
            for (Synchronization synchronization : synchronizations) {
                synchronization.beforeCompletion();
            }
            for (Synchronization synchronization : interposedSynchronizations) {
                synchronization.beforeCompletion();
            }
            status = Status.STATUS_COMMITTED;
            notifyAfterCompletion(Status.STATUS_COMMITTED);
        }

        @Override
        public boolean delistResource(XAResource xaRes, int flag) {
            ensureActive();
            return enlistedResources.remove(xaRes);
        }

        @Override
        public boolean enlistResource(XAResource xaRes) throws RollbackException {
            ensureActive();
            if (status == Status.STATUS_MARKED_ROLLBACK) {
                throw new RollbackException("Transaction marked for rollback");
            }
            return enlistedResources.add(xaRes);
        }

        @Override
        public int getStatus() {
            return status;
        }

        @Override
        public void registerSynchronization(Synchronization sync) throws RollbackException {
            ensureActive();
            if (status == Status.STATUS_MARKED_ROLLBACK) {
                throw new RollbackException("Transaction marked for rollback");
            }
            synchronizations.add(sync);
        }

        @Override
        public void rollback() {
            ensureActive();
            status = Status.STATUS_ROLLING_BACK;
            status = Status.STATUS_ROLLEDBACK;
            notifyAfterCompletion(Status.STATUS_ROLLEDBACK);
        }

        @Override
        public void setRollbackOnly() {
            ensureActive();
            status = Status.STATUS_MARKED_ROLLBACK;
        }

        void putResource(Object key, Object value) {
            if (key == null) {
                throw new NullPointerException("Resource key cannot be null");
            }
            resources.put(key, value);
        }

        Object getResource(Object key) {
            if (key == null) {
                throw new NullPointerException("Resource key cannot be null");
            }
            return resources.get(key);
        }

        void registerInterposedSynchronization(Synchronization sync) {
            ensureActive();
            interposedSynchronizations.add(sync);
        }

        boolean isFinished() {
            return status == Status.STATUS_COMMITTED || status == Status.STATUS_ROLLEDBACK;
        }

        private void notifyAfterCompletion(int completionStatus) {
            for (Synchronization synchronization : interposedSynchronizations) {
                synchronization.afterCompletion(completionStatus);
            }
            for (Synchronization synchronization : synchronizations) {
                synchronization.afterCompletion(completionStatus);
            }
        }

        private void ensureActive() {
            if (isFinished()) {
                throw new IllegalStateException("Transaction is already complete");
            }
        }
    }

    private static final class RecordingXaResource implements XAResource {
        private final String resourceManagerId;
        private final List<String> events = new ArrayList<>();
        private int timeoutSeconds;
        private Xid recoverableXid;

        RecordingXaResource(String resourceManagerId) {
            this.resourceManagerId = resourceManagerId;
        }

        @Override
        public void commit(Xid xid, boolean onePhase) throws XAException {
            events.add("commit:" + xid.getFormatId() + ":" + onePhase);
        }

        @Override
        public void end(Xid xid, int flags) throws XAException {
            events.add("end:" + xid.getFormatId() + ":" + flags);
        }

        @Override
        public void forget(Xid xid) throws XAException {
            events.add("forget:" + xid.getFormatId());
        }

        @Override
        public int getTransactionTimeout() throws XAException {
            events.add("get-timeout");
            return timeoutSeconds;
        }

        @Override
        public boolean isSameRM(XAResource xaResource) throws XAException {
            if (!(xaResource instanceof RecordingXaResource)) {
                return false;
            }
            RecordingXaResource other = (RecordingXaResource) xaResource;
            return resourceManagerId.equals(other.resourceManagerId);
        }

        @Override
        public int prepare(Xid xid) throws XAException {
            events.add("prepare:" + xid.getFormatId());
            return XAResource.XA_OK;
        }

        @Override
        public Xid[] recover(int flag) throws XAException {
            events.add("recover:" + flag);
            return new Xid[] {recoverableXid};
        }

        @Override
        public void rollback(Xid xid) throws XAException {
            events.add("rollback:" + xid.getFormatId());
        }

        @Override
        public boolean setTransactionTimeout(int seconds) throws XAException {
            timeoutSeconds = seconds;
            events.add("timeout:" + seconds);
            return true;
        }

        @Override
        public void start(Xid xid, int flags) throws XAException {
            recoverableXid = xid;
            events.add("start:" + xid.getFormatId() + ":" + flags);
        }
    }

    private static final class NoOpXaResource implements XAResource {
        @Override
        public void commit(Xid xid, boolean onePhase) throws XAException {
        }

        @Override
        public void end(Xid xid, int flags) throws XAException {
        }

        @Override
        public void forget(Xid xid) throws XAException {
        }

        @Override
        public int getTransactionTimeout() throws XAException {
            return 0;
        }

        @Override
        public boolean isSameRM(XAResource xaResource) throws XAException {
            return this == xaResource;
        }

        @Override
        public int prepare(Xid xid) throws XAException {
            return XAResource.XA_OK;
        }

        @Override
        public Xid[] recover(int flag) throws XAException {
            return new Xid[0];
        }

        @Override
        public void rollback(Xid xid) throws XAException {
        }

        @Override
        public boolean setTransactionTimeout(int seconds) throws XAException {
            return true;
        }

        @Override
        public void start(Xid xid, int flags) throws XAException {
        }
    }

    private static final class ForeignTransaction implements Transaction {
        @Override
        public void commit() {
        }

        @Override
        public boolean delistResource(XAResource xaRes, int flag) {
            return false;
        }

        @Override
        public boolean enlistResource(XAResource xaRes) {
            return false;
        }

        @Override
        public int getStatus() {
            return Status.STATUS_UNKNOWN;
        }

        @Override
        public void registerSynchronization(Synchronization sync) {
        }

        @Override
        public void rollback() {
        }

        @Override
        public void setRollbackOnly() {
        }
    }
}
