/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.stream.binder.BinderType;
import org.springframework.cloud.stream.binder.BinderTypeRegistry;
import org.springframework.cloud.stream.config.BinderFactoryAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;

public class BinderFactoryAutoConfigurationTest {

    @Test
    void binderTypeRegistryLoadsBinderTypesFromSpringBindersResources() {
        BinderFactoryAutoConfiguration autoConfiguration = new BinderFactoryAutoConfiguration();
        try (GenericApplicationContext applicationContext = new GenericApplicationContext()) {
            BinderTypeRegistry registry = autoConfiguration.binderTypeRegistry(applicationContext);

            BinderType binderType = registry.get("coverage");
            assertNotNull(binderType);
            assertEquals("coverage", binderType.getDefaultName());
            assertArrayEquals(new Class<?>[] {TestBinderConfiguration.class },
                    binderType.getConfigurationClasses());
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class TestBinderConfiguration {
    }
}
