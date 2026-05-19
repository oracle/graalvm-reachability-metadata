/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_transaction;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizationAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizer;
import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizers;
import org.springframework.boot.transaction.autoconfigure.TransactionProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_transactionTest {
    @Test
    void transactionPropertiesCustomizePlatformTransactionManagerSettings() {
        assertThat(new TransactionAutoConfiguration()).isNotNull();

        TransactionProperties properties = new TransactionProperties();
        properties.setDefaultTimeout(Duration.ofSeconds(37));
        properties.setRollbackOnCommitFailure(true);
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();

        properties.customize(transactionManager);

        assertThat(properties.getDefaultTimeout()).isEqualTo(Duration.ofSeconds(37));
        assertThat(properties.getRollbackOnCommitFailure()).isTrue();
        assertThat(transactionManager.getDefaultTimeout()).isEqualTo(37);
        assertThat(transactionManager.isRollbackOnCommitFailure()).isTrue();
    }

    @Test
    void transactionManagerCustomizersApplyCompatibleCustomizersInOrder() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        List<String> calls = new ArrayList<>();
        TransactionManagerCustomizers customizers = TransactionManagerCustomizers.of(List.of(
                new RecordingManagerCustomizer("first", calls),
                new OtherManagerCustomizer(calls),
                new RecordingManagerCustomizer("second", calls)));

        customizers.customize(transactionManager);
        TransactionManagerCustomizers.of(null).customize(transactionManager);

        assertThat(calls).containsExactly("first", "second");
        assertThat(transactionManager.customizerCalls).containsExactly("first", "second");
    }

    @Test
    void autoConfigurationBindsPropertiesCreatesCustomizersAndTransactionTemplate() {
        try (AnnotationConfigApplicationContext context = newContext("15s", "true")) {
            TransactionProperties properties = context.getBean(TransactionProperties.class);
            RecordingTransactionManager transactionManager = context.getBean(RecordingTransactionManager.class);
            TransactionTemplate transactionTemplate = context.getBean(TransactionTemplate.class);
            RecordingTransactionExecutionListener listener = context.getBean(
                    RecordingTransactionExecutionListener.class);

            String result = transactionTemplate.execute((status) -> {
                assertThat(status.isNewTransaction()).isTrue();
                assertThat(status.isReadOnly()).isFalse();
                return "committed";
            });

            assertThat(result).isEqualTo("committed");
            assertThat(properties.getDefaultTimeout()).isEqualTo(Duration.ofSeconds(15));
            assertThat(properties.getRollbackOnCommitFailure()).isTrue();
            assertThat(transactionTemplate.getTransactionManager()).isSameAs(transactionManager);
            assertThat(transactionManager.getDefaultTimeout()).isEqualTo(15);
            assertThat(transactionManager.isRollbackOnCommitFailure()).isTrue();
            assertThat(transactionManager.begins).isEqualTo(1);
            assertThat(transactionManager.commits).isEqualTo(1);
            assertThat(transactionManager.rollbacks).isZero();
            assertThat(transactionManager.getTransactionExecutionListeners()).containsExactly(listener);
            assertThat(listener.events).containsExactly("beforeBegin", "afterBegin", "beforeCommit", "afterCommit");
        }
    }

    @Test
    void autoConfiguredTransactionTemplateRollsBackRollbackOnlyTransactions() {
        try (AnnotationConfigApplicationContext context = newContext("5s", "false")) {
            RecordingTransactionManager transactionManager = context.getBean(RecordingTransactionManager.class);
            TransactionTemplate transactionTemplate = context.getBean(TransactionTemplate.class);
            RecordingTransactionExecutionListener listener = context.getBean(
                    RecordingTransactionExecutionListener.class);

            String result = transactionTemplate.execute((status) -> {
                status.setRollbackOnly();
                return "rolled-back";
            });

            assertThat(result).isEqualTo("rolled-back");
            assertThat(transactionManager.getDefaultTimeout()).isEqualTo(5);
            assertThat(transactionManager.isRollbackOnCommitFailure()).isFalse();
            assertThat(transactionManager.begins).isEqualTo(1);
            assertThat(transactionManager.commits).isZero();
            assertThat(transactionManager.rollbacks).isEqualTo(1);
            assertThat(listener.events).containsExactly("beforeBegin", "afterBegin", "beforeRollback", "afterRollback");
        }
    }

    @Test
    void transactionAutoConfigurationCreatesTransactionTemplateForSingleTransactionManager() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                AutoConfiguredTemplateConfiguration.class)) {
            RecordingTransactionManager transactionManager = context.getBean(RecordingTransactionManager.class);
            TransactionTemplate transactionTemplate = context.getBean(TransactionTemplate.class);

            String result = transactionTemplate.execute((status) -> {
                assertThat(status.isNewTransaction()).isTrue();
                return "auto-configured";
            });

            assertThat(result).isEqualTo("auto-configured");
            assertThat(context.getBean(TransactionOperations.class)).isSameAs(transactionTemplate);
            assertThat(transactionTemplate.getTransactionManager()).isSameAs(transactionManager);
            assertThat(transactionManager.begins).isEqualTo(1);
            assertThat(transactionManager.commits).isEqualTo(1);
            assertThat(transactionManager.rollbacks).isZero();
        }
    }

    @Test
    void transactionAutoConfigurationEnablesDeclarativeTransactionManagementWithJdkProxies() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "spring.aop.proxy-target-class", "false")));
            context.register(DeclarativeTransactionConfiguration.class);
            context.refresh();

            TransactionalService service = context.getBean(TransactionalService.class);
            RecordingTransactionManager transactionManager = context.getBean(RecordingTransactionManager.class);

            assertThat(service.process()).isEqualTo("transaction-active");
            assertThat(transactionManager.begins).isEqualTo(1);
            assertThat(transactionManager.commits).isEqualTo(1);
            assertThat(transactionManager.rollbacks).isZero();
        }
    }

    private static AnnotationConfigApplicationContext newContext(
            String defaultTimeout, String rollbackOnCommitFailure) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        Map<String, Object> properties = Map.of(
                "spring.transaction.default-timeout", defaultTimeout,
                "spring.transaction.rollback-on-commit-failure", rollbackOnCommitFailure);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        context.register(
                TransactionInfrastructureConfiguration.class,
                TransactionManagerCustomizationAutoConfiguration.class);
        context.refresh();
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(TransactionAutoConfiguration.class)
    static class AutoConfiguredTemplateConfiguration {
        @Bean
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(TransactionAutoConfiguration.class)
    static class DeclarativeTransactionConfiguration {
        @Bean
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }

        @Bean
        TransactionalService transactionalService() {
            return new TransactionalServiceImpl();
        }
    }

    interface TransactionalService {
        @Transactional
        String process();
    }

    static class TransactionalServiceImpl implements TransactionalService {
        @Override
        public String process() {
            return TransactionSynchronizationManager.isActualTransactionActive()
                    ? "transaction-active" : "transaction-inactive";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TransactionInfrastructureConfiguration {
        @Bean
        RecordingTransactionExecutionListener transactionExecutionListener() {
            return new RecordingTransactionExecutionListener();
        }

        @Bean
        TransactionTemplate transactionTemplate(RecordingTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }

        @Bean
        RecordingTransactionManager transactionManager(TransactionManagerCustomizers customizers) {
            RecordingTransactionManager transactionManager = new RecordingTransactionManager();
            customizers.customize(transactionManager);
            return transactionManager;
        }
    }

    static class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private final List<String> customizerCalls = new ArrayList<>();

        private int begins;

        private int commits;

        private int rollbacks;

        @Override
        protected Object doGetTransaction() throws TransactionException {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) throws TransactionException {
            this.begins++;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) throws TransactionException {
            this.commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) throws TransactionException {
            this.rollbacks++;
        }

        void recordCustomizerCall(String name) {
            this.customizerCalls.add(name);
        }
    }

    static class OtherTransactionManager extends RecordingTransactionManager {
    }

    static class RecordingManagerCustomizer implements TransactionManagerCustomizer<RecordingTransactionManager> {
        private final String name;

        private final List<String> calls;

        RecordingManagerCustomizer(String name, List<String> calls) {
            this.name = name;
            this.calls = calls;
        }

        @Override
        public void customize(RecordingTransactionManager transactionManager) {
            this.calls.add(this.name);
            transactionManager.recordCustomizerCall(this.name);
        }
    }

    static class OtherManagerCustomizer implements TransactionManagerCustomizer<OtherTransactionManager> {
        private final List<String> calls;

        OtherManagerCustomizer(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public void customize(OtherTransactionManager transactionManager) {
            this.calls.add("other");
            transactionManager.recordCustomizerCall("other");
        }
    }

    static class RecordingTransactionExecutionListener implements TransactionExecutionListener {
        private final List<String> events = new ArrayList<>();

        @Override
        public void beforeBegin(TransactionExecution transaction) {
            this.events.add("beforeBegin");
        }

        @Override
        public void afterBegin(TransactionExecution transaction, Throwable beginFailure) {
            this.events.add("afterBegin");
            assertThat(beginFailure).isNull();
        }

        @Override
        public void beforeCommit(TransactionExecution transaction) {
            this.events.add("beforeCommit");
        }

        @Override
        public void afterCommit(TransactionExecution transaction, Throwable commitFailure) {
            this.events.add("afterCommit");
            assertThat(commitFailure).isNull();
        }

        @Override
        public void beforeRollback(TransactionExecution transaction) {
            this.events.add("beforeRollback");
        }

        @Override
        public void afterRollback(TransactionExecution transaction, Throwable rollbackFailure) {
            this.events.add("afterRollback");
            assertThat(rollbackFailure).isNull();
        }
    }
}
