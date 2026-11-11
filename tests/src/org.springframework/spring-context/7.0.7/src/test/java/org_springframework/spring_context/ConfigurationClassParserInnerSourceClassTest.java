/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConfigurationClassParserInnerSourceClassTest {
    @Test
    void discoversNestedConfigurationWithoutExplicitRegistration() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Root.class)) {
            assertEquals("nested", context.getBean("nestedValue"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class Root {
        @Configuration(proxyBeanMethods = false)
        public static class Nested {
            @Bean
            public String nestedValue() {
                return "nested";
            }
        }
    }
}
