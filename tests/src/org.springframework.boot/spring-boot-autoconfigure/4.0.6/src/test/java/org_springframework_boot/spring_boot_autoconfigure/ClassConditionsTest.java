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
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ClassConditionsTest {
    @Test
    void classpathConditionsSelectAvailableAndFallbackImplementations() {
        new ApplicationContextRunner().withUserConfiguration(Features.class).run(context -> {
            assertEquals("jdk", context.getBean("available"));
            assertEquals("fallback", context.getBean("fallback"));
            assertFalse(context.containsBean("unavailable"));
            assertFalse(context.containsBean("unnecessaryFallback"));
        });
    }

    @Configuration(proxyBeanMethods = false)
    public static class Features {
        @Bean
        @ConditionalOnClass(name = "java.time.LocalDate")
        public String available() {
            return "jdk";
        }

        @Bean
        @ConditionalOnMissingClass("example.optional.AbsentTransport")
        public String fallback() {
            return "fallback";
        }

        @Bean
        @ConditionalOnClass(name = "example.optional.AbsentTransport")
        public String unavailable() {
            return "optional";
        }

        @Bean
        @ConditionalOnMissingClass("java.time.LocalDate")
        public String unnecessaryFallback() {
            return "legacy";
        }
    }
}
