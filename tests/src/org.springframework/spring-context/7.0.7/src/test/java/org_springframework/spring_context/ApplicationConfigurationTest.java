/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Role;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org_springframework.spring_context.components.CatalogController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class ApplicationConfigurationTest {
    @Test
    void honorsScanningImportsConditionsScopesAndBeanLifecycleAnnotations() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(CatalogConfiguration.class)) {
            assertEquals("catalog=north:books", context.getBean(CatalogController.class).render());
            assertEquals("Regional catalog", context.getBean("title"));
            assertFalse(context.containsBean("disabledFeature"));
            Startup startup = context.getBean(Startup.class);
            assertEquals(List.of("warmup"), startup.events);
            assertEquals("preferred", context.getBean(Report.class).label());
            assertEquals(List.of("warmup", "report"), startup.events);
            assertNotSame(context.getBean("requestToken"), context.getBean("requestToken"));
            assertEquals(BeanDefinition.ROLE_SUPPORT, context.getBeanDefinition("title").getRole());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = CatalogController.class)
    @EnableAspectJAutoProxy
    @PropertySource("classpath:org_springframework/spring_context/catalog.properties")
    @Import(ImportedConfiguration.class)
    public static class CatalogConfiguration {
        @Bean
        public Startup startup() {
            return new Startup();
        }

        @Bean
        public String warmup(Startup startup) {
            startup.events.add("warmup");
            return "ready";
        }

        @Bean
        @Lazy
        @DependsOn("warmup")
        @Conditional(Enabled.class)
        public Report report(String label, Startup startup) {
            startup.events.add("report");
            return new Report(label);
        }

        @Bean
        @Primary
        public String preferredLabel() {
            return "preferred";
        }

        @Bean
        public String alternateLabel() {
            return "alternate";
        }

        @Bean
        @Scope("prototype")
        public Object requestToken() {
            return new Object();
        }

        @Bean
        @Conditional(Disabled.class)
        public Object disabledFeature() {
            return new Object();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ImportedConfiguration {
        @Bean
        @Role(BeanDefinition.ROLE_SUPPORT)
        public String title(Environment environment) {
            return environment.getRequiredProperty("catalog.title");
        }
    }

    public static class Enabled implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return context.getEnvironment().getProperty("catalog.enabled", Boolean.class, false);
        }
    }

    public static class Disabled implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !context.getEnvironment().getProperty("catalog.enabled", Boolean.class, false);
        }
    }

    public static class Startup {
        private final List<String> events = new ArrayList<>();
    }

    public record Report(String label) {
    }
}
