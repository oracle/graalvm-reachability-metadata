/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_batch.spring_batch_infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.batch.infrastructure.support.transaction.TransactionAwareProxyFactory;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

@SuppressWarnings("removal")
public class TransactionAwareProxyFactoryInnerTransactionAwareInterceptorTest {
    @Test
    void commitsOperationsInvokedOnTheTransactionalCopy() {
        Map<String, String> map = TransactionAwareProxyFactory.createTransactionalMap();
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(new ResourcelessTransactionManager());

        transactionTemplate.execute(
                new TransactionCallbackWithoutResult() {
                    @Override
                    protected void doInTransactionWithoutResult(TransactionStatus status) {
                        assertThat(map.put("job", "running")).isNull();
                        assertThat(map.get("job")).isEqualTo("running");
                    }
                });

        assertThat(map).containsEntry("job", "running");
    }
}
