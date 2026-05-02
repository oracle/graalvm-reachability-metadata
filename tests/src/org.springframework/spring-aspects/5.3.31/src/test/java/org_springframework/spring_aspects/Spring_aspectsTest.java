/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_aspects;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.beans.factory.aspectj.AnnotationBeanConfigurerAspect;
import org.springframework.beans.factory.aspectj.ConfigurableObject;
import org.springframework.beans.factory.aspectj.GenericInterfaceDrivenDependencyInjectionAspect;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.aspectj.AnnotationCacheAspect;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.config.CacheManagementConfigUtils;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.aspectj.EnableSpringConfigured;
import org.springframework.context.annotation.aspectj.SpringConfiguredConfiguration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.aspectj.AnnotationAsyncExecutionAspect;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.aspectj.AnnotationTransactionAspect;
import org.springframework.transaction.config.TransactionManagementConfigUtils;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Properties;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_aspectsTest {
    @Test
    void springConfiguredRegistersTheSingletonBeanConfigurerAspect() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SpringConfiguredTestConfiguration.class)) {
            BeanDefinition beanDefinition = context.getBeanFactory()
                    .getBeanDefinition(SpringConfiguredConfiguration.BEAN_CONFIGURER_ASPECT_BEAN_NAME);
            AnnotationBeanConfigurerAspect aspect = context.getBean(
                    SpringConfiguredConfiguration.BEAN_CONFIGURER_ASPECT_BEAN_NAME,
                    AnnotationBeanConfigurerAspect.class);

            assertThat(beanDefinition.getRole()).isEqualTo(BeanDefinition.ROLE_INFRASTRUCTURE);
            assertThat(aspect).isSameAs(AnnotationBeanConfigurerAspect.aspectOf());
            assertThat(AnnotationBeanConfigurerAspect.hasAspect()).isTrue();
        }
    }

    @Test
    void beanConfigurerAspectInjectsDependenciesIntoConfigurableObjects() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("configurableService", BeanDefinitionBuilder
                .genericBeanDefinition(ConfigurableService.class)
                .setScope(BeanDefinition.SCOPE_PROTOTYPE)
                .addPropertyValue("message", "configured by aspect")
                .getBeanDefinition());
        AnnotationBeanConfigurerAspect aspect = AnnotationBeanConfigurerAspect.aspectOf();
        aspect.setBeanFactory(beanFactory);
        aspect.afterPropertiesSet();

        ConfigurableService service = new ConfigurableService();
        aspect.configureBean(service);

        assertThat(service.getMessage()).isEqualTo("configured by aspect");
    }

    @Test
    void beanConfigurerAspectAutowiresConfigurableObjectsByType() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        MessageProvider provider = new MessageProvider("autowired by type");
        beanFactory.registerSingleton("messageProvider", provider);
        AnnotationBeanConfigurerAspect aspect = AnnotationBeanConfigurerAspect.aspectOf();
        aspect.setBeanFactory(beanFactory);
        aspect.afterPropertiesSet();

        AutowiredConfigurableService service = new AutowiredConfigurableService();
        aspect.configureBean(service);

        assertThat(service.getMessage()).isEqualTo("autowired by type");
        assertThat(service.getMessageProvider()).isSameAs(provider);
    }

    @Test
    void aspectjModeRegistersCacheTransactionAndAsyncInfrastructureAspects() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AspectJInfrastructureConfiguration.class)) {
            AnnotationCacheAspect cacheAspect = context.getBean(
                    CacheManagementConfigUtils.CACHE_ASPECT_BEAN_NAME,
                    AnnotationCacheAspect.class);
            AnnotationTransactionAspect transactionAspect = context.getBean(
                    TransactionManagementConfigUtils.TRANSACTION_ASPECT_BEAN_NAME,
                    AnnotationTransactionAspect.class);
            AnnotationAsyncExecutionAspect asyncAspect = context.getBean(
                    TaskManagementConfigUtils.ASYNC_EXECUTION_ASPECT_BEAN_NAME,
                    AnnotationAsyncExecutionAspect.class);

            assertThat(cacheAspect).isSameAs(AnnotationCacheAspect.aspectOf());
            assertThat(transactionAspect).isSameAs(AnnotationTransactionAspect.aspectOf());
            assertThat(asyncAspect).isSameAs(AnnotationAsyncExecutionAspect.aspectOf());
            assertThat(context.getBeanFactory()
                    .getBeanDefinition(CacheManagementConfigUtils.CACHE_ASPECT_BEAN_NAME)
                    .getRole()).isEqualTo(BeanDefinition.ROLE_INFRASTRUCTURE);
            assertThat(context.getBeanFactory()
                    .getBeanDefinition(TransactionManagementConfigUtils.TRANSACTION_ASPECT_BEAN_NAME)
                    .getRole()).isEqualTo(BeanDefinition.ROLE_INFRASTRUCTURE);
            assertThat(context.getBeanFactory()
                    .getBeanDefinition(TaskManagementConfigUtils.ASYNC_EXECUTION_ASPECT_BEAN_NAME)
                    .getRole()).isEqualTo(BeanDefinition.ROLE_INFRASTRUCTURE);
        }
    }

    @Test
    void transactionAspectAcceptsPublicTransactionConfiguration() {
        AnnotationTransactionAspect aspect = AnnotationTransactionAspect.aspectOf();
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        Properties attributes = new Properties();
        attributes.setProperty("save*", "PROPAGATION_REQUIRES_NEW,readOnly,timeout_7");

        aspect.setTransactionManager(transactionManager);
        aspect.setTransactionAttributes(attributes);
        aspect.afterPropertiesSet();

        assertThat(aspect.getTransactionManager()).isSameAs(transactionManager);
        assertThat(aspect.getTransactionAttributeSource()).isNotNull();
    }

    @Test
    void cacheAspectAcceptsPublicCachingConfiguration() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager("books");
        AnnotationCacheAspect aspect = AnnotationCacheAspect.aspectOf();
        aspect.setCacheManager(cacheManager);
        aspect.afterPropertiesSet();
        Cache cache = cacheManager.getCache("books");

        assertThat(aspect.getCacheOperationSource()).isNotNull();
        assertThat(aspect.getCacheResolver()).isNotNull();
        assertThat(aspect.getKeyGenerator()).isNotNull();
        assertThat(cache).isNotNull();
        cache.put("978-0134685991", "Effective Java");
        assertThat(cache.get("978-0134685991", String.class)).isEqualTo("Effective Java");
    }

    @Test
    void asyncAspectAcceptsBoundedExecutorConfiguration() {
        AnnotationAsyncExecutionAspect aspect = AnnotationAsyncExecutionAspect.aspectOf();
        SyncTaskExecutor executor = new SyncTaskExecutor();
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

        aspect.configure(() -> executor, () -> (throwable, method, parameters) -> {
            throw new AssertionError("Unexpected asynchronous failure", throwable);
        });
        aspect.setBeanFactory(beanFactory);
        aspect.setExecutor(executor);

        assertThat(AnnotationAsyncExecutionAspect.hasAspect()).isTrue();
    }

    @Test
    void genericInterfaceDrivenAspectConfiguresConfigurableObjects() {
        InterfaceDrivenConfigurableService service = new InterfaceDrivenConfigurableService();
        InterfaceDrivenConfigurerAspect aspect = new InterfaceDrivenConfigurerAspect("configured through interface aspect");

        aspect.configureBean(service);

        assertThat(service.getMessage()).isEqualTo("configured through interface aspect");
        assertThat(aspect.wasInvoked()).isTrue();
    }

    @EnableSpringConfigured
    @Configuration(proxyBeanMethods = false)
    static class SpringConfiguredTestConfiguration {
    }

    @EnableAsync(mode = AdviceMode.ASPECTJ)
    @EnableCaching(mode = AdviceMode.ASPECTJ)
    @EnableTransactionManagement(mode = AdviceMode.ASPECTJ)
    @Configuration(proxyBeanMethods = false)
    static class AspectJInfrastructureConfiguration {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("books");
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }

        @Bean
        Executor taskExecutor() {
            return new SyncTaskExecutor();
        }
    }

    @Configurable("configurableService")
    public static class ConfigurableService {
        private String message;

        public String getMessage() {
            return this.message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    @Configurable(autowire = Autowire.BY_TYPE)
    public static class AutowiredConfigurableService {
        private MessageProvider messageProvider;

        public MessageProvider getMessageProvider() {
            return this.messageProvider;
        }

        public void setMessageProvider(MessageProvider messageProvider) {
            this.messageProvider = messageProvider;
        }

        public String getMessage() {
            return this.messageProvider.getMessage();
        }
    }

    static class MessageProvider {
        private final String message;

        MessageProvider(String message) {
            this.message = message;
        }

        String getMessage() {
            return this.message;
        }
    }

    static class InterfaceDrivenConfigurableService implements ConfigurableObject {
        private String message;

        String getMessage() {
            return this.message;
        }

        void setMessage(String message) {
            this.message = message;
        }
    }

    static class InterfaceDrivenConfigurerAspect
            extends GenericInterfaceDrivenDependencyInjectionAspect<InterfaceDrivenConfigurableService> {
        private final String message;

        private boolean invoked;

        InterfaceDrivenConfigurerAspect(String message) {
            this.message = message;
        }

        boolean wasInvoked() {
            return this.invoked;
        }

        @Override
        protected void configure(InterfaceDrivenConfigurableService service) {
            this.invoked = true;
            service.setMessage(this.message);
        }
    }

    static class RecordingTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
