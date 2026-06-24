/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package weblogic.transaction;

import java.io.Serializable;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.InvalidTransactionException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.xa.XAResource;

/**
 * Test double for WebLogic's static transaction helper.
 */
public final class TransactionHelper {

    private static final RecordingUserTransaction USER_TRANSACTION = new RecordingUserTransaction();

    private static final RecordingClientTransactionManager TRANSACTION_MANAGER =
            new RecordingClientTransactionManager();

    private static int getTransactionHelperCalls;

    public static TransactionHelper getTransactionHelper() {
        getTransactionHelperCalls++;
        return new TransactionHelper();
    }

    public static void reset() {
        getTransactionHelperCalls = 0;
        USER_TRANSACTION.reset();
        TRANSACTION_MANAGER.reset();
    }

    public static int getTransactionHelperCalls() {
        return getTransactionHelperCalls;
    }

    public static RecordingUserTransaction userTransaction() {
        return USER_TRANSACTION;
    }

    public static RecordingClientTransactionManager transactionManager() {
        return TRANSACTION_MANAGER;
    }

    public UserTransaction getUserTransaction() {
        return USER_TRANSACTION;
    }

    public ClientTransactionManager getTransactionManager() {
        return TRANSACTION_MANAGER;
    }

    public static final class RecordingUserTransaction implements UserTransaction {

        private int beginCalls;

        private int beginWithNameCalls;

        private int beginWithNameAndTimeoutCalls;

        private String lastName;

        private int lastTimeout;

        private void reset() {
            this.beginCalls = 0;
            this.beginWithNameCalls = 0;
            this.beginWithNameAndTimeoutCalls = 0;
            this.lastName = null;
            this.lastTimeout = -1;
        }

        @Override
        public void begin() {
            this.beginCalls++;
        }

        @Override
        public void begin(String name) {
            this.beginWithNameCalls++;
            this.lastName = name;
        }

        @Override
        public void begin(String name, int timeout) {
            this.beginWithNameAndTimeoutCalls++;
            this.lastName = name;
            this.lastTimeout = timeout;
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

        public int beginCalls() {
            return this.beginCalls;
        }

        public int beginWithNameCalls() {
            return this.beginWithNameCalls;
        }

        public int beginWithNameAndTimeoutCalls() {
            return this.beginWithNameAndTimeoutCalls;
        }

        public String lastName() {
            return this.lastName;
        }

        public int lastTimeout() {
            return this.lastTimeout;
        }
    }

    public static final class RecordingClientTransactionManager implements ClientTransactionManager {

        private final RecordingTransaction transaction = new RecordingTransaction();

        private int resumeCalls;

        private int forceResumeCalls;

        private boolean failResume;

        private javax.transaction.Transaction lastResumedTransaction;

        private void reset() {
            this.resumeCalls = 0;
            this.forceResumeCalls = 0;
            this.failResume = false;
            this.lastResumedTransaction = null;
            this.transaction.reset();
        }

        @Override
        public void begin() {
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
        public javax.transaction.Transaction getTransaction() {
            return this.transaction;
        }

        @Override
        public void setTransactionTimeout(int seconds) {
        }

        @Override
        public javax.transaction.Transaction suspend() {
            return this.transaction;
        }

        @Override
        public void resume(javax.transaction.Transaction transaction) throws InvalidTransactionException {
            this.resumeCalls++;
            this.lastResumedTransaction = transaction;
            if (this.failResume) {
                throw new InvalidTransactionException("resume rejected by test double");
            }
        }

        @Override
        public void forceResume(javax.transaction.Transaction transaction) {
            this.forceResumeCalls++;
            this.lastResumedTransaction = transaction;
        }

        public void failResume() {
            this.failResume = true;
        }

        public RecordingTransaction transaction() {
            return this.transaction;
        }

        public int resumeCalls() {
            return this.resumeCalls;
        }

        public int forceResumeCalls() {
            return this.forceResumeCalls;
        }

        public javax.transaction.Transaction lastResumedTransaction() {
            return this.lastResumedTransaction;
        }
    }

    public static final class RecordingTransaction implements Transaction {

        private String lastPropertyName;

        private Serializable lastPropertyValue;

        private int setPropertyCalls;

        private void reset() {
            this.lastPropertyName = null;
            this.lastPropertyValue = null;
            this.setPropertyCalls = 0;
        }

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

        @Override
        public void setProperty(String name, Serializable value) {
            this.setPropertyCalls++;
            this.lastPropertyName = name;
            this.lastPropertyValue = value;
        }

        public String lastPropertyName() {
            return this.lastPropertyName;
        }

        public Serializable lastPropertyValue() {
            return this.lastPropertyValue;
        }

        public int setPropertyCalls() {
            return this.setPropertyCalls;
        }
    }
}
