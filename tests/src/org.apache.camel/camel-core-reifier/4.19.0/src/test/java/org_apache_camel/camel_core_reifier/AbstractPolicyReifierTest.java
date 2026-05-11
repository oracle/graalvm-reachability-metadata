/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_core_reifier;

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.TransactedDefinition;
import org.apache.camel.reifier.AbstractPolicyReifier;
import org.apache.camel.spi.Policy;
import org.apache.camel.spi.TransactedPolicy;
import org.apache.camel.support.SimpleRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractPolicyReifierTest {
    @Test
    void createsSpringTransactionPolicyFromRegistryTransactionManager() throws Exception {
        PlatformTransactionManager transactionManager = new NoopTransactionManager();
        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("transactionManager", transactionManager);

        try (CamelContext camelContext = new DefaultCamelContext(registry)) {
            Policy policy = new TestPolicyReifier(camelContext).resolvePolicy(null, null, TransactedPolicy.class);

            assertThat(policy).isInstanceOf(TransactedPolicy.class);
        }
    }

    private static final class TestPolicyReifier extends AbstractPolicyReifier<TransactedDefinition> {
        private TestPolicyReifier(CamelContext camelContext) {
            super(camelContext, new TransactedDefinition());
        }

        @Override
        public Processor createProcessor() throws Exception {
            throw new UnsupportedOperationException("Processor creation is not needed for resolvePolicy coverage");
        }
    }

    private static final class NoopTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) throws TransactionException {
        }

        @Override
        public void rollback(TransactionStatus status) throws TransactionException {
        }
    }
}
