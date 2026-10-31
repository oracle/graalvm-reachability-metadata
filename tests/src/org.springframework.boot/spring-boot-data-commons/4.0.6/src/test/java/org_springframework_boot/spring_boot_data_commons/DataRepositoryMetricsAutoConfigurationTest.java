/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_data_commons;

import java.util.Map;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.boot.data.autoconfigure.metrics.DataMetricsProperties;
import org.springframework.boot.data.autoconfigure.metrics.DataRepositoryMetricsAutoConfiguration;
import org.springframework.boot.data.metrics.DefaultRepositoryTagsProvider;
import org.springframework.boot.data.metrics.MetricsRepositoryMethodInvocationListener;
import org.springframework.boot.data.metrics.RepositoryTagsProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DataRepositoryMetricsAutoConfigurationTest {

    @Test
    void configuresListenerAndBindsApplicationSettingsWhenRegistryIsAvailable() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "management.metrics.data.repository.metric-name", "books.calls",
                    "management.metrics.data.repository.autotime.enabled", "false")));
            context.registerBean(SimpleMeterRegistry.class, () -> registry);
            context.register(DataRepositoryMetricsAutoConfiguration.class);
            context.refresh();

            assertNotNull(context.getBean(MetricsRepositoryMethodInvocationListener.class));
            assertInstanceOf(DefaultRepositoryTagsProvider.class, context.getBean(RepositoryTagsProvider.class));
            DataMetricsProperties properties = context.getBean(DataMetricsProperties.class);
            assertEquals("books.calls", properties.getRepository().getMetricName());
            assertFalse(properties.getRepository().getAutotime().isEnabled());
        } finally {
            registry.close();
        }
    }

    @Test
    void preservesApplicationSuppliedTagsProvider() {
        RepositoryTagsProvider tagsProvider = invocation -> Tags.of("application", "catalog");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(SimpleMeterRegistry.class, () -> registry);
            context.registerBean(RepositoryTagsProvider.class, () -> tagsProvider);
            context.register(DataRepositoryMetricsAutoConfiguration.class);
            context.refresh();

            assertSame(tagsProvider, context.getBean(RepositoryTagsProvider.class));
            assertEquals(1, context.getBeansOfType(RepositoryTagsProvider.class).size());
            assertNotNull(context.getBean(MetricsRepositoryMethodInvocationListener.class));
        } finally {
            registry.close();
        }
    }

    @Test
    void doesNotConfigureRepositoryMetricsWithoutRegistry() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(DataRepositoryMetricsAutoConfiguration.class);
            context.refresh();

            assertTrue(context.getBeansOfType(MetricsRepositoryMethodInvocationListener.class).isEmpty());
            assertTrue(context.getBeansOfType(RepositoryTagsProvider.class).isEmpty());
        }
    }
}
