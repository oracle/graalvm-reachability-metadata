/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.grpc.Context;
import io.grpc.Deadline;

class Grpc_contextTest {

    @Test
    void valuesAreAvailableInAttachedContextsAndForkKeepsValuesWithoutCancellation() {
        Context.Key<String> tenantKey = Context.keyWithDefault("tenant", "default-tenant");
        Context.Key<String> requestKey = Context.key("request-id");
        Context.Key<String> userKey = Context.key("user");
        Context.Key<String> traceKey = Context.key("trace");

        Context baseContext = Context.ROOT.withValues(requestKey, "req-1", userKey, "alice", traceKey, "trace-1")
                .withValue(tenantKey, "team-a");

        assertThat(tenantKey.get(Context.ROOT)).isEqualTo("default-tenant");
        assertThat(requestKey.get(Context.ROOT)).isNull();
        assertThat(requestKey.get(baseContext)).isEqualTo("req-1");
        assertThat(userKey.get(baseContext)).isEqualTo("alice");
        assertThat(traceKey.get(baseContext)).isEqualTo("trace-1");
        assertThat(tenantKey.toString()).isEqualTo("tenant");

        Context previous = baseContext.attach();
        try {
            assertThat(Context.current()).isSameAs(baseContext);
            assertThat(requestKey.get()).isEqualTo("req-1");
            assertThat(userKey.get()).isEqualTo("alice");
            assertThat(tenantKey.get()).isEqualTo("team-a");

            Context nested = Context.current().withValue(userKey, "bob");
            Context nestedPrevious = nested.attach();
            try {
                assertThat(Context.current()).isSameAs(nested);
                assertThat(userKey.get()).isEqualTo("bob");
                assertThat(requestKey.get()).isEqualTo("req-1");
            } finally {
                nested.detach(nestedPrevious);
            }

            assertThat(Context.current()).isSameAs(baseContext);
            assertThat(userKey.get()).isEqualTo("alice");
        } finally {
            baseContext.detach(previous);
        }

        assertThat(Context.current()).isSameAs(Context.ROOT);
        assertThat(tenantKey.get()).isEqualTo("default-tenant");

        Context.CancellableContext cancellable = baseContext.withCancellation();
        Context forked = cancellable.fork();
        RuntimeException cause = new RuntimeException("cancelled");

        assertThat(requestKey.get(forked)).isEqualTo("req-1");
        assertThat(cancellable.cancel(cause)).isTrue();
        assertThat(cancellable.isCancelled()).isTrue();
        assertThat(forked.isCancelled()).isFalse();
        assertThat(forked.cancellationCause()).isNull();
    }

    @Test
    void runCallWrapAndExecutorsPropagateContextWithoutLeakingToWorkerThreads() throws Exception {
        Context.Key<String> roleKey = Context.key("role");
        Context context = Context.ROOT.withValue(roleKey, "admin");

        AtomicReference<String> valueSeenByRun = new AtomicReference<>();
        AtomicReference<Context> currentSeenByRun = new AtomicReference<>();
        context.run(() -> {
            valueSeenByRun.set(roleKey.get());
            currentSeenByRun.set(Context.current());
        });

        assertThat(valueSeenByRun.get()).isEqualTo("admin");
        assertThat(currentSeenByRun.get()).isSameAs(context);
        assertThat(Context.current()).isSameAs(Context.ROOT);
        String callResult = context.call(() -> roleKey.get());
        assertThat(callResult).isEqualTo("admin");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            assertThat(executor.submit(context.wrap(() -> roleKey.get())).get(5, TimeUnit.SECONDS)).isEqualTo("admin");

            AtomicReference<String> wrappedRunnableValue = new AtomicReference<>();
            AtomicReference<Context> wrappedRunnableCurrent = new AtomicReference<>();
            executor.submit(context.wrap(() -> {
                wrappedRunnableValue.set(roleKey.get());
                wrappedRunnableCurrent.set(Context.current());
            })).get(5, TimeUnit.SECONDS);

            assertThat(wrappedRunnableValue.get()).isEqualTo("admin");
            assertThat(wrappedRunnableCurrent.get()).isSameAs(context);

            CountDownLatch fixedExecutorLatch = new CountDownLatch(1);
            AtomicReference<String> fixedExecutorValue = new AtomicReference<>();
            AtomicReference<Context> fixedExecutorCurrent = new AtomicReference<>();
            context.fixedContextExecutor(executor).execute(() -> {
                fixedExecutorValue.set(roleKey.get());
                fixedExecutorCurrent.set(Context.current());
                fixedExecutorLatch.countDown();
            });

            assertThat(fixedExecutorLatch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(fixedExecutorValue.get()).isEqualTo("admin");
            assertThat(fixedExecutorCurrent.get()).isSameAs(context);

            CountDownLatch currentExecutorLatch = new CountDownLatch(1);
            AtomicReference<String> currentExecutorValue = new AtomicReference<>();
            Context invokingContext = Context.ROOT.withValue(roleKey, "captured-at-execute");
            Context previous = invokingContext.attach();
            try {
                Context.currentContextExecutor(executor).execute(() -> {
                    currentExecutorValue.set(roleKey.get());
                    currentExecutorLatch.countDown();
                });
            } finally {
                invokingContext.detach(previous);
            }

            assertThat(currentExecutorLatch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(currentExecutorValue.get()).isEqualTo("captured-at-execute");
            assertThat(executor.submit(() -> roleKey.get()).get(5, TimeUnit.SECONDS)).isNull();
            assertThat(executor.submit(Context::current).get(5, TimeUnit.SECONDS)).isSameAs(Context.ROOT);
        } finally {
            shutdown(executor);
        }
    }

    @Test
    void cancellationListenersSeeParentCancellationAndDetachAndCancelRestoresPreviousContext() throws Exception {
        Context.CancellableContext parent = Context.ROOT.withCancellation();
        Context child = parent.withValue(Context.key("request"), "req-7");

        CountDownLatch listenerLatch = new CountDownLatch(1);
        AtomicReference<Context> notifiedContext = new AtomicReference<>();
        AtomicInteger removedListenerCalls = new AtomicInteger();

        child.addListener(context -> {
            notifiedContext.set(context);
            listenerLatch.countDown();
        }, Runnable::run);
        Context.CancellationListener removedListener = context -> removedListenerCalls.incrementAndGet();
        child.addListener(removedListener, Runnable::run);
        child.removeListener(removedListener);

        IllegalStateException cause = new IllegalStateException("boom");

        assertThat(parent.cancel(cause)).isTrue();
        assertThat(parent.cancel(new RuntimeException("ignored"))).isFalse();
        assertThat(listenerLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(child.isCancelled()).isTrue();
        assertThat(child.cancellationCause()).isSameAs(cause);
        assertThat(notifiedContext.get()).isSameAs(child);
        assertThat(removedListenerCalls.get()).isZero();

        Context.CancellableContext attached = Context.ROOT.withCancellation();
        Context previous = attached.attach();
        IllegalArgumentException detachedCause = new IllegalArgumentException("done");
        attached.detachAndCancel(previous, detachedCause);

        assertThat(Context.current()).isSameAs(Context.ROOT);
        assertThat(attached.isCancelled()).isTrue();
        assertThat(attached.cancellationCause()).isSameAs(detachedCause);
    }

    @Test
    void deadlinesSupportOrderingAndAutomaticallyCancelContexts() throws Exception {
        MutableTicker ticker = new MutableTicker();
        Deadline base = Deadline.after(5, TimeUnit.SECONDS, ticker);
        Deadline later = base.offset(2, TimeUnit.SECONDS);
        Deadline earlier = later.offset(-3, TimeUnit.SECONDS);

        assertThat(base.timeRemaining(TimeUnit.SECONDS)).isEqualTo(5);
        assertThat(base.isBefore(later)).isTrue();
        assertThat(later.minimum(base)).isSameAs(base);
        assertThat(earlier.compareTo(base)).isLessThan(0);
        assertThat(base.offset(0, TimeUnit.SECONDS)).isSameAs(base);
        assertThat(base.toString()).contains("from now").contains("ticker=");

        ticker.advance(6, TimeUnit.SECONDS);
        assertThat(base.isExpired()).isTrue();
        assertThat(base.timeRemaining(TimeUnit.SECONDS)).isNegative();

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            CountDownLatch deadlineLatch = new CountDownLatch(1);
            Deadline expiringDeadline = Deadline.after(50, TimeUnit.MILLISECONDS);
            expiringDeadline.runOnExpiration(deadlineLatch::countDown, scheduler);
            assertThat(deadlineLatch.await(5, TimeUnit.SECONDS)).isTrue();

            Context.CancellableContext parent = Context.ROOT.withDeadlineAfter(50, TimeUnit.MILLISECONDS, scheduler);
            Context.CancellableContext child = parent.withDeadlineAfter(500, TimeUnit.MILLISECONDS, scheduler);
            CountDownLatch childCancelledLatch = new CountDownLatch(1);
            child.addListener(context -> childCancelledLatch.countDown(), Runnable::run);

            assertThat(child.getDeadline()).isEqualTo(parent.getDeadline());
            assertThat(childCancelledLatch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(parent.isCancelled()).isTrue();
            assertThat(child.isCancelled()).isTrue();
            assertThat(child.cancellationCause()).isInstanceOf(TimeoutException.class).hasMessage("context timed out");
        } finally {
            shutdown(scheduler);
        }
    }

    @Test
    void shorterChildDeadlineCancelsOnlyChildAndKeepsParentActive() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        Context.CancellableContext parent = null;
        try {
            parent = Context.ROOT.withDeadlineAfter(500, TimeUnit.MILLISECONDS, scheduler);
            Context.CancellableContext child = parent.withDeadlineAfter(50, TimeUnit.MILLISECONDS, scheduler);
            CountDownLatch childCancelledLatch = new CountDownLatch(1);
            AtomicReference<Context> notifiedContext = new AtomicReference<>();

            child.addListener(context -> {
                notifiedContext.set(context);
                childCancelledLatch.countDown();
            }, Runnable::run);

            assertThat(child.getDeadline().isBefore(parent.getDeadline())).isTrue();
            assertThat(childCancelledLatch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(notifiedContext.get()).isSameAs(child);
            assertThat(child.isCancelled()).isTrue();
            assertThat(child.cancellationCause()).isInstanceOf(TimeoutException.class).hasMessage("context timed out");
            assertThat(parent.isCancelled()).isFalse();
            assertThat(parent.cancellationCause()).isNull();
        } finally {
            if (parent != null) {
                parent.cancel(null);
            }
            shutdown(scheduler);
        }
    }

    @Test
    void expiredDeadlinesCancelContextsImmediatelyAndNotifyLateListeners() throws Exception {
        MutableTicker ticker = new MutableTicker();
        Deadline expiredDeadline = Deadline.after(5, TimeUnit.SECONDS, ticker);
        ticker.advance(6, TimeUnit.SECONDS);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            Context.CancellableContext expiredContext = Context.ROOT.withDeadline(expiredDeadline, scheduler);
            CountDownLatch lateListenerLatch = new CountDownLatch(1);
            AtomicReference<Context> notifiedContext = new AtomicReference<>();

            expiredContext.addListener(context -> {
                notifiedContext.set(context);
                lateListenerLatch.countDown();
            }, Runnable::run);

            assertThat(expiredContext.getDeadline()).isSameAs(expiredDeadline);
            assertThat(expiredContext.isCancelled()).isTrue();
            assertThat(expiredContext.cancellationCause()).isInstanceOf(TimeoutException.class).hasMessage("context timed out");
            assertThat(lateListenerLatch.getCount()).isZero();
            assertThat(notifiedContext.get()).isSameAs(expiredContext);
        } finally {
            shutdown(scheduler);
        }
    }

    private static void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    private static final class MutableTicker extends Deadline.Ticker {
        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long nanoTime() {
            return nanos.get();
        }

        void advance(long time, TimeUnit unit) {
            nanos.addAndGet(unit.toNanos(time));
        }
    }
}
