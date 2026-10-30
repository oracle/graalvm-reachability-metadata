/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_sql;

import org.junit.jupiter.api.Test;
import org.springframework.boot.sql.autoconfigure.init.ConditionalOnSqlInitialization;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ConditionalOnSqlInitializationTest {
    @Test
    void enablesInitializationWhenModeIsNotConfigured() {
        assertEnabled(Map.of());
    }

    @Test
    void enablesInitializationForAlwaysMode() {
        assertEnabled(Map.of("spring.sql.init.mode", "always"));
    }

    @Test
    void enablesInitializationForEmbeddedModeIgnoringCase() {
        assertEnabled(Map.of("spring.sql.init.mode", "EmBeDdEd"));
    }

    @Test
    void emptyModeUsesEmbeddedDefault() {
        assertEnabled(Map.of("spring.sql.init.mode", ""));
    }

    @Test
    void neverModePreventsConditionalBeanRegistration() {
        try (AnnotationConfigApplicationContext context = context(Map.of("spring.sql.init.mode", "never"))) {
            assertFalse(context.containsBean("initializationMarker"));
        }
    }

    private static void assertEnabled(Map<String, Object> properties) {
        try (AnnotationConfigApplicationContext context = context(properties)) {
            assertEquals("initialization enabled", context.getBean("initializationMarker", String.class));
        }
    }

    private static AnnotationConfigApplicationContext context(Map<String, Object> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        context.register(InitializationConfiguration.class);
        context.refresh();
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    public static class InitializationConfiguration {
        @Bean
        @ConditionalOnSqlInitialization
        public String initializationMarker() {
            return "initialization enabled";
        }
    }
}
