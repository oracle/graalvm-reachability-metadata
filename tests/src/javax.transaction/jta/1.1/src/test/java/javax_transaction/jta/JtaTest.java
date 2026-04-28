/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_transaction.jta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

public class JtaTest {
    @Test
    void statusConstantsExposeTheTransactionLifecycleValues() {
        assertThat(Status.STATUS_ACTIVE).isEqualTo(0);
        assertThat(Status.STATUS_MARKED_ROLLBACK).isEqualTo(1);
        assertThat(Status.STATUS_PREPARED).isEqualTo(2);
        assertThat(Status.STATUS_COMMITTED).isEqualTo(3);
        assertThat(Status.STATUS_ROLLEDBACK).isEqualTo(4);
        assertThat(Status.STATUS_UNKNOWN).isEqualTo(5);
        assertThat(Status.STATUS_NO_TRANSACTION).isEqualTo(6);
        assertThat(Status.STATUS_PREPARING).isEqualTo(7);
        assertThat(Status.STATUS_COMMITTING).isEqualTo(8);
        assertThat(Status.STATUS_ROLLING_BACK).isEqualTo(9);
    }

    @Test
    void checkedExceptionConstructorsPreserveMessagesAndErrorCodes() {
        assertThat(new HeuristicCommitException("heuristic commit")).hasMessage("heuristic commit");
        assertThat(new HeuristicMixedException("heuristic mixed")).hasMessage("heuristic mixed");
        assertThat(new HeuristicRollbackException("heuristic rollback")).hasMessage("heuristic rollback");
        assertThat(new InvalidTransactionException("invalid transaction")).hasMessage("invalid transaction");
        assertThat(new NotSupportedException("not supported")).hasMessage("not supported");
        assertThat(new RollbackException("rolled back")).hasMessage("rolled back");
        assertThat(new TransactionRequiredException("transaction required")).hasMessage("transaction required");
        assertThat(new TransactionRolledbackException("transaction rolled back"))
                .hasMessage("transaction rolled back");

        SystemException systemException = new SystemException(53);
        assertThat(systemException.errorCode).isEqualTo(53);
        assertThat(new SystemException("system failed")).hasMessage("system failed");
        assertThat(new SystemException().errorCode).isZero();

        XAException xaException = new XAException(XAException.XAER_RMERR);
        assertThat(xaException.errorCode).isEqualTo(XAException.XAER_RMERR);
        assertThat(new XAException("xa failed")).hasMessage("xa failed");
        assertThat(new XAException().errorCode).isZero();
    }

    @Test
    void userTransactionCommitPreparesAndCommitsEnlistedXaResources() throws Exception {
        InMemoryTransactionManager manager = new InMemoryTransactionManager();
        UserTransaction userTransaction = manager;
        TransactionManager transactionManager = manager;
        TransactionSynchronizationRegistry registry = manager;

        userTransaction.setTransactionTimeout(45);
        userTransaction.begin();
        Transaction transaction = transactionManager.getTransaction();
        RecordingXaResource resource = new RecordingXaResource("primary");
        RecordingSynchronization synchronization = new RecordingSynchronization("regular");
        RecordingSynchronization interposedSynchronization = new RecordingSynchronization("interposed");

        assertThat(registry.getTransactionStatus()).isEqualTo(Status.STATUS_ACTIVE);
        assertThat(registry.getRollbackOnly()).isFalse();
        assertThat(registry.getTransactionKey()).isSameAs(transaction);
        registry.putResource("request-id", "txn-1");
        registry.registerInterposedSynchronization(interposedSynchronization);

        assertThat(transaction.enlistResource(resource)).isTrue();
        transaction.registerSynchronization(synchronization);
        assertThat(transaction.delistResource(resource, XAResource.TMSUCCESS)).isTrue();
        transactionManager.commit();

        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_COMMITTED);
        assertThat(transactionManager.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
        assertThat(resource.getTransactionTimeout()).isEqualTo(45);
        assertThat(resource.events()).containsExactly(
                "primary:start:4660:0",
                "primary:end:67108864",
                "primary:prepare:4660",
                "primary:commit:false");
        assertThat(synchronization.events()).containsExactly(
                "regular:beforeCompletion",
                "regular:afterCompletion:3");
        assertThat(interposedSynchronization.events()).containsExactly(
                "interposed:beforeCompletion",
                "interposed:afterCompletion:3");
        assertThat(registry.getResource("request-id")).isNull();
    }

    @Test
    void readOnlyXaResourceVotesAreNotCommittedInSecondPhase() throws Exception {
        InMemoryTransactionManager manager = new InMemoryTransactionManager();
        manager.begin();
        Transaction transaction = manager.getTransaction();
        ReadOnlyXaResource readOnlyResource = new ReadOnlyXaResource("read-only");
        RecordingXaResource updatingResource = new RecordingXaResource("updating");
        RecordingSynchronization synchronization = new RecordingSynchronization("sync");

        assertThat(transaction.enlistResource(readOnlyResource)).isTrue();
        assertThat(transaction.enlistResource(updatingResource)).isTrue();
        transaction.registerSynchronization(synchronization);
        assertThat(transaction.delistResource(readOnlyResource, XAResource.TMSUCCESS)).isTrue();
        assertThat(transaction.delistResource(updatingResource, XAResource.TMSUCCESS)).isTrue();

        manager.commit();

        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_COMMITTED);
        assertThat(readOnlyResource.events()).containsExactly(
                "read-only:start:4660:0",
                "read-only:end:67108864",
                "read-only:prepare:4660");
        assertThat(updatingResource.events()).containsExactly(
                "updating:start:4660:0",
                "updating:end:67108864",
                "updating:prepare:4660",
                "updating:commit:false");
        assertThat(synchronization.events()).containsExactly(
                "sync:beforeCompletion",
                "sync:afterCompletion:3");
    }

    @Test
    void rollbackOnlyTransactionRollsBackAndReportsRollbackException() throws Exception {
        InMemoryTransactionManager manager = new InMemoryTransactionManager();
        manager.begin();
        Transaction transaction = manager.getTransaction();
        RecordingXaResource resource = new RecordingXaResource("rollback");
        RecordingSynchronization synchronization = new RecordingSynchronization("sync");

        assertThat(transaction.enlistResource(resource)).isTrue();
        transaction.registerSynchronization(synchronization);
        manager.setRollbackOnly();

        assertThat(manager.getRollbackOnly()).isTrue();
        assertThat(manager.getStatus()).isEqualTo(Status.STATUS_MARKED_ROLLBACK);
        assertThatThrownBy(manager::commit)
                .isInstanceOf(RollbackException.class)
                .hasMessage("transaction was marked rollback-only");

        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_ROLLEDBACK);
        assertThat(manager.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
        assertThat(resource.events()).containsExactly(
                "rollback:start:4660:0",
                "rollback:end:536870912",
                "rollback:rollback:4660");
        assertThat(synchronization.events()).containsExactly("sync:afterCompletion:4");
    }

    @Test
    void transactionSetRollbackOnlyRejectsNewTransactionalWorkUntilRollback() throws Exception {
        InMemoryTransactionManager manager = new InMemoryTransactionManager();
        TransactionSynchronizationRegistry registry = manager;
        manager.begin();
        Transaction transaction = manager.getTransaction();

        transaction.setRollbackOnly();

        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_MARKED_ROLLBACK);
        assertThat(registry.getRollbackOnly()).isTrue();
        assertThatThrownBy(() -> transaction.enlistResource(new RecordingXaResource("late")))
                .isInstanceOf(RollbackException.class)
                .hasMessage("transaction was marked rollback-only");
        assertThatThrownBy(() -> transaction.registerSynchronization(new RecordingSynchronization("late")))
                .isInstanceOf(RollbackException.class)
                .hasMessage("transaction was marked rollback-only");

        transaction.rollback();

        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_ROLLEDBACK);
        assertThat(manager.getStatus()).isEqualTo(Status.STATUS_ROLLEDBACK);
    }

    @Test
    void xaPrepareFailureIsMappedToSystemExceptionWithTheXaErrorCode() throws Exception {
        InMemoryTransactionManager manager = new InMemoryTransactionManager();
        manager.begin();
        Transaction transaction = manager.getTransaction();
        PrepareFailingXaResource resource = new PrepareFailingXaResource(XAException.XA_RBTIMEOUT);

        assertThat(transaction.enlistResource(resource)).isTrue();

        assertThatThrownBy(manager::commit)
                .isInstanceOf(SystemException.class)
                .satisfies(exception -> {
                    SystemException systemException = (SystemException) exception;
                    assertThat(systemException.errorCode).isEqualTo(XAException.XA_RBTIMEOUT);
                    assertThat(systemException.getCause()).isInstanceOf(XAException.class);
                    assertThat(((XAException) systemException.getCause()).errorCode)
                            .isEqualTo(XAException.XA_RBTIMEOUT);
                });

        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_UNKNOWN);
        assertThat(manager.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
        assertThat(resource.events()).containsExactly(
                "start:4660:0",
                "prepare:4660");
    }

    @Test
    void transactionManagerSuspendsResumesAndRejectsInvalidUsage() throws Exception {
        InMemoryTransactionManager manager = new InMemoryTransactionManager();

        assertThatThrownBy(manager::commit).isInstanceOf(IllegalStateException.class);
        manager.begin();
        assertThatThrownBy(manager::begin)
                .isInstanceOf(NotSupportedException.class)
                .hasMessage("nested transactions are not supported");
        Transaction suspended = manager.suspend();

        assertThat(manager.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
        assertThat(manager.getTransaction()).isNull();
        assertThatThrownBy(() -> manager.resume(null))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("transaction must not be null");

        manager.resume(suspended);
        assertThat(manager.getTransaction()).isSameAs(suspended);
        manager.rollback();
        assertThat(suspended.getStatus()).isEqualTo(Status.STATUS_ROLLEDBACK);
    }

    @Test
    void xaResourceFlagsAndXidDataDriveTwoPhaseOperations() throws Exception {
        SimpleXid xid = new SimpleXid(0xCAFE, new byte[] {1, 2, 3 }, new byte[] {4, 5 });
        RecordingXaResource first = new RecordingXaResource("first");
        RecordingXaResource second = new RecordingXaResource("second");

        assertThat(Xid.MAXGTRIDSIZE).isEqualTo(64);
        assertThat(Xid.MAXBQUALSIZE).isEqualTo(64);
        assertThat(xid.getFormatId()).isEqualTo(0xCAFE);
        assertThat(xid.getGlobalTransactionId()).containsExactly(1, 2, 3);
        assertThat(xid.getBranchQualifier()).containsExactly(4, 5);

        first.start(xid, XAResource.TMNOFLAGS);
        first.end(xid, XAResource.TMSUSPEND);
        second.start(xid, XAResource.TMJOIN);
        second.end(xid, XAResource.TMFAIL);
        assertThat(first.prepare(xid)).isEqualTo(XAResource.XA_OK);
        assertThat(second.prepare(xid)).isEqualTo(XAResource.XA_OK);
        first.commit(xid, false);
        second.rollback(xid);
        second.forget(xid);

        assertThat(first.isSameRM(first)).isTrue();
        assertThat(first.isSameRM(second)).isFalse();
        assertThat(first.setTransactionTimeout(30)).isTrue();
        assertThat(first.getTransactionTimeout()).isEqualTo(30);
        assertThat(first.recover(XAResource.TMSTARTRSCAN | XAResource.TMENDRSCAN)).isEmpty();
        assertThat(first.events()).containsExactly(
                "first:start:51966:0",
                "first:end:33554432",
                "first:prepare:51966",
                "first:commit:false");
        assertThat(second.events()).containsExactly(
                "second:start:51966:2097152",
                "second:end:536870912",
                "second:prepare:51966",
                "second:rollback:51966",
                "second:forget:51966");
    }

    @Test
    void transactionSynchronizationRegistryResourcesAreScopedToTransactionKeys() throws Exception {
        InMemoryTransactionManager manager = new InMemoryTransactionManager();
        TransactionSynchronizationRegistry registry = manager;

        manager.begin();
        Object firstKey = registry.getTransactionKey();
        registry.putResource("shared", "first");
        Transaction firstTransaction = manager.suspend();

        manager.begin();
        Object secondKey = registry.getTransactionKey();
        assertThat(secondKey).isNotSameAs(firstKey);
        assertThat(registry.getResource("shared")).isNull();

        registry.putResource("shared", "second");
        assertThat(registry.getResource("shared")).isEqualTo("second");
        manager.commit();

        manager.resume(firstTransaction);
        assertThat(registry.getTransactionKey()).isSameAs(firstKey);
        assertThat(registry.getResource("shared")).isEqualTo("first");

        manager.rollback();
        assertThat(registry.getResource("shared")).isNull();
    }

    private static final class InMemoryTransactionManager
            implements TransactionManager, UserTransaction, TransactionSynchronizationRegistry {
        private TransactionContext currentTransaction;
        private int timeoutSeconds;

        @Override
        public void begin() throws NotSupportedException {
            if (currentTransaction != null) {
                throw new NotSupportedException("nested transactions are not supported");
            }
            currentTransaction = new TransactionContext(timeoutSeconds);
        }

        @Override
        public void commit() throws HeuristicMixedException, HeuristicRollbackException, RollbackException,
                SystemException {
            TransactionContext transaction = requireTransaction();
            try {
                transaction.commit();
            } finally {
                currentTransaction = null;
            }
        }

        @Override
        public int getStatus() {
            if (currentTransaction == null) {
                return Status.STATUS_NO_TRANSACTION;
            }
            return currentTransaction.currentStatus();
        }

        @Override
        public Transaction getTransaction() {
            return currentTransaction;
        }

        @Override
        public void resume(Transaction transaction) throws InvalidTransactionException {
            if (transaction == null) {
                throw new InvalidTransactionException("transaction must not be null");
            }
            if (currentTransaction != null) {
                throw new IllegalStateException("another transaction is already associated");
            }
            currentTransaction = (TransactionContext) transaction;
        }

        @Override
        public void rollback() throws SystemException {
            TransactionContext transaction = requireTransaction();
            try {
                transaction.rollback();
            } finally {
                currentTransaction = null;
            }
        }

        @Override
        public void setRollbackOnly() {
            requireTransaction().setRollbackOnly();
        }

        @Override
        public void setTransactionTimeout(int seconds) {
            timeoutSeconds = seconds;
        }

        @Override
        public Transaction suspend() {
            TransactionContext suspended = currentTransaction;
            currentTransaction = null;
            return suspended;
        }

        @Override
        public Object getResource(Object key) {
            TransactionContext transaction = currentTransaction;
            if (transaction == null) {
                return null;
            }
            return transaction.resourcesByKey.get(key);
        }

        @Override
        public boolean getRollbackOnly() {
            TransactionContext transaction = requireActiveRegistryTransaction();
            return transaction.currentStatus() == Status.STATUS_MARKED_ROLLBACK;
        }

        @Override
        public Object getTransactionKey() {
            return currentTransaction;
        }

        @Override
        public int getTransactionStatus() {
            return getStatus();
        }

        @Override
        public void putResource(Object key, Object value) {
            requireActiveRegistryTransaction().resourcesByKey.put(key, value);
        }

        @Override
        public void registerInterposedSynchronization(Synchronization synchronization) {
            requireActiveRegistryTransaction().interposedSynchronizations.add(synchronization);
        }

        private TransactionContext requireTransaction() {
            if (currentTransaction == null) {
                throw new IllegalStateException("no transaction is associated with the current thread");
            }
            return currentTransaction;
        }

        private TransactionContext requireActiveRegistryTransaction() {
            TransactionContext transaction = requireTransaction();
            if (transaction.currentStatus() == Status.STATUS_MARKED_ROLLBACK) {
                return transaction;
            }
            if (transaction.currentStatus() != Status.STATUS_ACTIVE) {
                throw new IllegalStateException("transaction is not active");
            }
            return transaction;
        }
    }

    private static final class TransactionContext implements Transaction {
        private final SimpleXid xid = new SimpleXid(0x1234, new byte[] {10, 20 }, new byte[] {30 });
        private final List<XAResource> xaResources = new ArrayList<>();
        private final List<Synchronization> synchronizations = new ArrayList<>();
        private final List<Synchronization> interposedSynchronizations = new ArrayList<>();
        private final Map<Object, Object> resourcesByKey = new HashMap<>();
        private final int timeoutSeconds;
        private int status = Status.STATUS_ACTIVE;

        TransactionContext(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        @Override
        public void commit() throws HeuristicMixedException, HeuristicRollbackException, RollbackException,
                SystemException {
            if (status == Status.STATUS_MARKED_ROLLBACK) {
                rollback();
                throw new RollbackException("transaction was marked rollback-only");
            }
            ensureActive();
            try {
                beforeCompletion();
                status = Status.STATUS_PREPARING;
                List<XAResource> readOnlyResources = new ArrayList<>();
                for (XAResource resource : xaResources) {
                    if (resource.prepare(xid) == XAResource.XA_RDONLY) {
                        readOnlyResources.add(resource);
                    }
                }
                status = Status.STATUS_COMMITTING;
                for (XAResource resource : xaResources) {
                    if (!readOnlyResources.contains(resource)) {
                        resource.commit(xid, false);
                    }
                }
                status = Status.STATUS_COMMITTED;
                afterCompletion(Status.STATUS_COMMITTED);
                resourcesByKey.clear();
            } catch (XAException exception) {
                status = Status.STATUS_UNKNOWN;
                SystemException systemException = new SystemException(exception.errorCode);
                systemException.initCause(exception);
                throw systemException;
            }
        }

        @Override
        public boolean delistResource(XAResource xaResource, int flag) throws SystemException {
            ensureActive();
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
            ensureActive();
            if (status == Status.STATUS_MARKED_ROLLBACK) {
                throw new RollbackException("transaction was marked rollback-only");
            }
            try {
                if (timeoutSeconds > 0) {
                    xaResource.setTransactionTimeout(timeoutSeconds);
                }
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
            ensureActive();
            if (status == Status.STATUS_MARKED_ROLLBACK) {
                throw new RollbackException("transaction was marked rollback-only");
            }
            synchronizations.add(synchronization);
        }

        @Override
        public void rollback() throws SystemException {
            if (status == Status.STATUS_ROLLEDBACK) {
                return;
            }
            try {
                status = Status.STATUS_ROLLING_BACK;
                for (XAResource resource : xaResources) {
                    resource.end(xid, XAResource.TMFAIL);
                    resource.rollback(xid);
                }
                status = Status.STATUS_ROLLEDBACK;
                afterCompletion(Status.STATUS_ROLLEDBACK);
                resourcesByKey.clear();
            } catch (XAException exception) {
                status = Status.STATUS_UNKNOWN;
                SystemException systemException = new SystemException(exception.errorCode);
                systemException.initCause(exception);
                throw systemException;
            }
        }

        @Override
        public void setRollbackOnly() {
            ensureActive();
            status = Status.STATUS_MARKED_ROLLBACK;
        }

        int currentStatus() {
            return status;
        }

        private void ensureActive() {
            if (status != Status.STATUS_ACTIVE && status != Status.STATUS_MARKED_ROLLBACK) {
                throw new IllegalStateException("transaction is not active");
            }
        }

        private void beforeCompletion() {
            for (Synchronization synchronization : synchronizations) {
                synchronization.beforeCompletion();
            }
            for (Synchronization synchronization : interposedSynchronizations) {
                synchronization.beforeCompletion();
            }
        }

        private void afterCompletion(int completionStatus) {
            for (Synchronization synchronization : synchronizations) {
                synchronization.afterCompletion(completionStatus);
            }
            for (Synchronization synchronization : interposedSynchronizations) {
                synchronization.afterCompletion(completionStatus);
            }
        }
    }

    private static final class RecordingSynchronization implements Synchronization {
        private final String name;
        private final List<String> events = new ArrayList<>();

        RecordingSynchronization(String name) {
            this.name = name;
        }

        @Override
        public void beforeCompletion() {
            events.add(name + ":beforeCompletion");
        }

        @Override
        public void afterCompletion(int status) {
            events.add(name + ":afterCompletion:" + status);
        }

        List<String> events() {
            return events;
        }
    }

    private static final class RecordingXaResource implements XAResource {
        private final String name;
        private final List<String> events = new ArrayList<>();
        private int timeoutSeconds;

        RecordingXaResource(String name) {
            this.name = name;
        }

        @Override
        public void commit(Xid xid, boolean onePhase) {
            events.add(name + ":commit:" + onePhase);
        }

        @Override
        public void end(Xid xid, int flags) {
            events.add(name + ":end:" + flags);
        }

        @Override
        public void forget(Xid xid) {
            events.add(name + ":forget:" + xid.getFormatId());
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
            events.add(name + ":prepare:" + xid.getFormatId());
            return XA_OK;
        }

        @Override
        public Xid[] recover(int flag) {
            return new Xid[0];
        }

        @Override
        public void rollback(Xid xid) {
            events.add(name + ":rollback:" + xid.getFormatId());
        }

        @Override
        public boolean setTransactionTimeout(int seconds) {
            timeoutSeconds = seconds;
            return true;
        }

        @Override
        public void start(Xid xid, int flags) {
            events.add(name + ":start:" + xid.getFormatId() + ":" + flags);
        }

        List<String> events() {
            return events;
        }
    }

    private static final class ReadOnlyXaResource implements XAResource {
        private final String name;
        private final List<String> events = new ArrayList<>();

        ReadOnlyXaResource(String name) {
            this.name = name;
        }

        @Override
        public void commit(Xid xid, boolean onePhase) {
            events.add(name + ":commit:" + onePhase);
        }

        @Override
        public void end(Xid xid, int flags) {
            events.add(name + ":end:" + flags);
        }

        @Override
        public void forget(Xid xid) {
            events.add(name + ":forget:" + xid.getFormatId());
        }

        @Override
        public int getTransactionTimeout() {
            return 0;
        }

        @Override
        public boolean isSameRM(XAResource xaResource) {
            return this == xaResource;
        }

        @Override
        public int prepare(Xid xid) {
            events.add(name + ":prepare:" + xid.getFormatId());
            return XA_RDONLY;
        }

        @Override
        public Xid[] recover(int flag) {
            return new Xid[0];
        }

        @Override
        public void rollback(Xid xid) {
            events.add(name + ":rollback:" + xid.getFormatId());
        }

        @Override
        public boolean setTransactionTimeout(int seconds) {
            return true;
        }

        @Override
        public void start(Xid xid, int flags) {
            events.add(name + ":start:" + xid.getFormatId() + ":" + flags);
        }

        List<String> events() {
            return events;
        }
    }

    private static final class PrepareFailingXaResource implements XAResource {
        private final int errorCode;
        private final List<String> events = new ArrayList<>();

        PrepareFailingXaResource(int errorCode) {
            this.errorCode = errorCode;
        }

        @Override
        public void commit(Xid xid, boolean onePhase) {
            events.add("commit:" + onePhase);
        }

        @Override
        public void end(Xid xid, int flags) {
            events.add("end:" + flags);
        }

        @Override
        public void forget(Xid xid) {
            events.add("forget:" + xid.getFormatId());
        }

        @Override
        public int getTransactionTimeout() {
            return 0;
        }

        @Override
        public boolean isSameRM(XAResource xaResource) {
            return this == xaResource;
        }

        @Override
        public int prepare(Xid xid) throws XAException {
            events.add("prepare:" + xid.getFormatId());
            throw new XAException(errorCode);
        }

        @Override
        public Xid[] recover(int flag) {
            return new Xid[0];
        }

        @Override
        public void rollback(Xid xid) {
            events.add("rollback:" + xid.getFormatId());
        }

        @Override
        public boolean setTransactionTimeout(int seconds) {
            return true;
        }

        @Override
        public void start(Xid xid, int flags) {
            events.add("start:" + xid.getFormatId() + ":" + flags);
        }

        List<String> events() {
            return events;
        }
    }

    private static final class SimpleXid implements Xid {
        private final int formatId;
        private final byte[] globalTransactionId;
        private final byte[] branchQualifier;

        SimpleXid(int formatId, byte[] globalTransactionId, byte[] branchQualifier) {
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
