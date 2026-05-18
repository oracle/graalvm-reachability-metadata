/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_tx;

import javax.transaction.InvalidTransactionException;
import javax.transaction.NotSupportedException;
import javax.transaction.SystemException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.jta.JtaTransactionObject;
import org.springframework.transaction.jta.WebLogicJtaTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import weblogic.transaction.TransactionHelper;

import static org.assertj.core.api.Assertions.assertThat;

public class WebLogicJtaTransactionManagerTest {

    @BeforeEach
    void resetWebLogicTestDouble() {
        TransactionHelper.reset();
    }

    @Test
    void afterPropertiesSetRetrievesWebLogicHandlesAndInitializesWebLogicMethods() {
        ExposedWebLogicJtaTransactionManager transactionManager = initializedTransactionManager();

        assertThat(transactionManager.getUserTransaction()).isSameAs(TransactionHelper.userTransaction());
        assertThat(transactionManager.getTransactionManager()).isSameAs(TransactionHelper.transactionManager());
        assertThat(TransactionHelper.getTransactionHelperCalls()).isEqualTo(1);
    }

    @Test
    void createTransactionUsesWebLogicBeginOverloadsForNamedTransactions()
            throws NotSupportedException, SystemException {
        ExposedWebLogicJtaTransactionManager transactionManager = initializedTransactionManager();

        transactionManager.createTransaction("timed", 7);
        transactionManager.createTransaction("untimed", TransactionDefinition.TIMEOUT_DEFAULT);

        assertThat(TransactionHelper.userTransaction().beginWithNameAndTimeoutCalls()).isEqualTo(1);
        assertThat(TransactionHelper.userTransaction().beginWithNameCalls()).isEqualTo(1);
        assertThat(TransactionHelper.userTransaction().lastName()).isEqualTo("untimed");
        assertThat(TransactionHelper.userTransaction().lastTimeout()).isEqualTo(7);
    }

    @Test
    void doJtaBeginAppliesWebLogicNameTimeoutAndIsolation() throws NotSupportedException, SystemException {
        ExposedWebLogicJtaTransactionManager transactionManager = initializedTransactionManager();
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setName("inventory.update");
        definition.setTimeout(11);
        definition.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);

        transactionManager.begin(new JtaTransactionObject(TransactionHelper.userTransaction()), definition);

        assertThat(TransactionHelper.userTransaction().beginWithNameAndTimeoutCalls()).isEqualTo(1);
        assertThat(TransactionHelper.userTransaction().lastName()).isEqualTo("inventory.update");
        assertThat(TransactionHelper.userTransaction().lastTimeout()).isEqualTo(11);
        assertThat(TransactionHelper.transactionManager().transaction().setPropertyCalls()).isEqualTo(1);
        assertThat(TransactionHelper.transactionManager().transaction().lastPropertyName())
                .isEqualTo("ISOLATION LEVEL");
        assertThat(TransactionHelper.transactionManager().transaction().lastPropertyValue())
                .isEqualTo(TransactionDefinition.ISOLATION_SERIALIZABLE);
    }

    @Test
    void doJtaBeginUsesSingleArgumentWebLogicBeginWhenNoTimeout() throws NotSupportedException, SystemException {
        ExposedWebLogicJtaTransactionManager transactionManager = initializedTransactionManager();
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setName("billing.close");

        transactionManager.begin(new JtaTransactionObject(TransactionHelper.userTransaction()), definition);

        assertThat(TransactionHelper.userTransaction().beginWithNameCalls()).isEqualTo(1);
        assertThat(TransactionHelper.userTransaction().beginWithNameAndTimeoutCalls()).isZero();
        assertThat(TransactionHelper.userTransaction().lastName()).isEqualTo("billing.close");
    }

    @Test
    void doJtaResumeFallsBackToWebLogicForceResume() throws InvalidTransactionException, SystemException {
        ExposedWebLogicJtaTransactionManager transactionManager = initializedTransactionManager();
        TransactionHelper.transactionManager().failResume();

        transactionManager.resume(TransactionHelper.transactionManager().transaction());

        assertThat(TransactionHelper.transactionManager().resumeCalls()).isEqualTo(1);
        assertThat(TransactionHelper.transactionManager().forceResumeCalls()).isEqualTo(1);
        assertThat(TransactionHelper.transactionManager().lastResumedTransaction())
                .isSameAs(TransactionHelper.transactionManager().transaction());
    }

    private static ExposedWebLogicJtaTransactionManager initializedTransactionManager() {
        ExposedWebLogicJtaTransactionManager transactionManager = new ExposedWebLogicJtaTransactionManager();
        transactionManager.afterPropertiesSet();
        return transactionManager;
    }

    private static final class ExposedWebLogicJtaTransactionManager extends WebLogicJtaTransactionManager {

        void begin(JtaTransactionObject transactionObject, TransactionDefinition definition)
                throws NotSupportedException, SystemException {
            doJtaBegin(transactionObject, definition);
        }

        void resume(Object suspendedTransaction) throws InvalidTransactionException, SystemException {
            doJtaResume(null, suspendedTransaction);
        }
    }
}
