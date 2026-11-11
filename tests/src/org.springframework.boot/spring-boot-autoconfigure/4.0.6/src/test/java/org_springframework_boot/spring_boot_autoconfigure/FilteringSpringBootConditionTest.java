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
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.type.AnnotationMetadata;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FilteringSpringBootConditionTest {
    @Test
    void beanTypeConditionUsesTheDefaultLoaderWhenTheContextHasNoExplicitLoader() {
        ConfigurationCondition condition = SpringFactoriesLoader.loadFactories(
                AutoConfigurationImportFilter.class, getClass().getClassLoader()).stream()
                .filter(ConfigurationCondition.class::isInstance)
                .map(ConfigurationCondition.class::cast)
                .filter(candidate -> candidate.getConfigurationPhase()
                        == ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN)
                .findFirst().orElseThrow();
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        ConditionContext context = new DefaultLoaderConditionContext(beanFactory);
        AnnotationMetadata metadata = AnnotationMetadata.introspect(RequiresBuilder.class);
        try {
            assertFalse(condition.matches(context, metadata));
            beanFactory.registerSingleton("orders", new StringBuilder("orders"));
            assertTrue(condition.matches(context, metadata));
            beanFactory.destroySingleton("orders");
            assertFalse(condition.matches(context, metadata));
        } finally {
            beanFactory.destroySingletons();
        }
    }

    @ConditionalOnBean(type = "java.lang.StringBuilder")
    public static class RequiresBuilder {
    }

    private record DefaultLoaderConditionContext(DefaultListableBeanFactory beanFactory)
            implements ConditionContext {
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
}
