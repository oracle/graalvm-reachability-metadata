/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ResourceConditionTest {
    @Test
    void resourceConditionEnablesTheBeanThatConsumesTheAvailableResource() {
        new ApplicationContextRunner().withUserConfiguration(Features.class).run(context -> {
            assertEquals("orders enabled", context.getBean("resourceFeature"));
            assertFalse(context.containsBean("missingResourceFeature"));
        });
    }

    @Configuration(proxyBeanMethods = false)
    public static class Features {
        @Bean
        @ConditionalOnResource(resources = "classpath:features/orders.txt")
        public String resourceFeature(ResourceLoader loader) throws IOException {
            return loader.getResource("classpath:features/orders.txt")
                    .getContentAsString(StandardCharsets.UTF_8).strip();
        }

        @Bean
        @ConditionalOnResource(resources = "classpath:features/absent.txt")
        public String missingResourceFeature() {
            return "optional";
        }
    }
}
