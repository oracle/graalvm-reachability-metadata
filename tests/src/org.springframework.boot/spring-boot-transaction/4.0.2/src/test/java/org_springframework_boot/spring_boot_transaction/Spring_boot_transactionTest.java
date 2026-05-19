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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizationAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizer;
import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizers;
import org.springframework.boot.transaction.autoconfigure.TransactionProperties;
import org.springframework.boot.transaction.jta.autoconfigure.JtaAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.ReactiveTransaction;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.jta.JtaTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class Spring_boot_transactionTest {

    @Test
    void autoConfigurationsArePublishedAsImportCandidates() {
        ClassLoader classLoader = TransactionAutoConfiguration.class.getClassLoader();

        assertThat(ImportCandidates.load(AutoConfiguration.class, classLoader).getCandidates())
                .contains(TransactionManagerCustomizationAutoConfiguration.class.getName(),
                        TransactionAutoConfiguration.class.getName(), JtaAutoConfiguration.class.getName());
    }

    @Test
    void transactionPropertiesApplyConfiguredManagerOptions() {
        TransactionProperties properties = new TransactionProperties();
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();

        assertThat(properties.getDefaultTimeout()).isNull();
        assertThat(properties.getRollbackOnCommitFailure()).isNull();

        properties.setDefaultTimeout(Duration.ofSeconds(12));
        properties.setRollbackOnCommitFailure(true);
        properties.customize(transactionManager);

        assertThat(properties.getDefaultTimeout()).isEqualTo(Duration.ofSeconds(12));
        assertThat(properties.getRollbackOnCommitFailure()).isTrue();
        assertThat(transactionManager.getDefaultTimeout()).isEqualTo(12);
        assertThat(transactionManager.isRollbackOnCommitFailure()).isTrue();
    }

    @Test
    void transactionManagerCustomizersApplyOnlyCompatibleCustomizers() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        TransactionProperties properties = new TransactionProperties();
        properties.setDefaultTimeout(Duration.ofSeconds(4));
        AtomicInteger compatibleCustomizations = new AtomicInteger();
        TransactionManagerCustomizer<RecordingTransactionManager> compatibleCustomizer = (manager) -> {
            compatibleCustomizations.incrementAndGet();
            manager.customized = true;
        };
        TransactionManagerCustomizer<JtaTransactionManager> incompatibleCustomizer =
                new FailingJtaTransactionManagerCustomizer();

        TransactionManagerCustomizers.of(List.of(properties, compatibleCustomizer, incompatibleCustomizer))
                .customize(transactionManager);

        assertThat(transactionManager.getDefaultTimeout()).isEqualTo(4);
        assertThat(transactionManager.customized).isTrue();
        assertThat(compatibleCustomizations).hasValue(1);
    }

    @Test
    void customizationAutoConfigurationBindsPropertiesAndRegistersExecutionListeners() {
        try (AnnotationConfigApplicationContext context = contextWithProperties(
                Map.of("spring.transaction.default-timeout", "7s",
                        "spring.transaction.rollback-on-commit-failure", "true"),
                TransactionManagerCustomizationAutoConfiguration.class, ListenerConfiguration.class)) {
            RecordingTransactionManager transactionManager = new RecordingTransactionManager();
            RecordingTransactionExecutionListener listener = context
                    .getBean(RecordingTransactionExecutionListener.class);

            context.getBean(TransactionManagerCustomizers.class).customize(transactionManager);
            new TransactionTemplate(transactionManager).execute((status) -> "ok");

            assertThat(context.getBean(TransactionProperties.class).getDefaultTimeout())
                    .isEqualTo(Duration.ofSeconds(7));
            assertThat(transactionManager.getDefaultTimeout()).isEqualTo(7);
            assertThat(transactionManager.isRollbackOnCommitFailure()).isTrue();
            assertThat(transactionManager.getTransactionExecutionListeners()).containsExactly(listener);
            assertThat(listener.events).containsExactly("beforeBegin", "afterBegin", "beforeCommit", "afterCommit");
        }
    }

    @Test
    void transactionAutoConfigurationCreatesTemplateAndJdkTransactionalProxy() {
        try (AnnotationConfigApplicationContext context = contextWithProperties(
                Map.of("spring.aop.proxy-target-class", "false"),
                TransactionAutoConfigurationImport.class, TransactionalServiceConfiguration.class)) {
            RecordingTransactionManager transactionManager = context.getBean(RecordingTransactionManager.class);
            TransactionTemplate transactionTemplate = context.getBean(TransactionTemplate.class);
            TransactionalService service = context.getBean(TransactionalService.class);

            String result = transactionTemplate.execute((status) -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                return "template";
            });
            service.performInTransaction();

            assertThat(result).isEqualTo("template");
            assertThat(transactionTemplate.getTransactionManager()).isSameAs(transactionManager);
            assertThat(context.getBean(TransactionOperations.class)).isSameAs(transactionTemplate);
            assertThat(AopUtils.isJdkDynamicProxy(service)).isTrue();
            assertThat(transactionManager.beginCount).isEqualTo(2);
            assertThat(transactionManager.commitCount).isEqualTo(2);
            assertThat(transactionManager.rollbackCount).isZero();
            assertThat(transactionManager.lastTransactionName).endsWith(".performInTransaction");
        }
    }

    @Test
    void transactionAutoConfigurationHonorsUserProvidedTransactionManagementConfiguration() {
        try (AnnotationConfigApplicationContext context = contextWithProperties(
                Map.of("spring.aop.proxy-target-class", "true"),
                TransactionAutoConfigurationImport.class, ExplicitJdkTransactionManagementConfiguration.class)) {
            RecordingTransactionManager transactionManager = context.getBean(RecordingTransactionManager.class);
            TransactionalService service = context.getBean(TransactionalService.class);

            service.performInTransaction();

            assertThat(AopUtils.isJdkDynamicProxy(service)).isTrue();
            assertThat(AopUtils.isCglibProxy(service)).isFalse();
            assertThat(transactionManager.beginCount).isEqualTo(1);
            assertThat(transactionManager.commitCount).isEqualTo(1);
            assertThat(transactionManager.rollbackCount).isZero();
        }
    }

    @Test
    void transactionAutoConfigurationBacksOffWhenUserProvidesTransactionOperations() {
        try (AnnotationConfigApplicationContext context = contextWithProperties(
                Map.of("spring.aop.proxy-target-class", "false"),
                TransactionAutoConfigurationImport.class, CustomTransactionOperationsConfiguration.class)) {
            TransactionOperations operations = context.getBean(TransactionOperations.class);

            assertThat(operations).isInstanceOf(CustomTransactionOperations.class);
            assertThat(context.getBeansOfType(TransactionTemplate.class)).isEmpty();
        }
    }

    @Test
    void transactionAutoConfigurationCreatesReactiveTransactionalOperator() {
        try (AnnotationConfigApplicationContext context = contextWithProperties(
                Map.of("spring.aop.proxy-target-class", "false"),
                TransactionAutoConfigurationImport.class, ReactiveTransactionManagerConfiguration.class)) {
            RecordingReactiveTransactionManager transactionManager = context
                    .getBean(RecordingReactiveTransactionManager.class);
            TransactionalOperator operator = context.getBean(TransactionalOperator.class);

            String result = operator.transactional(Mono.just("reactive")).block(Duration.ofSeconds(10));

            assertThat(result).isEqualTo("reactive");
            assertThat(transactionManager.beginCount).isEqualTo(1);
            assertThat(transactionManager.commitCount).isEqualTo(1);
            assertThat(transactionManager.rollbackCount).isZero();
        }
    }

    private static AnnotationConfigApplicationContext contextWithProperties(Map<String, Object> properties,
            Class<?>... configurationClasses) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        context.register(configurationClasses);
        try {
            context.refresh();
        } catch (RuntimeException ex) {
            context.close();
            throw ex;
        }
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(TransactionAutoConfiguration.class)
    public static class TransactionAutoConfigurationImport {
    }

    @Configuration(proxyBeanMethods = false)
    public static class ListenerConfiguration {

        @Bean
        RecordingTransactionExecutionListener transactionExecutionListener() {
            return new RecordingTransactionExecutionListener();
        }

    }

    @Configuration(proxyBeanMethods = false)
    public static class TransactionalServiceConfiguration {

        @Bean
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }

        @Bean
        TransactionalService transactionalService() {
            return new DefaultTransactionalService();
        }

    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = false)
    public static class ExplicitJdkTransactionManagementConfiguration {

        @Bean
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }

        @Bean
        TransactionalService transactionalService() {
            return new DefaultTransactionalService();
        }

    }

    @Configuration(proxyBeanMethods = false)
    public static class CustomTransactionOperationsConfiguration {

        @Bean
        PlatformTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }

        @Bean
        TransactionOperations transactionOperations() {
            return new CustomTransactionOperations();
        }

    }

    @Configuration(proxyBeanMethods = false)
    public static class ReactiveTransactionManagerConfiguration {

        @Bean
        RecordingReactiveTransactionManager reactiveTransactionManager() {
            return new RecordingReactiveTransactionManager();
        }

    }

    public interface TransactionalService {

        @Transactional
        void performInTransaction();

    }

    public static class DefaultTransactionalService implements TransactionalService {

        @Override
        public void performInTransaction() {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        }

    }

    public static class FailingJtaTransactionManagerCustomizer
            implements TransactionManagerCustomizer<JtaTransactionManager> {

        @Override
        public void customize(JtaTransactionManager transactionManager) {
            fail("JTA-specific customizer should not be invoked for a platform transaction manager");
        }

    }

    public static class RecordingTransactionExecutionListener implements TransactionExecutionListener {

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
        }

    }

    public static class RecordingTransactionManager extends AbstractPlatformTransactionManager {

        private int beginCount;

        private int commitCount;

        private int rollbackCount;

        private boolean customized;

        private String lastTransactionName;

        @Override
        protected Object doGetTransaction() throws TransactionException {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) throws TransactionException {
            this.beginCount++;
            this.lastTransactionName = definition.getName();
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) throws TransactionException {
            this.commitCount++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) throws TransactionException {
            this.rollbackCount++;
        }

    }

    public static class RecordingReactiveTransactionManager implements ReactiveTransactionManager {

        private int beginCount;

        private int commitCount;

        private int rollbackCount;

        @Override
        public Mono<ReactiveTransaction> getReactiveTransaction(TransactionDefinition definition)
                throws TransactionException {
            this.beginCount++;
            return Mono.just(new SimpleReactiveTransaction());
        }

        @Override
        public Mono<Void> commit(ReactiveTransaction transaction) throws TransactionException {
            this.commitCount++;
            return Mono.empty();
        }

        @Override
        public Mono<Void> rollback(ReactiveTransaction transaction) throws TransactionException {
            this.rollbackCount++;
            return Mono.empty();
        }

    }

    public static class SimpleReactiveTransaction implements ReactiveTransaction {
    }

    public static class CustomTransactionOperations implements TransactionOperations {

        @Override
        public <T> T execute(TransactionCallback<T> action) throws TransactionException {
            fail("The custom transaction operations bean is only used to test conditional bean creation");
            return null;
        }

    }

}
