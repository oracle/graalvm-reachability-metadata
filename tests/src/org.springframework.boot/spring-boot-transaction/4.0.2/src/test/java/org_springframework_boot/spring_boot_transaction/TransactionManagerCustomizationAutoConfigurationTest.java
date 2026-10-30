/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_transaction;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizationAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizers;
import org.springframework.boot.transaction.autoconfigure.TransactionProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
public class TransactionManagerCustomizationAutoConfigurationTest {

    @Test
    void bindsTransactionPropertiesAndAppliesThemToTheManager() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("transactionSettings", Map.of(
                    "spring.transaction.default-timeout", "35",
                    "spring.transaction.rollback-on-commit-failure", "true")));
            context.register(TransactionManagerCustomizationAutoConfiguration.class);
            context.refresh();
            RecordingTransactionManager manager = new RecordingTransactionManager();

            context.getBean(TransactionManagerCustomizers.class).customize(manager);
            String result = new TransactionTemplate(manager).execute(status -> "stored");

            TransactionProperties properties = context.getBean(TransactionProperties.class);
            assertThat(properties.getDefaultTimeout()).isEqualTo(Duration.ofSeconds(35));
            assertThat(properties.getRollbackOnCommitFailure()).isTrue();
            assertThat(manager.isRollbackOnCommitFailure()).isTrue();
            assertThat(result).isEqualTo("stored");
            assertThat(manager.getEvents()).containsExactly("begin:35", "commit");
        }
    }

    @Test
    void preservesAnApplicationProvidedCustomizerCollection() {
        TransactionProperties properties = new TransactionProperties();
        properties.setDefaultTimeout(Duration.ofSeconds(55));
        TransactionManagerCustomizers supplied = TransactionManagerCustomizers.of(List.of(properties));
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("applicationCustomizers", TransactionManagerCustomizers.class, () -> supplied);
            context.register(TransactionManagerCustomizationAutoConfiguration.class);
            context.refresh();
            RecordingTransactionManager manager = new RecordingTransactionManager();

            context.getBean(TransactionManagerCustomizers.class).customize(manager);
            new TransactionTemplate(manager).executeWithoutResult(status -> status.setRollbackOnly());

            assertThat(context.getBeansOfType(TransactionManagerCustomizers.class)).hasSize(1);
            assertThat(context.getBean(TransactionManagerCustomizers.class)).isSameAs(supplied);
            assertThat(manager.getEvents()).containsExactly("begin:55", "rollback");
        }
    }
}
