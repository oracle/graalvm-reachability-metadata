/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_narayana_jta.jta;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.TransactionSynchronizationRegistry;

import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import com.arjuna.ats.jta.common.jtaPropertyManager;
import com.arjuna.ats.jta.utils.JNDIManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JNDIManagerTest {
    @Test
    void bindTransactionSynchronizationRegistryImplementationUsesConfiguredClass() throws Exception {
        JTAEnvironmentBean environmentBean = jtaPropertyManager.getJTAEnvironmentBean();
        String originalClassName = environmentBean.getTransactionSynchronizationRegistryClassName();
        String originalJndiName = environmentBean.getTransactionSynchronizationRegistryJNDIContext();

        try {
            TestTransactionSynchronizationRegistry.CONSTRUCTOR_CALLS.set(0);
            environmentBean.setTransactionSynchronizationRegistryClassName(
                TestTransactionSynchronizationRegistry.class.getName()
            );
            environmentBean.setTransactionSynchronizationRegistryJNDIContext(
                "java:/test/TransactionSynchronizationRegistry"
            );

            RecordingInitialContext initialContext = new RecordingInitialContext();

            JNDIManager.bindJTATransactionSynchronizationRegistryImplementation(initialContext);

            assertThat(TestTransactionSynchronizationRegistry.CONSTRUCTOR_CALLS).hasValue(1);
            assertThat(initialContext.boundName).isEqualTo("java:/test/TransactionSynchronizationRegistry");
            assertThat(initialContext.boundValue).isInstanceOf(TestTransactionSynchronizationRegistry.class);
        } finally {
            environmentBean.setTransactionSynchronizationRegistryClassName(originalClassName);
            environmentBean.setTransactionSynchronizationRegistryJNDIContext(originalJndiName);
        }
    }

    public static final class TestTransactionSynchronizationRegistry implements TransactionSynchronizationRegistry {
        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();

        private final Map<Object, Object> resources = new HashMap<>();
        private boolean rollbackOnly;

        public TestTransactionSynchronizationRegistry() {
            CONSTRUCTOR_CALLS.incrementAndGet();
        }

        @Override
        public Object getTransactionKey() {
            return this;
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
        }

        @Override
        public int getTransactionStatus() {
            return Status.STATUS_NO_TRANSACTION;
        }

        @Override
        public void setRollbackOnly() {
            rollbackOnly = true;
        }

        @Override
        public boolean getRollbackOnly() {
            return rollbackOnly;
        }
    }

    private static final class RecordingInitialContext extends InitialContext {
        private String boundName;
        private Object boundValue;

        private RecordingInitialContext() throws NamingException {
            super(true);
        }

        @Override
        public void rebind(String name, Object obj) {
            boundName = name;
            boundValue = obj;
        }
    }
}
