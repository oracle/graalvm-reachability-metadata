/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_spec_javax_transaction.jboss_transaction_api_1_2_spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
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
import javax.transaction.TransactionScoped;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;
import javax.transaction.TransactionalException;
import javax.transaction.UserTransaction;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import org.junit.jupiter.api.Test;

public class Jboss_transaction_api_1_2_specTest {
    @Test
    void statusConstantsExposeJtaStateMachineValues() {
        assertThat(Status.STATUS_ACTIVE).isZero();
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
    void checkedExceptionConstructorsPreserveMessages() {
        assertThat(new HeuristicCommitException("heuristic commit")).hasMessage("heuristic commit");
        assertThat(new HeuristicMixedException("heuristic mixed")).hasMessage("heuristic mixed");
        assertThat(new HeuristicRollbackException("heuristic rollback")).hasMessage("heuristic rollback");
        assertThat(new InvalidTransactionException("invalid")).hasMessage("invalid");
        assertThat(new NotSupportedException("nested transactions are unsupported"))
                .hasMessage("nested transactions are unsupported");
        assertThat(new RollbackException("rollback only")).hasMessage("rollback only");
        assertThat(new TransactionRequiredException("transaction required")).hasMessage("transaction required");
        assertThat(new TransactionRolledbackException("rolled back")).hasMessage("rolled back");

        SystemException systemException = new SystemException(1234);
        assertThat(systemException.errorCode).isEqualTo(1234);
        assertThat(new SystemException("resource manager unavailable")).hasMessage("resource manager unavailable");
    }

    @Test
    void transactionalExceptionKeepsCauseForInterceptors() {
        IllegalArgumentException cause = new IllegalArgumentException("business failure");

        TransactionalException exception = new TransactionalException("transaction interceptor failed", cause);

        assertThat(exception)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("transaction interceptor failed")
                .hasCause(cause);
    }

    @Test
    void transactionalTxTypeEnumExposesAllPropagationPolicies() {
        assertThat(TxType.values())
                .containsExactly(
                        TxType.REQUIRED,
                        TxType.REQUIRES_NEW,
                        TxType.MANDATORY,
                        TxType.SUPPORTS,
                        TxType.NOT_SUPPORTED,
                        TxType.NEVER);
        assertThat(TxType.valueOf("REQUIRES_NEW")).isSameAs(TxType.REQUIRES_NEW);
    }

    @Test
    void transactionalAndTransactionScopedAnnotationsCanDecorateApplicationTypes() {
        AnnotatedTransactionalBean bean = new AnnotatedTransactionalBean();

        assertThat(bean.defaultTransactionalWork()).isEqualTo("required");
        assertThat(bean.requiresNewWork()).isEqualTo("requires-new");
        assertThat(bean.transactionScopedState).isEqualTo("scoped");
    }

    @Test
    void transactionalRollbackRulesHonorExplicitRollbackAndDontRollbackPolicies() {
        Transactional policy = new ConfiguredTransactional(
                TxType.REQUIRED,
                new Class<?>[] {CheckedBusinessException.class, RuntimeException.class},
                new Class<?>[] {IgnoredBusinessException.class, IllegalArgumentException.class});
        Transactional defaultPolicy = new ConfiguredTransactional(TxType.SUPPORTS, new Class<?>[0], new Class<?>[0]);

        assertThat(shouldRollback(policy, new CheckedBusinessException())).isTrue();
        assertThat(shouldRollback(policy, new RuntimeException("runtime failure"))).isTrue();
        assertThat(shouldRollback(policy, new IgnoredBusinessException())).isFalse();
        assertThat(shouldRollback(policy, new IllegalArgumentException("ignored runtime failure"))).isFalse();
        assertThat(shouldRollback(defaultPolicy, new CheckedBusinessException())).isFalse();
        assertThat(shouldRollback(defaultPolicy, new IllegalStateException("default runtime failure"))).isTrue();
    }

    @Test
    void transactionManagerCommitsEnlistedXaResourcesAndRegisteredSynchronizations() throws Exception {
        RecordingTransactionManager manager = new RecordingTransactionManager();
        RecordingXAResource resource = new RecordingXAResource();
        RecordingSynchronization synchronization = new RecordingSynchronization();

        manager.begin();
        Transaction transaction = manager.getTransaction();
        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_ACTIVE);

        assertThat(transaction.enlistResource(resource)).isTrue();
        transaction.registerSynchronization(synchronization);
        assertThat(transaction.delistResource(resource, XAResource.TMSUCCESS)).isTrue();

        manager.commit();

        assertThat(manager.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_COMMITTED);
        assertThat(resource.events).containsExactly("start:0", "end:" + XAResource.TMSUCCESS, "commit:true");
        assertThat(synchronization.events).containsExactly("before", "after:" + Status.STATUS_COMMITTED);
    }

    @Test
    void xaResourceRecoveryScanReturnsPreparedBranchesAndHonorsTimeoutConfiguration() throws Exception {
        Xid inDoubtXid = new FixedXid(19);
        RecoverableXAResource resource = new RecoverableXAResource(inDoubtXid);

        assertThat(resource.setTransactionTimeout(45)).isTrue();
        assertThat(resource.getTransactionTimeout()).isEqualTo(45);
        assertThat(resource.prepare(inDoubtXid)).isEqualTo(XAResource.XA_OK);
        assertThat(resource.recover(XAResource.TMSTARTRSCAN)).containsExactly(inDoubtXid);
        assertThat(resource.isSameRM(resource)).isTrue();
        assertThat(resource.isSameRM(new RecoverableXAResource(new FixedXid(20)))).isFalse();

        resource.forget(inDoubtXid);

        assertThat(resource.recover(XAResource.TMENDRSCAN)).isEmpty();
        assertThat(resource.events)
                .containsExactly(
                        "timeout:45",
                        "prepare:19",
                        "recover:" + XAResource.TMSTARTRSCAN,
                        "forget:19",
                        "recover:" + XAResource.TMENDRSCAN);
    }

    @Test
    void commitOnRollbackOnlyTransactionRollsBackAndThrowsRollbackException() throws Exception {
        RecordingTransactionManager manager = new RecordingTransactionManager();
        RecordingXAResource resource = new RecordingXAResource();
        RecordingSynchronization synchronization = new RecordingSynchronization();

        manager.begin();
        Transaction transaction = manager.getTransaction();
        transaction.enlistResource(resource);
        transaction.registerSynchronization(synchronization);
        manager.setRollbackOnly();

        assertThatThrownBy(manager::commit)
                .isInstanceOf(RollbackException.class)
                .hasMessage("Transaction was marked rollback-only");

        assertThat(manager.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_ROLLEDBACK);
        assertThat(resource.events).containsExactly("start:0", "rollback");
        assertThat(synchronization.events).containsExactly("after:" + Status.STATUS_ROLLEDBACK);
    }

    @Test
    void transactionManagerSuspendsAndResumesTransactionIdentity() throws Exception {
        RecordingTransactionManager manager = new RecordingTransactionManager();

        manager.begin();
        Transaction suspended = manager.suspend();

        assertThat(suspended).isNotNull();
        assertThat(manager.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
        assertThat(manager.getTransaction()).isNull();

        manager.resume(suspended);

        assertThat(manager.getTransaction()).isSameAs(suspended);
        assertThat(manager.getStatus()).isEqualTo(Status.STATUS_ACTIVE);
    }

    @Test
    void transactionSynchronizationRegistryManagesResourcesAndInterposedSynchronizations() throws Exception {
        RecordingTransactionManager manager = new RecordingTransactionManager();
        RecordingTransactionSynchronizationRegistry registry = new RecordingTransactionSynchronizationRegistry(manager);
        RecordingSynchronization synchronization = new RecordingSynchronization();

        manager.begin();
        Object key = registry.getTransactionKey();

        registry.putResource("connection", "primary");
        registry.registerInterposedSynchronization(synchronization);

        assertThat(key).isSameAs(manager.getTransaction());
        assertThat(registry.getTransactionStatus()).isEqualTo(Status.STATUS_ACTIVE);
        assertThat(registry.getRollbackOnly()).isFalse();
        assertThat(registry.getResource("connection")).isEqualTo("primary");

        registry.setRollbackOnly();

        assertThat(registry.getRollbackOnly()).isTrue();
        assertThat(manager.getStatus()).isEqualTo(Status.STATUS_MARKED_ROLLBACK);
        assertThatThrownBy(manager::commit).isInstanceOf(RollbackException.class);
        assertThat(synchronization.events).containsExactly("after:" + Status.STATUS_ROLLEDBACK);
    }

    @Test
    void transactionSynchronizationRegistryRejectsResourceAccessWithoutTransaction() {
        RecordingTransactionManager manager = new RecordingTransactionManager();
        RecordingTransactionSynchronizationRegistry registry = new RecordingTransactionSynchronizationRegistry(manager);

        assertThat(registry.getTransactionStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
        assertThatThrownBy(registry::getRollbackOnly)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No active transaction");
        assertThatThrownBy(() -> registry.putResource("key", "value"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No active transaction");
    }

    @Test
    void userTransactionDelegatesLifecycleAndTimeoutOperations() throws Exception {
        RecordingTransactionManager manager = new RecordingTransactionManager();
        RecordingUserTransaction userTransaction = new RecordingUserTransaction(manager);

        userTransaction.setTransactionTimeout(30);
        userTransaction.begin();

        assertThat(userTransaction.getStatus()).isEqualTo(Status.STATUS_ACTIVE);
        assertThat(manager.timeoutSeconds).isEqualTo(30);

        userTransaction.rollback();

        assertThat(userTransaction.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
    }

    private static boolean shouldRollback(Transactional transactional, Throwable throwable) {
        if (matchesExceptionType(transactional.dontRollbackOn(), throwable)) {
            return false;
        }
        if (matchesExceptionType(transactional.rollbackOn(), throwable)) {
            return true;
        }
        return throwable instanceof RuntimeException;
    }

    private static boolean matchesExceptionType(Class<?>[] exceptionTypes, Throwable throwable) {
        for (Class<?> exceptionType : exceptionTypes) {
            if (exceptionType.isAssignableFrom(throwable.getClass())) {
                return true;
            }
        }
        return false;
    }

    @Transactional
    private static final class AnnotatedTransactionalBean {
        @TransactionScoped
        private final String transactionScopedState = "scoped";

        private String defaultTransactionalWork() {
            return "required";
        }

        @Transactional(
                value = TxType.REQUIRES_NEW,
                rollbackOn = IllegalArgumentException.class,
                dontRollbackOn = IllegalStateException.class)
        private String requiresNewWork() {
            return "requires-new";
        }
    }

    private static final class ConfiguredTransactional implements Transactional {
        private final TxType value;
        private final Class<?>[] rollbackOn;
        private final Class<?>[] dontRollbackOn;

        private ConfiguredTransactional(TxType value, Class<?>[] rollbackOn, Class<?>[] dontRollbackOn) {
            this.value = value;
            this.rollbackOn = rollbackOn;
            this.dontRollbackOn = dontRollbackOn;
        }

        @Override
        public TxType value() {
            return value;
        }

        @Override
        public Class<?>[] rollbackOn() {
            return rollbackOn;
        }

        @Override
        public Class<?>[] dontRollbackOn() {
            return dontRollbackOn;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Transactional.class;
        }
    }

    private static class CheckedBusinessException extends Exception {
    }

    private static final class IgnoredBusinessException extends CheckedBusinessException {
    }

    private static final class RecordingTransactionManager implements TransactionManager {
        private RecordingTransaction current;
        private int timeoutSeconds;

        @Override
        public void begin() throws NotSupportedException {
            if (current != null) {
                throw new NotSupportedException("Nested transactions are not supported");
            }
            current = new RecordingTransaction();
        }

        @Override
        public void commit()
                throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SystemException {
            try {
                requireCurrent().commit();
            } finally {
                current = null;
            }
        }

        @Override
        public void rollback() throws SystemException {
            requireCurrent().rollback();
            current = null;
        }

        @Override
        public void setRollbackOnly() throws SystemException {
            requireCurrent().setRollbackOnly();
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
        public void setTransactionTimeout(int seconds) {
            timeoutSeconds = seconds;
        }

        @Override
        public Transaction suspend() {
            Transaction suspended = current;
            current = null;
            return suspended;
        }

        @Override
        public void resume(Transaction transaction) throws InvalidTransactionException {
            if (current != null) {
                throw new IllegalStateException("A transaction is already associated with this thread");
            }
            if (transaction != null && !(transaction instanceof RecordingTransaction)) {
                throw new InvalidTransactionException("Unknown transaction implementation");
            }
            current = (RecordingTransaction) transaction;
        }

        private RecordingTransaction requireCurrent() {
            if (current == null) {
                throw new IllegalStateException("No active transaction");
            }
            return current;
        }
    }

    private static final class RecordingTransaction implements Transaction {
        private final List<XAResource> resources = new ArrayList<>();
        private final List<Synchronization> synchronizations = new ArrayList<>();
        private final Map<Object, Object> registryResources = new HashMap<>();
        private final Xid xid = new FixedXid(7);
        private int status = Status.STATUS_ACTIVE;

        @Override
        public void commit()
                throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SystemException {
            if (status == Status.STATUS_MARKED_ROLLBACK) {
                rollback();
                throw new RollbackException("Transaction was marked rollback-only");
            }
            status = Status.STATUS_COMMITTING;
            for (Synchronization synchronization : synchronizations) {
                synchronization.beforeCompletion();
            }
            for (XAResource resource : resources) {
                commitResource(resource);
            }
            status = Status.STATUS_COMMITTED;
            for (Synchronization synchronization : synchronizations) {
                synchronization.afterCompletion(Status.STATUS_COMMITTED);
            }
        }

        @Override
        public void rollback() throws SystemException {
            status = Status.STATUS_ROLLING_BACK;
            for (XAResource resource : resources) {
                rollbackResource(resource);
            }
            status = Status.STATUS_ROLLEDBACK;
            for (Synchronization synchronization : synchronizations) {
                synchronization.afterCompletion(Status.STATUS_ROLLEDBACK);
            }
        }

        @Override
        public void setRollbackOnly() {
            status = Status.STATUS_MARKED_ROLLBACK;
        }

        @Override
        public int getStatus() {
            return status;
        }

        @Override
        public boolean enlistResource(XAResource resource) throws RollbackException, SystemException {
            if (status == Status.STATUS_MARKED_ROLLBACK) {
                throw new RollbackException("Transaction is marked rollback-only");
            }
            startResource(resource);
            resources.add(resource);
            return true;
        }

        @Override
        public boolean delistResource(XAResource resource, int flag) throws SystemException {
            endResource(resource, flag);
            return resources.contains(resource);
        }

        @Override
        public void registerSynchronization(Synchronization synchronization) throws RollbackException {
            if (status == Status.STATUS_MARKED_ROLLBACK) {
                throw new RollbackException("Transaction is marked rollback-only");
            }
            synchronizations.add(synchronization);
        }

        private void startResource(XAResource resource) throws SystemException {
            try {
                resource.start(xid, XAResource.TMNOFLAGS);
            } catch (XAException exception) {
                throw new SystemException(exception.errorCode);
            }
        }

        private void endResource(XAResource resource, int flag) throws SystemException {
            try {
                resource.end(xid, flag);
            } catch (XAException exception) {
                throw new SystemException(exception.errorCode);
            }
        }

        private void commitResource(XAResource resource) throws SystemException {
            try {
                resource.commit(xid, true);
            } catch (XAException exception) {
                throw new SystemException(exception.errorCode);
            }
        }

        private void rollbackResource(XAResource resource) throws SystemException {
            try {
                resource.rollback(xid);
            } catch (XAException exception) {
                throw new SystemException(exception.errorCode);
            }
        }
    }

    private static final class RecordingTransactionSynchronizationRegistry
            implements TransactionSynchronizationRegistry {
        private final RecordingTransactionManager manager;

        private RecordingTransactionSynchronizationRegistry(RecordingTransactionManager manager) {
            this.manager = manager;
        }

        @Override
        public Object getTransactionKey() {
            return manager.current;
        }

        @Override
        public int getTransactionStatus() {
            return manager.current == null ? Status.STATUS_NO_TRANSACTION : manager.current.status;
        }

        @Override
        public boolean getRollbackOnly() {
            return requireCurrent().status == Status.STATUS_MARKED_ROLLBACK;
        }

        @Override
        public void setRollbackOnly() {
            requireCurrent().status = Status.STATUS_MARKED_ROLLBACK;
        }

        @Override
        public void registerInterposedSynchronization(Synchronization synchronization) {
            requireCurrent().synchronizations.add(synchronization);
        }

        @Override
        public Object getResource(Object key) {
            return requireCurrent().registryResources.get(key);
        }

        @Override
        public void putResource(Object key, Object value) {
            requireCurrent().registryResources.put(key, value);
        }

        private RecordingTransaction requireCurrent() {
            if (manager.current == null) {
                throw new IllegalStateException("No active transaction");
            }
            return manager.current;
        }
    }

    private static final class RecordingUserTransaction implements UserTransaction {
        private final RecordingTransactionManager manager;

        private RecordingUserTransaction(RecordingTransactionManager manager) {
            this.manager = manager;
        }

        @Override
        public void begin() throws NotSupportedException, SystemException {
            manager.begin();
        }

        @Override
        public void commit()
                throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SystemException {
            manager.commit();
        }

        @Override
        public void rollback() throws SystemException {
            manager.rollback();
        }

        @Override
        public void setRollbackOnly() throws SystemException {
            manager.setRollbackOnly();
        }

        @Override
        public int getStatus() throws SystemException {
            return manager.getStatus();
        }

        @Override
        public void setTransactionTimeout(int seconds) throws SystemException {
            manager.setTransactionTimeout(seconds);
        }
    }

    private static final class RecordingXAResource implements XAResource {
        private final List<String> events = new ArrayList<>();

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
            events.add("forget");
        }

        @Override
        public int getTransactionTimeout() {
            return 0;
        }

        @Override
        public boolean isSameRM(XAResource resource) {
            return this == resource;
        }

        @Override
        public int prepare(Xid xid) {
            events.add("prepare");
            return XAResource.XA_OK;
        }

        @Override
        public Xid[] recover(int flag) {
            events.add("recover:" + flag);
            return new Xid[0];
        }

        @Override
        public void rollback(Xid xid) {
            events.add("rollback");
        }

        @Override
        public boolean setTransactionTimeout(int seconds) {
            events.add("timeout:" + seconds);
            return true;
        }

        @Override
        public void start(Xid xid, int flags) {
            events.add("start:" + flags);
        }
    }

    private static final class RecoverableXAResource implements XAResource {
        private final List<String> events = new ArrayList<>();
        private final Xid preparedXid;
        private int timeoutSeconds;
        private boolean forgotten;

        private RecoverableXAResource(Xid preparedXid) {
            this.preparedXid = preparedXid;
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
            forgotten = true;
            events.add("forget:" + xid.getFormatId());
        }

        @Override
        public int getTransactionTimeout() {
            return timeoutSeconds;
        }

        @Override
        public boolean isSameRM(XAResource resource) {
            return this == resource;
        }

        @Override
        public int prepare(Xid xid) {
            events.add("prepare:" + xid.getFormatId());
            return XAResource.XA_OK;
        }

        @Override
        public Xid[] recover(int flag) {
            events.add("recover:" + flag);
            return forgotten ? new Xid[0] : new Xid[] {preparedXid};
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

    private static final class FixedXid implements Xid {
        private final int id;

        private FixedXid(int id) {
            this.id = id;
        }

        @Override
        public int getFormatId() {
            return id;
        }

        @Override
        public byte[] getGlobalTransactionId() {
            return new byte[] {(byte) id};
        }

        @Override
        public byte[] getBranchQualifier() {
            return new byte[] {(byte) (id + 1)};
        }
    }
}
