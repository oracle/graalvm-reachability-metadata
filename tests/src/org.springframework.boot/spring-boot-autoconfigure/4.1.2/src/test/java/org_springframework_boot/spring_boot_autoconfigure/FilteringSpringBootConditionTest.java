/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.type.AnnotationMetadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FilteringSpringBootConditionTest {
    @Test
    void enablesBeansOnlyWhenTheirClassConditionsMatch() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(ConditionalConfiguration.class);
            context.refresh();
            assertEquals("json available", context.getBean("jsonSupport"));
            assertEquals("optional integration absent", context.getBean("fallback"));
            assertFalse(context.containsBean("unavailableIntegration"));
            assertFalse(context.containsBean("unneededFallback"));
        }
    }

    @Test
    void evaluatesBeanTypeConditionsWithoutAnExplicitClassLoader() {
        SpringBootCondition condition = SpringFactoriesLoader.forDefaultResourceLocation(getClass().getClassLoader())
                .load(AutoConfigurationImportFilter.class)
                .stream()
                .filter(filter -> filter.getClass().getName().equals(
                        "org.springframework.boot.autoconfigure.condition.OnBeanCondition"))
                .map(SpringBootCondition.class::cast)
                .findFirst()
                .orElseThrow();
        AnnotationMetadata metadata = AnnotationMetadata.introspect(RequiresTextBean.class);
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        ConditionContext context = new DefaultLoaderConditionContext(beanFactory);
        assertFalse(condition.matches(context, metadata));
        beanFactory.registerSingleton("text", "available");
        assertTrue(condition.matches(context, metadata));
        beanFactory.destroySingletons();
        assertFalse(condition.matches(context, metadata));
    }

    @ConditionalOnBean(type = "java.lang.CharSequence")
    public static class RequiresTextBean {
    }

    private record DefaultLoaderConditionContext(DefaultListableBeanFactory beanFactory) implements ConditionContext {
        @Override
        public BeanDefinitionRegistry getRegistry() {
            return beanFactory;
        }

        @Override
        public ConfigurableListableBeanFactory getBeanFactory() {
            return beanFactory;
        }

        @Override
        public Environment getEnvironment() {
            return new StandardEnvironment();
        }

        @Override
        public ResourceLoader getResourceLoader() {
            return new DefaultResourceLoader();
        }

        @Override
        public ClassLoader getClassLoader() {
            return null;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ConditionalConfiguration {
        @Bean
        @ConditionalOnClass(name = "tools.jackson.databind.ObjectMapper")
        String jsonSupport() {
            return "json available";
        }

        @Bean
        @ConditionalOnMissingClass("com.datastax.oss.driver.api.core.CqlSession")
        String fallback() {
            return "optional integration absent";
        }

        @Bean
        @ConditionalOnClass(name = "com.datastax.oss.driver.api.core.CqlSession")
        String unavailableIntegration() {
            return "cassandra";
        }

        @Bean
        @ConditionalOnMissingClass("tools.jackson.databind.ObjectMapper")
        String unneededFallback() {
            return "no json";
        }
    }
}
