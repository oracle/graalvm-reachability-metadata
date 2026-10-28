/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    @Configuration(proxyBeanMethods = false)
    public static class ConditionalConfiguration {
        @Bean
        @ConditionalOnClass(name = "com.fasterxml.jackson.databind.ObjectMapper")
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
        @ConditionalOnMissingClass("com.fasterxml.jackson.databind.ObjectMapper")
        String unneededFallback() {
            return "no json";
        }
    }
}
