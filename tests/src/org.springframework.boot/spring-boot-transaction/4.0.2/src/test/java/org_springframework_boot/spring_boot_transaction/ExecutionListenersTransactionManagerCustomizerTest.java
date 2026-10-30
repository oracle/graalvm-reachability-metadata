/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_transaction;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizationAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizers;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
public class ExecutionListenersTransactionManagerCustomizerTest {

    @Test
    void attachesApplicationListenersInOrderForCommitAndRollback() {
        List<String> events = new ArrayList<>();
        RecordingListener first = new RecordingListener("first", 0, events);
        RecordingListener second = new RecordingListener("second", 10, events);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("secondListener", RecordingListener.class, () -> second);
            context.registerBean("firstListener", RecordingListener.class, () -> first);
            context.register(TransactionManagerCustomizationAutoConfiguration.class);
            context.refresh();
            RecordingTransactionManager manager = new RecordingTransactionManager();
            context.getBean(TransactionManagerCustomizers.class).customize(manager);
            TransactionTemplate template = new TransactionTemplate(manager);

            String result = template.execute(status -> "committed");
            template.executeWithoutResult(status -> status.setRollbackOnly());

            assertThat(result).isEqualTo("committed");
            assertThat(manager.getTransactionExecutionListeners()).containsExactly(first, second);
            assertThat(manager.getEvents()).containsExactly("begin:-1", "commit", "begin:-1", "rollback");
            assertThat(events).containsExactly("first:begin", "second:begin", "first:commit", "second:commit",
                    "first:begin", "second:begin", "first:rollback", "second:rollback");
        }
    }

    public static final class RecordingListener implements TransactionExecutionListener, Ordered {

        private final String name;

        private final int order;

        private final List<String> events;

        RecordingListener(String name, int order, List<String> events) {
            this.name = name;
            this.order = order;
            this.events = events;
        }

        @Override
        public int getOrder() {
            return this.order;
        }

        @Override
        public void beforeBegin(TransactionExecution transaction) {
            this.events.add(this.name + ":begin");
        }

        @Override
        public void beforeCommit(TransactionExecution transaction) {
            this.events.add(this.name + ":commit");
        }

        @Override
        public void beforeRollback(TransactionExecution transaction) {
            this.events.add(this.name + ":rollback");
        }
    }
}
