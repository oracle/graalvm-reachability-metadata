/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_transaction.transaction_api;

import org.junit.jupiter.api.Test;

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
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Transaction_apiTest {
    @Test
    void statusConstantsRepresentAllTransactionStates() {
        Map<Integer, String> statusNames = new LinkedHashMap<>();
        statusNames.put(Status.STATUS_ACTIVE, "ACTIVE");
        statusNames.put(Status.STATUS_MARKED_ROLLBACK, "MARKED_ROLLBACK");
        statusNames.put(Status.STATUS_PREPARED, "PREPARED");
        statusNames.put(Status.STATUS_COMMITTED, "COMMITTED");
        statusNames.put(Status.STATUS_ROLLEDBACK, "ROLLEDBACK");
        statusNames.put(Status.STATUS_UNKNOWN, "UNKNOWN");
        statusNames.put(Status.STATUS_NO_TRANSACTION, "NO_TRANSACTION");
        statusNames.put(Status.STATUS_PREPARING, "PREPARING");
        statusNames.put(Status.STATUS_COMMITTING, "COMMITTING");
        statusNames.put(Status.STATUS_ROLLING_BACK, "ROLLING_BACK");

        assertThat(statusNames.keySet()).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertThat(statusNames.values()).containsExactly(
                "ACTIVE",
                "MARKED_ROLLBACK",
                "PREPARED",
                "COMMITTED",
                "ROLLEDBACK",
                "UNKNOWN",
                "NO_TRANSACTION",
                "PREPARING",
                "COMMITTING",
                "ROLLING_BACK");
    }

    @Test
    void exceptionConstructorsExposeMessagesAndErrorCodes() {
        assertExceptionConstructors(
                new HeuristicCommitException(),
                new HeuristicCommitException("heuristic commit"),
                "heuristic commit");
        assertExceptionConstructors(
                new HeuristicMixedException(),
                new HeuristicMixedException("heuristic mixed"),
                "heuristic mixed");
        assertExceptionConstructors(
                new HeuristicRollbackException(),
                new HeuristicRollbackException("heuristic rollback"),
                "heuristic rollback");
        assertExceptionConstructors(
                new NotSupportedException(),
                new NotSupportedException("not supported"),
                "not supported");
        assertExceptionConstructors(new RollbackException(), new RollbackException("rollback"), "rollback");

        assertRemoteExceptionConstructors(
                new InvalidTransactionException(),
                new InvalidTransactionException("invalid transaction"),
                "invalid transaction");
        assertRemoteExceptionConstructors(
                new TransactionRequiredException(),
                new TransactionRequiredException("transaction required"),
                "transaction required");
        assertRemoteExceptionConstructors(
                new TransactionRolledbackException(),
                new TransactionRolledbackException("transaction rolled back"),
                "transaction rolled back");

        SystemException defaultSystemException = new SystemException();
        SystemException messageSystemException = new SystemException("system failure");
        SystemException codedSystemException = new SystemException(55);
        assertThat(defaultSystemException.getMessage()).isNull();
        assertThat(defaultSystemException.errorCode).isZero();
        assertThat(messageSystemException).hasMessage("system failure");
        assertThat(messageSystemException.errorCode).isZero();
        assertThat(codedSystemException.getMessage()).isNull();
        assertThat(codedSystemException.errorCode).isEqualTo(55);

        XAException defaultXaException = new XAException();
        XAException messageXaException = new XAException("xa failure");
        XAException codedXaException = new XAException(XAException.XAER_RMERR);
        assertThat(defaultXaException.getMessage()).isNull();
        assertThat(defaultXaException.errorCode).isZero();
        assertThat(messageXaException).hasMessage("xa failure");
        assertThat(messageXaException.errorCode).isZero();
        assertThat(codedXaException.getMessage()).isNull();
        assertThat(codedXaException.errorCode).isEqualTo(XAException.XAER_RMERR);
    }

    @Test
    void transactionManagerAndUserTransactionDriveCommitLifecycle() throws Exception {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        UserTransaction userTransaction = transactionManager;

        assertThat(userTransaction.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);

        userTransaction.setTransactionTimeout(30);
        userTransaction.begin();
        assertThat(userTransaction.getStatus()).isEqualTo(Status.STATUS_ACTIVE);

        Transaction transaction = transactionManager.getTransaction();
        RecordingSynchronization synchronization = new RecordingSynchronization();
        RecordingXAResource resource = new RecordingXAResource("resource-manager");

        transaction.registerSynchronization(synchronization);
        assertThat(transaction.enlistResource(resource)).isTrue();
        assertThat(transaction.delistResource(resource, XAResource.TMSUCCESS)).isTrue();

        userTransaction.commit();

        assertThat(userTransaction.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_COMMITTED);
        assertThat(synchronization.events).containsExactly("before", "after:" + Status.STATUS_COMMITTED);
        assertThat(resource.events).containsExactly(
                "timeout:30",
                "start:101:" + XAResource.TMNOFLAGS,
                "end:101:" + XAResource.TMSUCCESS,
                "prepare:101",
                "commit:101:true");
        assertThat(transactionManager.lifecycle).containsExactly("timeout:30", "begin", "commit");
    }

    @Test
    void readOnlyXaResourceVotesArePreparedButNotCommitted() throws Exception {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        transactionManager.begin();

        Transaction transaction = transactionManager.getTransaction();
        RecordingXAResource readWriteResource = new RecordingXAResource("read-write-resource");
        RecordingXAResource readOnlyResource = new RecordingXAResource("read-only-resource", XAResource.XA_RDONLY);

        assertThat(transaction.enlistResource(readWriteResource)).isTrue();
        assertThat(transaction.enlistResource(readOnlyResource)).isTrue();
        assertThat(transaction.delistResource(readWriteResource, XAResource.TMSUCCESS)).isTrue();
        assertThat(transaction.delistResource(readOnlyResource, XAResource.TMSUCCESS)).isTrue();

        transactionManager.commit();

        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_COMMITTED);
        assertThat(readWriteResource.events).containsExactly(
                "start:101:" + XAResource.TMNOFLAGS,
                "end:101:" + XAResource.TMSUCCESS,
                "prepare:101",
                "commit:101:false");
        assertThat(readOnlyResource.events).containsExactly(
                "start:101:" + XAResource.TMNOFLAGS,
                "end:101:" + XAResource.TMSUCCESS,
                "prepare:101");
    }

    @Test
    void rollbackOnlyTransactionRollsBackOnCommit() throws Exception {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        transactionManager.begin();

        Transaction transaction = transactionManager.getTransaction();
        RecordingSynchronization synchronization = new RecordingSynchronization();
        RecordingXAResource resource = new RecordingXAResource("rollback-resource");
        transaction.registerSynchronization(synchronization);
        transaction.enlistResource(resource);

        transactionManager.setRollbackOnly();
        assertThat(transactionManager.getStatus()).isEqualTo(Status.STATUS_MARKED_ROLLBACK);

        assertThatThrownBy(transactionManager::commit)
                .isInstanceOf(RollbackException.class)
                .hasMessage("Transaction marked for rollback");

        assertThat(transactionManager.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_ROLLEDBACK);
        assertThat(synchronization.events).containsExactly("after:" + Status.STATUS_ROLLEDBACK);
        assertThat(resource.events).containsExactly(
                "start:101:" + XAResource.TMNOFLAGS,
                "rollback:101");
        assertThat(transactionManager.lifecycle).containsExactly("begin", "setRollbackOnly", "commit");
    }

    @Test
    void transactionManagerSuspendsResumesAndRejectsInvalidTransactions() throws Exception {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        transactionManager.begin();
        Transaction suspended = transactionManager.suspend();

        assertThat(suspended).isNotNull();
        assertThat(transactionManager.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
        assertThat(transactionManager.getTransaction()).isNull();

        transactionManager.resume(suspended);
        assertThat(transactionManager.getTransaction()).isSameAs(suspended);
        assertThat(transactionManager.getStatus()).isEqualTo(Status.STATUS_ACTIVE);
        transactionManager.rollback();

        assertThat(transactionManager.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
        assertThat(suspended.getStatus()).isEqualTo(Status.STATUS_ROLLEDBACK);

        transactionManager.begin();
        assertThatThrownBy(() -> transactionManager.resume(suspended)).isInstanceOf(IllegalStateException.class);
        transactionManager.rollback();

        assertThatThrownBy(() -> transactionManager.resume(new ForeignTransaction()))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("Transaction was not created by this manager");
    }

    @Test
    void transactionSynchronizationRegistryStoresResourcesAndInterposedSynchronizations() {
        RecordingTransactionSynchronizationRegistry registry = new RecordingTransactionSynchronizationRegistry();
        RecordingSynchronization synchronization = new RecordingSynchronization();
        Object resourceKey = new Object();
        Object resourceValue = new Object();

        assertThat(registry.getTransactionKey()).isSameAs(registry.getTransactionKey());
        assertThat(registry.getTransactionStatus()).isEqualTo(Status.STATUS_ACTIVE);
        assertThat(registry.getRollbackOnly()).isFalse();

        registry.putResource(resourceKey, resourceValue);
        registry.registerInterposedSynchronization(synchronization);
        registry.setRollbackOnly();

        assertThat(registry.getResource(resourceKey)).isSameAs(resourceValue);
        assertThat(registry.getRollbackOnly()).isTrue();
        assertThat(registry.getTransactionStatus()).isEqualTo(Status.STATUS_MARKED_ROLLBACK);

        registry.complete();

        assertThat(registry.getTransactionStatus()).isEqualTo(Status.STATUS_ROLLEDBACK);
        assertThat(synchronization.events).containsExactly("after:" + Status.STATUS_ROLLEDBACK);
    }

    @Test
    void transactionSynchronizationRegistryRunsInterposedSynchronizationsForCommittedTransaction() {
        RecordingTransactionSynchronizationRegistry registry = new RecordingTransactionSynchronizationRegistry();
        RecordingSynchronization synchronization = new RecordingSynchronization();

        registry.registerInterposedSynchronization(synchronization);
        registry.complete();

        assertThat(registry.getTransactionStatus()).isEqualTo(Status.STATUS_COMMITTED);
        assertThat(registry.getRollbackOnly()).isFalse();
        assertThat(synchronization.events).containsExactly("before", "after:" + Status.STATUS_COMMITTED);
    }

    @Test
    void xaResourceAndXidApisExposeResourceManagerOperations() throws Exception {
        RecordingXAResource firstResource = new RecordingXAResource("orders");
        RecordingXAResource sameResourceManager = new RecordingXAResource("orders");
        RecordingXAResource differentResourceManager = new RecordingXAResource("inventory");
        RecordingXid xid = new RecordingXid(321, new byte[] {1, 2, 3}, new byte[] {4, 5});

        assertThat(xid.getFormatId()).isEqualTo(321);
        assertThat(xid.getGlobalTransactionId()).containsExactly(1, 2, 3);
        assertThat(xid.getBranchQualifier()).containsExactly(4, 5);
        assertThat(Xid.MAXGTRIDSIZE).isGreaterThanOrEqualTo(xid.getGlobalTransactionId().length);
        assertThat(Xid.MAXBQUALSIZE).isGreaterThanOrEqualTo(xid.getBranchQualifier().length);

        assertThat(firstResource.setTransactionTimeout(45)).isTrue();
        assertThat(firstResource.getTransactionTimeout()).isEqualTo(45);
        assertThat(firstResource.isSameRM(sameResourceManager)).isTrue();
        assertThat(firstResource.isSameRM(differentResourceManager)).isFalse();

        firstResource.start(xid, XAResource.TMNOFLAGS);
        firstResource.end(xid, XAResource.TMSUCCESS);
        assertThat(firstResource.prepare(xid)).isEqualTo(XAResource.XA_OK);
        assertThat(firstResource.recover(XAResource.TMSTARTRSCAN)).containsExactly(xid);
        firstResource.commit(xid, false);
        firstResource.rollback(xid);
        firstResource.forget(xid);

        assertThat(XAResource.TMNOFLAGS).isZero();
        assertThat(XAResource.XA_OK).isZero();
        assertThat(XAResource.XA_RDONLY).isEqualTo(XAException.XA_RDONLY);
        assertThat(XAException.XA_RBROLLBACK).isBetween(XAException.XA_RBBASE, XAException.XA_RBEND);
        assertThat(firstResource.events).containsExactly(
                "timeout:45",
                "start:321:" + XAResource.TMNOFLAGS,
                "end:321:" + XAResource.TMSUCCESS,
                "prepare:321",
                "recover:" + XAResource.TMSTARTRSCAN,
                "commit:321:false",
                "rollback:321",
                "forget:321");
    }

    private static void assertExceptionConstructors(
            Exception emptyException,
            Exception messageException,
            String message) {
        assertThat(emptyException.getMessage()).isNull();
        assertThat(messageException).hasMessage(message);
    }

    private static void assertRemoteExceptionConstructors(
            RemoteException emptyException,
            RemoteException messageException,
            String message) {
        assertThat(emptyException.getMessage()).isNull();
        assertThat(emptyException.detail).isNull();
        assertThat(messageException).hasMessage(message);
        assertThat(messageException.detail).isNull();
    }

    private static final class RecordingTransactionManager implements TransactionManager, UserTransaction {
        private final List<String> lifecycle = new ArrayList<>();
        private int timeoutSeconds;
        private RecordingTransaction currentTransaction;

        @Override
        public void begin() throws NotSupportedException {
            if (currentTransaction != null) {
                throw new NotSupportedException("Nested transactions are not supported");
            }
            currentTransaction = new RecordingTransaction(this, timeoutSeconds);
            lifecycle.add("begin");
        }

        @Override
        public void commit()
                throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SystemException {
            lifecycle.add("commit");
            requireCurrentTransaction().commit();
        }

        @Override
        public int getStatus() throws SystemException {
            if (currentTransaction == null) {
                return Status.STATUS_NO_TRANSACTION;
            }
            return currentTransaction.getStatus();
        }

        @Override
        public Transaction getTransaction() {
            return currentTransaction;
        }

        @Override
        public void resume(Transaction transaction) throws InvalidTransactionException {
            if (currentTransaction != null) {
                throw new IllegalStateException("A transaction is already associated with the thread");
            }
            if (!(transaction instanceof RecordingTransaction)) {
                throw new InvalidTransactionException("Transaction was not created by this manager");
            }
            RecordingTransaction recordingTransaction = (RecordingTransaction) transaction;
            if (recordingTransaction.manager != this || recordingTransaction.completed) {
                throw new InvalidTransactionException("Transaction is no longer valid");
            }
            currentTransaction = recordingTransaction;
            lifecycle.add("resume");
        }

        @Override
        public void rollback() throws SystemException {
            lifecycle.add("rollback");
            requireCurrentTransaction().rollback();
        }

        @Override
        public void setRollbackOnly() throws SystemException {
            lifecycle.add("setRollbackOnly");
            requireCurrentTransaction().setRollbackOnly();
        }

        @Override
        public void setTransactionTimeout(int seconds) {
            timeoutSeconds = seconds;
            lifecycle.add("timeout:" + seconds);
        }

        @Override
        public Transaction suspend() {
            Transaction suspended = currentTransaction;
            currentTransaction = null;
            lifecycle.add("suspend");
            return suspended;
        }

        private RecordingTransaction requireCurrentTransaction() {
            if (currentTransaction == null) {
                throw new IllegalStateException("No transaction is associated with the thread");
            }
            return currentTransaction;
        }

        private void finish(RecordingTransaction transaction) {
            if (currentTransaction == transaction) {
                currentTransaction = null;
            }
        }
    }

    private static final class RecordingTransaction implements Transaction {
        private final RecordingTransactionManager manager;
        private final int timeoutSeconds;
        private final RecordingXid xid = new RecordingXid(101, new byte[] {10, 11}, new byte[] {12});
        private final List<Synchronization> synchronizations = new ArrayList<>();
        private final List<XAResource> resources = new ArrayList<>();
        private int status = Status.STATUS_ACTIVE;
        private boolean completed;

        private RecordingTransaction(RecordingTransactionManager manager, int timeoutSeconds) {
            this.manager = manager;
            this.timeoutSeconds = timeoutSeconds;
        }

        @Override
        public void commit()
                throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SystemException {
            requireIncomplete();
            if (status == Status.STATUS_MARKED_ROLLBACK) {
                rollbackInternal();
                throw new RollbackException("Transaction marked for rollback");
            }
            try {
                for (Synchronization synchronization : synchronizations) {
                    synchronization.beforeCompletion();
                }
                status = Status.STATUS_PREPARING;
                List<XAResource> committableResources = new ArrayList<>();
                for (XAResource resource : resources) {
                    int vote = resource.prepare(xid);
                    if (vote == XAResource.XA_OK) {
                        committableResources.add(resource);
                    } else if (vote != XAResource.XA_RDONLY) {
                        throw new SystemException(vote);
                    }
                }
                status = Status.STATUS_COMMITTING;
                boolean onePhase = resources.size() == 1;
                for (XAResource resource : committableResources) {
                    resource.commit(xid, onePhase);
                }
                status = Status.STATUS_COMMITTED;
                completed = true;
                for (Synchronization synchronization : synchronizations) {
                    synchronization.afterCompletion(Status.STATUS_COMMITTED);
                }
            } catch (XAException exception) {
                status = Status.STATUS_UNKNOWN;
                throw systemException(exception);
            } finally {
                if (completed || status == Status.STATUS_UNKNOWN) {
                    manager.finish(this);
                }
            }
        }

        @Override
        public boolean delistResource(XAResource resource, int flag) throws SystemException {
            requireIncomplete();
            if (!resources.contains(resource)) {
                return false;
            }
            try {
                resource.end(xid, flag);
                return true;
            } catch (XAException exception) {
                throw systemException(exception);
            }
        }

        @Override
        public boolean enlistResource(XAResource resource) throws RollbackException, SystemException {
            requireIncomplete();
            if (status == Status.STATUS_MARKED_ROLLBACK) {
                throw new RollbackException("Transaction marked for rollback");
            }
            try {
                resources.add(resource);
                if (timeoutSeconds > 0) {
                    resource.setTransactionTimeout(timeoutSeconds);
                }
                resource.start(xid, XAResource.TMNOFLAGS);
                return true;
            } catch (XAException exception) {
                throw systemException(exception);
            }
        }

        @Override
        public int getStatus() {
            return status;
        }

        @Override
        public void registerSynchronization(Synchronization synchronization) throws RollbackException {
            requireIncomplete();
            if (status == Status.STATUS_MARKED_ROLLBACK) {
                throw new RollbackException("Transaction marked for rollback");
            }
            synchronizations.add(synchronization);
        }

        @Override
        public void rollback() throws SystemException {
            requireIncomplete();
            rollbackInternal();
        }

        @Override
        public void setRollbackOnly() {
            requireIncomplete();
            status = Status.STATUS_MARKED_ROLLBACK;
        }

        private void rollbackInternal() throws SystemException {
            status = Status.STATUS_ROLLING_BACK;
            try {
                for (XAResource resource : resources) {
                    resource.rollback(xid);
                }
                status = Status.STATUS_ROLLEDBACK;
                completed = true;
                for (Synchronization synchronization : synchronizations) {
                    synchronization.afterCompletion(Status.STATUS_ROLLEDBACK);
                }
            } catch (XAException exception) {
                status = Status.STATUS_UNKNOWN;
                throw systemException(exception);
            } finally {
                manager.finish(this);
            }
        }

        private void requireIncomplete() {
            if (completed) {
                throw new IllegalStateException("Transaction is already completed");
            }
        }

        private static SystemException systemException(XAException exception) {
            SystemException systemException = new SystemException(exception.errorCode);
            systemException.initCause(exception);
            return systemException;
        }
    }

    private static final class RecordingTransactionSynchronizationRegistry
            implements TransactionSynchronizationRegistry {
        private final Object transactionKey = new Object();
        private final Map<Object, Object> resources = new IdentityHashMap<>();
        private final List<Synchronization> synchronizations = new ArrayList<>();
        private int status = Status.STATUS_ACTIVE;
        private boolean rollbackOnly;

        @Override
        public Object getTransactionKey() {
            return transactionKey;
        }

        @Override
        public void putResource(Object key, Object value) {
            resources.put(key, value);
        }

        @Override
        public Object getResource(Object key) {
            return resources.get(key);
        }

        @Override
        public void registerInterposedSynchronization(Synchronization synchronization) {
            synchronizations.add(synchronization);
        }

        @Override
        public int getTransactionStatus() {
            return status;
        }

        @Override
        public void setRollbackOnly() {
            rollbackOnly = true;
            status = Status.STATUS_MARKED_ROLLBACK;
        }

        @Override
        public boolean getRollbackOnly() {
            return rollbackOnly;
        }

        private void complete() {
            if (rollbackOnly) {
                status = Status.STATUS_ROLLEDBACK;
            } else {
                for (Synchronization synchronization : synchronizations) {
                    synchronization.beforeCompletion();
                }
                status = Status.STATUS_COMMITTED;
            }
            for (Synchronization synchronization : synchronizations) {
                synchronization.afterCompletion(status);
            }
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

    private static final class RecordingXAResource implements XAResource {
        private final String resourceManagerName;
        private final int prepareResult;
        private final List<String> events = new ArrayList<>();
        private final List<Xid> knownXids = new ArrayList<>();
        private int timeoutSeconds;

        private RecordingXAResource(String resourceManagerName) {
            this(resourceManagerName, XA_OK);
        }

        private RecordingXAResource(String resourceManagerName, int prepareResult) {
            this.resourceManagerName = resourceManagerName;
            this.prepareResult = prepareResult;
        }

        @Override
        public void commit(Xid xid, boolean onePhase) {
            events.add("commit:" + xid.getFormatId() + ":" + onePhase);
        }

        @Override
        public void end(Xid xid, int flags) {
            events.add("end:" + xid.getFormatId() + ":" + flags);
        }

        @Override
        public void forget(Xid xid) {
            events.add("forget:" + xid.getFormatId());
            knownXids.remove(xid);
        }

        @Override
        public int getTransactionTimeout() {
            return timeoutSeconds;
        }

        @Override
        public boolean isSameRM(XAResource xaResource) {
            return xaResource instanceof RecordingXAResource
                    && resourceManagerName.equals(((RecordingXAResource) xaResource).resourceManagerName);
        }

        @Override
        public int prepare(Xid xid) {
            events.add("prepare:" + xid.getFormatId());
            return prepareResult;
        }

        @Override
        public Xid[] recover(int flag) {
            events.add("recover:" + flag);
            return knownXids.toArray(new Xid[0]);
        }

        @Override
        public void rollback(Xid xid) {
            events.add("rollback:" + xid.getFormatId());
        }

        @Override
        public boolean setTransactionTimeout(int seconds) {
            timeoutSeconds = seconds;
            events.add("timeout:" + seconds);
            return true;
        }

        @Override
        public void start(Xid xid, int flags) {
            events.add("start:" + xid.getFormatId() + ":" + flags);
            knownXids.add(xid);
        }
    }

    private static final class RecordingXid implements Xid {
        private final int formatId;
        private final byte[] globalTransactionId;
        private final byte[] branchQualifier;

        private RecordingXid(int formatId, byte[] globalTransactionId, byte[] branchQualifier) {
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

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Xid)) {
                return false;
            }
            Xid otherXid = (Xid) other;
            return formatId == otherXid.getFormatId()
                    && Arrays.equals(globalTransactionId, otherXid.getGlobalTransactionId())
                    && Arrays.equals(branchQualifier, otherXid.getBranchQualifier());
        }

        @Override
        public int hashCode() {
            int result = formatId;
            result = 31 * result + Arrays.hashCode(globalTransactionId);
            result = 31 * result + Arrays.hashCode(branchQualifier);
            return result;
        }
    }

    private static final class ForeignTransaction implements Transaction {
        @Override
        public void commit() {
            throw new UnsupportedOperationException("Foreign transaction marker");
        }

        @Override
        public boolean delistResource(XAResource xaResource, int flag) {
            throw new UnsupportedOperationException("Foreign transaction marker");
        }

        @Override
        public boolean enlistResource(XAResource xaResource) {
            throw new UnsupportedOperationException("Foreign transaction marker");
        }

        @Override
        public int getStatus() {
            return Status.STATUS_UNKNOWN;
        }

        @Override
        public void registerSynchronization(Synchronization synchronization) {
            throw new UnsupportedOperationException("Foreign transaction marker");
        }

        @Override
        public void rollback() {
            throw new UnsupportedOperationException("Foreign transaction marker");
        }

        @Override
        public void setRollbackOnly() {
            throw new UnsupportedOperationException("Foreign transaction marker");
        }
    }
}
