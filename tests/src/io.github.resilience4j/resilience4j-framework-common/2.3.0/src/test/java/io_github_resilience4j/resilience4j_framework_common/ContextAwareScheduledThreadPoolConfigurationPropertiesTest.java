/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_framework_common;

import io.github.resilience4j.common.scheduled.threadpool.configuration.ContextAwareScheduledThreadPoolConfigurationProperties;
import io.github.resilience4j.core.ContextAwareScheduledThreadPoolExecutor;
import io.github.resilience4j.core.ContextPropagator;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ContextAwareScheduledThreadPoolConfigurationPropertiesTest {

    @Test
    void buildsExecutorWithInstantiatedContextPropagatorClass() throws Exception {
        ContextAwareScheduledThreadPoolConfigurationProperties properties =
                new ContextAwareScheduledThreadPoolConfigurationProperties();
        properties.setCorePoolSize(1);
        properties.setContextPropagators(ThreadLocalContextPropagator.class);
        ThreadLocalContextPropagator.CONTEXT.set("request-42");
        ContextAwareScheduledThreadPoolExecutor executor = properties.build();
        try {
            Callable<String> contextLookup = ThreadLocalContextPropagator.CONTEXT::get;
            ScheduledFuture<String> future = executor.schedule(contextLookup, 0, TimeUnit.MILLISECONDS);

            assertThat(executor.getCorePoolSize()).isEqualTo(1);
            assertThat(executor.getContextPropagators())
                    .hasSize(1)
                    .first()
                    .isInstanceOf(ThreadLocalContextPropagator.class);
            assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("request-42");
        } finally {
            executor.shutdownNow();
            ThreadLocalContextPropagator.CONTEXT.remove();
        }
    }

    @Test
    void rejectsNonPositiveCorePoolSize() {
        ContextAwareScheduledThreadPoolConfigurationProperties properties =
                new ContextAwareScheduledThreadPoolConfigurationProperties();

        assertThatThrownBy(() -> properties.setCorePoolSize(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("corePoolSize");
    }

    public static class ThreadLocalContextPropagator implements ContextPropagator<String> {
        private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

        @Override
        public Supplier<Optional<String>> retrieve() {
            return () -> Optional.ofNullable(CONTEXT.get());
        }

        @Override
        public Consumer<Optional<String>> copy() {
            return value -> value.ifPresentOrElse(CONTEXT::set, CONTEXT::remove);
        }

        @Override
        public Consumer<Optional<String>> clear() {
            return value -> CONTEXT.remove();
        }
    }
}
