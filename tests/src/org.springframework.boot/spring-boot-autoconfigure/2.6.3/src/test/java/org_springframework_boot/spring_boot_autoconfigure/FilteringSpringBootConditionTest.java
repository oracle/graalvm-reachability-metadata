/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

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
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.setResourceLoader(new NullClassLoaderResourceLoader());
            context.registerBean(String.class, () -> "available");
            context.register(ConditionalOnBeanConfiguration.class);
            context.refresh();

            assertThat(context.getBean(String.class)).isEqualTo("available");
            assertThat(context.getBean(Integer.class)).isEqualTo(42);
        }
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
