/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestContextManager;

public class DuplicateJsonObjectContextCustomizerFactoryInnerDuplicateJsonObjectContextCustomizerTest {
    @Test
    void customizesABootTestContextWhileCheckingJsonResources() {
        TestContextManager manager = new TestContextManager(JsonContextFixture.class);
        try (ConfigurableApplicationContext context =
                (ConfigurableApplicationContext) manager.getTestContext().getApplicationContext()) {
            assertThat(context.getBean("jsonSupport", String.class)).isEqualTo("available");
        }
    }

    @SpringBootTest(classes = JsonConfiguration.class)
    static class JsonContextFixture {}

    @Configuration(proxyBeanMethods = false)
    static class JsonConfiguration {
        @Bean
        String jsonSupport() {
            return "available";
        }
    }
}
