/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class OnClassConditionTest {
    @Test
    void registersConfigurationOnlyWhenAllRequiredClassesArePresent() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(AvailableConfiguration.class, OptionalIntegrationConfiguration.class);
            context.refresh();

            assertEquals(Duration.ofSeconds(45), context.getBean("requestTimeout", Duration.class));
            assertFalse(context.containsBean("optionalIntegration"));
            assertEquals(1, context.getBeansOfType(Duration.class).size());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(value = {Duration.class, Map.class}, name = "java.util.concurrent.CompletableFuture")
    public static class AvailableConfiguration {
        @Bean
        Duration requestTimeout() {
            return Duration.ofSeconds(45);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(value = Duration.class, name = {
            "java.util.Map", "com.datastax.oss.driver.api.core.CqlSession"})
    public static class OptionalIntegrationConfiguration {
        @Bean
        Duration optionalIntegration() {
            return Duration.ofSeconds(90);
        }
    }
}
