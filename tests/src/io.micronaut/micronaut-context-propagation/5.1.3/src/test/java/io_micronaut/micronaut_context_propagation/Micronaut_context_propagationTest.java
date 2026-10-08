/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_context_propagation;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.propagation.instrument.execution.ContextPropagatingExecutorService;
import io.micronaut.context.propagation.instrument.execution.ContextPropagatingScheduledExecutorService;
import io.micronaut.context.propagation.slf4j.MdcPropagationContext;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.inject.qualifiers.Qualifiers;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

public class Micronaut_context_propagationTest {
    @Test
    void propagatesAnExplicitContextThroughExecutorServiceTasks() throws Exception {
        ExecutorService delegate = Executors.newSingleThreadExecutor();
        ContextPropagatingExecutorService instrumented = new ContextPropagatingExecutorService(
                delegate, PropagatedContext.empty().plus(new ContextValue("explicit")));
        try {
            Future<String> result = instrumented.submit(
                    () -> PropagatedContext.get().get(ContextValue.class).value());
            AtomicReference<String> runnableValue = new AtomicReference<>();
            Future<?> runnableResult = instrumented.submit(
                    () -> runnableValue.set(PropagatedContext.get().get(ContextValue.class).value()));

            assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("explicit");
            assertThat(runnableResult.get(10, TimeUnit.SECONDS)).isNull();
            assertThat(runnableValue).hasValue("explicit");
            assertThat(instrumented.getTarget()).isSameAs(delegate);
            assertThat(ContextPropagatingExecutorService.isInstrumented(instrumented)).isTrue();
            assertThat(ContextPropagatingExecutorService.unwrap(instrumented))
                    .containsSame(delegate);
            assertThat(ContextPropagatingExecutorService.isInstrumented(delegate)).isFalse();
            assertThat(ContextPropagatingExecutorService.unwrap(delegate)).isEmpty();
        } finally {
            shutdown(instrumented);
        }
    }

    @Test
    void capturesTheCurrentContextWhenAnExecutorTaskIsSubmitted() throws Exception {
        ExecutorService delegate = Executors.newSingleThreadExecutor();
        ExecutorService instrumented = new ContextPropagatingExecutorService(delegate);
        try {
            Future<String> result;
            try (PropagatedContext.Scope ignored = PropagatedContext.empty()
                    .plus(new ContextValue("current"))
                    .propagate()) {
                result = instrumented.submit(
                        () -> PropagatedContext.get().get(ContextValue.class).value());
            }

            assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("current");
            assertThat(PropagatedContext.exists()).isFalse();
        } finally {
            shutdown(instrumented);
        }
    }

    @Test
    void propagatesAnExplicitContextThroughScheduledTasks() throws Exception {
        ScheduledExecutorService delegate = Executors.newSingleThreadScheduledExecutor();
        ContextPropagatingScheduledExecutorService instrumented =
                new ContextPropagatingScheduledExecutorService(
                        delegate, PropagatedContext.empty().plus(new ContextValue("scheduled")));
        try {
            Future<String> result = instrumented.schedule(
                    () -> PropagatedContext.get().get(ContextValue.class).value(),
                    0,
                    TimeUnit.MILLISECONDS);

            assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("scheduled");
            assertThat(instrumented.getTarget()).isSameAs(delegate);
        } finally {
            shutdown(instrumented);
        }
    }

    @Test
    void propagatesContextThroughFixedRateScheduledTasks() throws Exception {
        ScheduledExecutorService delegate = Executors.newSingleThreadScheduledExecutor();
        ContextPropagatingScheduledExecutorService instrumented =
                new ContextPropagatingScheduledExecutorService(
                        delegate, PropagatedContext.empty().plus(new ContextValue("periodic")));
        CountDownLatch executions = new CountDownLatch(2);
        AtomicReference<String> propagatedValue = new AtomicReference<>();
        ScheduledFuture<?> periodicTask = null;
        try {
            periodicTask = instrumented.scheduleAtFixedRate(
                    () -> {
                        propagatedValue.set(
                                PropagatedContext.get().get(ContextValue.class).value());
                        executions.countDown();
                    },
                    0,
                    100,
                    TimeUnit.MILLISECONDS);

            assertThat(executions.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(propagatedValue).hasValue("periodic");
        } finally {
            if (periodicTask != null) {
                periodicTask.cancel(true);
            }
            shutdown(instrumented);
        }
    }

    @Test
    void instrumentsExecutorServiceBeansAndPropagatesTheirContext() throws Exception {
        try (ApplicationContext context = ApplicationContext.run(
                Map.of("micronaut.executors.context-propagation-test.type", "FIXED"))) {
            ExecutorService executor = context.getBean(
                    ExecutorService.class, Qualifiers.byName("context-propagation-test"));
            assertThat(ContextPropagatingExecutorService.isInstrumented(executor)).isTrue();

            try (PropagatedContext.Scope ignored = PropagatedContext.empty()
                    .plus(new ContextValue("application-context"))
                    .propagate()) {
                Future<String> result = executor.submit(
                        () -> PropagatedContext.get().get(ContextValue.class).value());

                assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("application-context");
            }
        }
    }

    @Test
    void propagatesAndRestoresMdcState() {
        MDC.clear();
        try {
            MDC.put("request", "outside");
            MdcPropagationContext captured = new MdcPropagationContext();
            MDC.put("request", "changed");

            try (PropagatedContext.Scope ignored = PropagatedContext.empty()
                    .plus(captured)
                    .propagate()) {
                assertThat(MDC.getCopyOfContextMap()).containsEntry("request", "outside");
            }

            assertThat(MDC.getCopyOfContextMap()).containsEntry("request", "changed");

            MdcPropagationContext explicit = new MdcPropagationContext(
                    Map.of("request", "explicit"));
            try (PropagatedContext.Scope ignored = PropagatedContext.empty()
                    .plus(explicit)
                    .propagate()) {
                assertThat(MDC.getCopyOfContextMap()).containsEntry("request", "explicit");
            }
            assertThat(MDC.getCopyOfContextMap()).containsEntry("request", "changed");
        } finally {
            MDC.clear();
        }
    }

    @Test
    void clearsMdcWhenThePropagatedStateIsEmpty() {
        MDC.clear();
        MDC.put("request", "outside");
        try {
            MdcPropagationContext empty = new MdcPropagationContext(null);

            try (PropagatedContext.Scope ignored = PropagatedContext.empty()
                    .plus(empty)
                    .propagate()) {
                assertThat(MDC.getCopyOfContextMap()).isNull();
            }

            assertThat(MDC.getCopyOfContextMap()).containsEntry("request", "outside");
        } finally {
            MDC.clear();
        }
    }

    private static void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    private record ContextValue(String value) implements PropagatedContextElement {
    }
}
