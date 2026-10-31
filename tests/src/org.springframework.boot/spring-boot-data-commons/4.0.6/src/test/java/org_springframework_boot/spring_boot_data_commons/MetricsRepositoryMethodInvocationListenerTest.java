/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_data_commons;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.boot.data.metrics.AutoTimer;
import org.springframework.boot.data.metrics.DefaultRepositoryTagsProvider;
import org.springframework.boot.data.metrics.MetricsRepositoryMethodInvocationListener;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.RepositoryMethodInvocationListener.RepositoryMethodInvocation;
import org.springframework.data.repository.core.support.RepositoryMethodInvocationListener.RepositoryMethodInvocationResult;
import org.springframework.data.repository.core.support.RepositoryMethodInvocationListener.RepositoryMethodInvocationResult.State;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MetricsRepositoryMethodInvocationListenerTest {

    @Test
    void recordsDurationAndSuccessTagsAndObtainsRegistryOnlyOnce() throws NoSuchMethodException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            AtomicInteger registryRequests = new AtomicInteger();
            MetricsRepositoryMethodInvocationListener listener = new MetricsRepositoryMethodInvocationListener(() -> {
                registryRequests.incrementAndGet();
                return registry;
            }, new DefaultRepositoryTagsProvider(), "repository.calls", AutoTimer.ENABLED);

            listener.afterInvocation(invocation(BookRepository.class, "count", null));
            listener.afterInvocation(invocation(BookRepository.class, "count", null));

            Timer timer = registry.get("repository.calls").tags("repository", "BookRepository", "method", "count",
                    "state", "SUCCESS", "exception", "None").timer();
            assertEquals(2, timer.count());
            assertEquals(50.0, timer.totalTime(TimeUnit.MILLISECONDS), 0.001);
            assertEquals("Duration of repository invocations", timer.getId().getDescription());
            assertEquals(1, registryRequests.get());
        } finally {
            registry.close();
        }
    }

    @Test
    void recordsErrorTypeAndErrorState() throws NoSuchMethodException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            MetricsRepositoryMethodInvocationListener listener = listener(registry, AutoTimer.ENABLED);
            listener.afterInvocation(invocation(BookRepository.class, "count", new IllegalArgumentException("invalid query")));

            Timer timer = registry.get("repository.calls").tags("repository", "BookRepository", "method", "count",
                    "state", "ERROR", "exception", "IllegalArgumentException").timer();
            assertEquals(1, timer.count());
            assertEquals(25.0, timer.totalTime(TimeUnit.MILLISECONDS), 0.001);
        } finally {
            registry.close();
        }
    }

    @Test
    void methodTimerOverridesTypeTimerEvenWhenAutoTimingIsDisabled() throws NoSuchMethodException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            MetricsRepositoryMethodInvocationListener listener = listener(registry, AutoTimer.DISABLED);
            listener.afterInvocation(invocation(TimedBookRepository.class, "count", null));

            assertEquals(1, registry.get("repository.lookup").tag("operation", "lookup").timer().count());
            assertNull(registry.find("repository.type").timer());
            assertNull(registry.find("repository.calls").timer());
        } finally {
            registry.close();
        }
    }

    @Test
    void usesTypeTimerForUnannotatedMethodAndReusesAnnotationLookup() throws NoSuchMethodException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            MetricsRepositoryMethodInvocationListener listener = listener(registry, null);
            listener.afterInvocation(invocation(TimedBookRepository.class, "size", null));
            listener.afterInvocation(invocation(TimedBookRepository.class, "size", null));

            Timer timer = registry.get("repository.type").tag("operation", "type").timer();
            assertEquals(2, timer.count());
            assertEquals(50.0, timer.totalTime(TimeUnit.MILLISECONDS), 0.001);
        } finally {
            registry.close();
        }
    }

    @Test
    void doesNotRecordUnannotatedInvocationWhenAutoTimingIsAbsent() throws NoSuchMethodException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            listener(registry, null).afterInvocation(invocation(BookRepository.class, "count", null));
            assertTrue(registry.getMeters().isEmpty());
        } finally {
            registry.close();
        }
    }

    private static MetricsRepositoryMethodInvocationListener listener(SimpleMeterRegistry registry, AutoTimer autoTimer) {
        return new MetricsRepositoryMethodInvocationListener(() -> registry, new DefaultRepositoryTagsProvider(),
                "repository.calls", autoTimer);
    }

    private static RepositoryMethodInvocation invocation(Class<?> repository, String method, Throwable error)
            throws NoSuchMethodException {
        RepositoryMethodInvocationResult result = new RepositoryMethodInvocationResult() {
            @Override
            public State getState() {
                return error == null ? State.SUCCESS : State.ERROR;
            }

            @Override
            public Throwable getError() {
                return error;
            }
        };
        return new RepositoryMethodInvocation(repository, repository.getMethod(method), result, TimeUnit.MILLISECONDS.toNanos(25));
    }

    public interface BookRepository extends Repository<String, Long> {
        long count();
    }

    @Timed(value = "repository.type", extraTags = {"operation", "type"})
    public interface TimedBookRepository extends Repository<String, Long> {
        @Timed(value = "repository.lookup", extraTags = {"operation", "lookup"})
        long count();

        long size();
    }
}
