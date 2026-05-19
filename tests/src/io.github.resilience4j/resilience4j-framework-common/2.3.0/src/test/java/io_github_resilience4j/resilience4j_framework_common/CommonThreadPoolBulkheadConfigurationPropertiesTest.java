/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_framework_common;

import io.github.resilience4j.bulkhead.ThreadPoolBulkheadConfig;
import io.github.resilience4j.common.CompositeCustomizer;
import io.github.resilience4j.common.bulkhead.configuration.CommonThreadPoolBulkheadConfigurationProperties;
import io.github.resilience4j.common.bulkhead.configuration.ThreadPoolBulkheadConfigCustomizer;
import io.github.resilience4j.core.ContextPropagator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

public class CommonThreadPoolBulkheadConfigurationPropertiesTest {

    @Test
    void createsThreadPoolBulkheadConfigWithContextPropagatorClass() {
        CommonThreadPoolBulkheadConfigurationProperties properties =
                new CommonThreadPoolBulkheadConfigurationProperties();
        properties.getConfigs().put("shared", new CommonThreadPoolBulkheadConfigurationProperties.InstanceProperties()
                .setCoreThreadPoolSize(1)
                .setMaxThreadPoolSize(2)
                .setQueueCapacity(3)
                .setKeepAliveDuration(Duration.ofMillis(25))
                .setWritableStackTraceEnabled(false)
                .setContextPropagators(TestContextPropagator.class));
        CommonThreadPoolBulkheadConfigurationProperties.InstanceProperties instance =
                new CommonThreadPoolBulkheadConfigurationProperties.InstanceProperties()
                        .setBaseConfig("shared");

        ThreadPoolBulkheadConfig config = properties.createThreadPoolBulkheadConfig(
                instance,
                new CompositeCustomizer<>(List.of(
                        ThreadPoolBulkheadConfigCustomizer.of("orders", builder -> builder.queueCapacity(5)))),
                "orders");

        assertThat(config.getCoreThreadPoolSize()).isEqualTo(1);
        assertThat(config.getMaxThreadPoolSize()).isEqualTo(2);
        assertThat(config.getQueueCapacity()).isEqualTo(5);
        assertThat(config.getKeepAliveDuration()).isEqualTo(Duration.ofMillis(25));
        assertThat(config.isWritableStackTraceEnabled()).isFalse();
        assertThat(config.getContextPropagator())
                .hasSize(1)
                .first()
                .isInstanceOf(TestContextPropagator.class);
    }

    public static class TestContextPropagator implements ContextPropagator<String> {
        @Override
        public Supplier<Optional<String>> retrieve() {
            return Optional::empty;
        }

        @Override
        public Consumer<Optional<String>> copy() {
            return value -> value.map(String::length).orElse(0);
        }

        @Override
        public Consumer<Optional<String>> clear() {
            return value -> value.map(String::length).orElse(0);
        }
    }
}
