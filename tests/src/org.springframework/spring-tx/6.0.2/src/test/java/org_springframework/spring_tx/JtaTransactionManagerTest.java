/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_tx;

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
import jakarta.transaction.UserTransaction;

import javax.transaction.xa.XAResource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.jta.JtaTransactionManager;
import org.springframework.transaction.jta.JtaTransactionObject;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import static org.assertj.core.api.Assertions.assertThat;

public class JtaTransactionManagerTest {

    private RecordingUserTransaction userTransaction;

    private RecordingTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        this.userTransaction = new RecordingUserTransaction();
        this.transactionManager = new RecordingTransactionManager();
    }

    @Test
    void constructorConfiguresUserTransactionAndTransactionManager() {
        ExposedJtaTransactionManager manager = new ExposedJtaTransactionManager(
                this.userTransaction, this.transactionManager);

        manager.afterPropertiesSet();

        assertThat(manager.getUserTransaction()).isSameAs(this.userTransaction);
        assertThat(manager.getTransactionManager()).isSameAs(this.transactionManager);
    }

    @Test
    void createTransactionUsesTransactionManagerBeginAndTimeout() throws NotSupportedException, SystemException {
        ExposedJtaTransactionManager manager = new ExposedJtaTransactionManager(
                this.userTransaction, this.transactionManager);

        Transaction transaction = manager.createTransaction("inventory.update", 7);

        assertThat(transaction).isNotNull();
        assertThat(this.transactionManager.beginCalls).isEqualTo(1);
        assertThat(this.transactionManager.lastTimeout).isEqualTo(7);
    }

    @Test
    void doJtaBeginAppliesTimeoutAndBeginsUserTransaction() throws NotSupportedException, SystemException {
        ExposedJtaTransactionManager manager = new ExposedJtaTransactionManager(
                this.userTransaction, this.transactionManager);
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setTimeout(11);

        manager.begin(new JtaTransactionObject(this.userTransaction), definition);

        assertThat(this.userTransaction.beginCalls).isEqualTo(1);
        assertThat(this.userTransaction.lastTimeout).isEqualTo(11);
    }

    @Test
    void doJtaResumeDelegatesToTransactionManager() throws InvalidTransactionException, SystemException {
        ExposedJtaTransactionManager manager = new ExposedJtaTransactionManager(
                this.userTransaction, this.transactionManager);

        manager.resume(this.transactionManager.transaction);

        assertThat(this.transactionManager.resumeCalls).isEqualTo(1);
        assertThat(this.transactionManager.lastResumedTransaction).isSameAs(this.transactionManager.transaction);
    }

    private static final class ExposedJtaTransactionManager extends JtaTransactionManager {

        private ExposedJtaTransactionManager(UserTransaction userTransaction, TransactionManager transactionManager) {
            super(userTransaction, transactionManager);
        }

        void begin(JtaTransactionObject transactionObject, TransactionDefinition definition)
                throws NotSupportedException, SystemException {
            doJtaBegin(transactionObject, definition);
        }

        void resume(Object suspendedTransaction) throws InvalidTransactionException, SystemException {
            doJtaResume(null, suspendedTransaction);
        }
    }

    private static final class RecordingUserTransaction implements UserTransaction {

        private int beginCalls;

        private int lastTimeout = TransactionDefinition.TIMEOUT_DEFAULT;

        @Override
        public void begin() {
            this.beginCalls++;
        }

        @Override
        public void commit() {
        }

        @Override
        public void rollback() {
        }

        @Override
        public void setRollbackOnly() {
        }

        @Override
        public int getStatus() {
            return Status.STATUS_ACTIVE;
        }

        @Override
        public void setTransactionTimeout(int seconds) {
            this.lastTimeout = seconds;
        }
    }

    private static final class RecordingTransactionManager implements TransactionManager {

        private final RecordingTransaction transaction = new RecordingTransaction();

        private int beginCalls;

        private int resumeCalls;

        private int lastTimeout = TransactionDefinition.TIMEOUT_DEFAULT;

        private Transaction lastResumedTransaction;

        @Override
        public void begin() {
            this.beginCalls++;
        }

        @Override
        public void commit() {
        }

        @Override
        public void rollback() {
        }

        @Override
        public void setRollbackOnly() {
        }

        @Override
        public int getStatus() {
            return Status.STATUS_ACTIVE;
        }

        @Override
        public Transaction getTransaction() {
            return this.transaction;
        }

        @Override
        public void setTransactionTimeout(int seconds) {
            this.lastTimeout = seconds;
        }

        @Override
        public Transaction suspend() {
            return this.transaction;
        }

        @Override
        public void resume(Transaction resumedTransaction) {
            this.resumeCalls++;
            this.lastResumedTransaction = resumedTransaction;
        }
    }

    private static final class RecordingTransaction implements Transaction {

        @Override
        public void commit()
                throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SystemException {
        }

        @Override
        public boolean delistResource(XAResource xaResource, int flag) {
            return true;
        }

        @Override
        public boolean enlistResource(XAResource xaResource) {
            return true;
        }

        @Override
        public int getStatus() {
            return Status.STATUS_ACTIVE;
        }

        @Override
        public void registerSynchronization(Synchronization synchronization) {
        }

        @Override
        public void rollback() {
        }

        @Override
        public void setRollbackOnly() {
        }
    }
}
