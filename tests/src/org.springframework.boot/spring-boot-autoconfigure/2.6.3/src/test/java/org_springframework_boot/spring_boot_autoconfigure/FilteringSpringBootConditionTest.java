/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.type.AnnotationMetadata;

import static org.assertj.core.api.Assertions.assertThat;

public class FilteringSpringBootConditionTest {

    @Test
    void conditionalOnClassResolvesWithContextClassLoader() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                PresentClassConfiguration.class)) {
            assertThat(context.containsBean("presentClassBean")).isTrue();
            assertThat(context.getBean(String.class)).isEqualTo("present");
        }
    }

    @Test
    void conditionalOnBeanTypeResolvesWithNullConditionClassLoader() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("availableString", "available");
        ConditionContext context = new NullClassLoaderConditionContext(beanFactory);
        SpringBootCondition condition = loadOnBeanCondition();
        AnnotationMetadata metadata = AnnotationMetadata.introspect(ConditionalOnBeanConfiguration.class);

        ConditionOutcome outcome = condition.getMatchOutcome(context, metadata);

        assertThat(outcome.isMatch()).isTrue();
    }

    private SpringBootCondition loadOnBeanCondition() {
        List<AutoConfigurationImportFilter> filters = SpringFactoriesLoader.loadFactories(
                AutoConfigurationImportFilter.class, getClass().getClassLoader());
        return filters.stream()
                .filter((filter) -> filter.getClass().getName()
                        .equals("org.springframework.boot.autoconfigure.condition.OnBeanCondition"))
                .map(SpringBootCondition.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("OnBeanCondition was not available"));
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "java.lang.String")
    static class PresentClassConfiguration {

        @Bean
        String presentClassBean() {
            return "present";
        }

    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(type = "java.lang.String")
    static class ConditionalOnBeanConfiguration {

        @Bean
        Integer conditionalIntegerBean() {
            return 42;
        }

    }

    private static final class NullClassLoaderConditionContext implements ConditionContext {

        private final DefaultListableBeanFactory beanFactory;

        private final Environment environment = new StandardEnvironment();

        private final ResourceLoader resourceLoader = new NullClassLoaderResourceLoader();

        private NullClassLoaderConditionContext(DefaultListableBeanFactory beanFactory) {
            this.beanFactory = beanFactory;
        }

        @Override
        public BeanDefinitionRegistry getRegistry() {
            return this.beanFactory;
        }

        @Override
        public DefaultListableBeanFactory getBeanFactory() {
            return this.beanFactory;
        }

        @Override
        public Environment getEnvironment() {
            return this.environment;
        }

        @Override
        public ResourceLoader getResourceLoader() {
            return this.resourceLoader;
        }

        @Override
        public ClassLoader getClassLoader() {
            return null;
        }

    }

    private static final class NullClassLoaderResourceLoader implements ResourceLoader {

        private final ResourceLoader delegate = new DefaultResourceLoader();

        @Override
        public Resource getResource(String location) {
            return this.delegate.getResource(location);
        }

        @Override
        public ClassLoader getClassLoader() {
            return null;
        }

    }

}
