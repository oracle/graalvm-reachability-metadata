/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_transaction;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
public class TransactionAutoConfigurationTest {

    @Test
    void createsATemplateForTheApplicationTransactionManager() {
        RecordingTransactionManager manager = new RecordingTransactionManager();
        try (AnnotationConfigApplicationContext context = newContext()) {
            context.registerBean("transactionManager", RecordingTransactionManager.class, () -> manager);
            context.refresh();

            TransactionTemplate template = context.getBean(TransactionTemplate.class);
            String result = template.execute(status -> "saved");
            template.executeWithoutResult(status -> status.setRollbackOnly());

            assertThat(template.getTransactionManager()).isSameAs(manager);
            assertThat(context.getBean(TransactionOperations.class)).isSameAs(template);
            assertThat(result).isEqualTo("saved");
            assertThat(manager.getEvents()).containsExactly("begin:-1", "commit", "begin:-1", "rollback");
        }
    }

    @Test
    void preservesApplicationProvidedTransactionOperations() {
        RecordingTransactionManager manager = new RecordingTransactionManager();
        TransactionTemplate supplied = new TransactionTemplate(manager);
        supplied.setTimeout(65);
        try (AnnotationConfigApplicationContext context = newContext()) {
            context.registerBean("transactionManager", RecordingTransactionManager.class, () -> manager);
            context.registerBean("applicationOperations", TransactionOperations.class, () -> supplied);
            context.refresh();

            TransactionOperations operations = context.getBean(TransactionOperations.class);
            Integer result = operations.execute(status -> 7);

            assertThat(operations).isSameAs(supplied);
            assertThat(context.getBeansOfType(TransactionOperations.class)).hasSize(1);
            assertThat(result).isEqualTo(7);
            assertThat(manager.getEvents()).containsExactly("begin:65", "commit");
        }
    }

    @Test
    void doesNotChooseBetweenMultipleTransactionManagers() {
        try (AnnotationConfigApplicationContext context = newContext()) {
            context.registerBean("firstManager", RecordingTransactionManager.class, RecordingTransactionManager::new);
            context.registerBean("secondManager", RecordingTransactionManager.class, RecordingTransactionManager::new);
            context.refresh();

            assertThat(context.getBeansOfType(TransactionOperations.class)).isEmpty();
            for (RecordingTransactionManager manager : context.getBeansOfType(RecordingTransactionManager.class)
                    .values()) {
                assertThat(manager.getEvents()).isEmpty();
            }
        }
    }

    @Test
    void doesNotCreateTransactionOperationsWithoutAManager() {
        try (AnnotationConfigApplicationContext context = newContext()) {
            context.refresh();

            assertThat(context.getBeansOfType(TransactionOperations.class)).isEmpty();
        }
    }

    private static AnnotationConfigApplicationContext newContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("proxySettings",
                Map.of("spring.aop.proxy-target-class", "false")));
        context.register(TransactionAutoConfiguration.class);
        return context;
    }
}
