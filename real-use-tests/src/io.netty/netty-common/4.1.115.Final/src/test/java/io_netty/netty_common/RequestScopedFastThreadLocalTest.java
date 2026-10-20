/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the fast thread-local storage subsystem of netty-common: `FastThreadLocal` values with
 * lazy `initialValue` and `onRemoval` callbacks, the `FastThreadLocalThread` workers that
 * `DefaultThreadFactory` creates, and the automatic `removeAll` cleanup those workers run on exit.
 *
 * A multi-tenant request pipeline keeps the current tenant's request context and a reusable
 * scratch buffer in fast thread-locals. Each worker lazily creates its own context, closes it
 * when a request finishes, reuses its buffer across requests, and any context a request forgot
 * to clear is closed by the factory's worker cleanup once the thread exits. The same pipeline
 * also runs on a plain JDK thread, where the values fall back to ordinary thread-local storage
 * and must be cleared explicitly.
 */
class RequestScopedFastThreadLocalTest {

    private static final long WAIT_SECONDS = 10;

    private final RequestPipeline pipeline = new RequestPipeline();

    @Test
    void factoryWorkersAreFastThreadLocalThreadsThatCleanUpOnExit() throws Exception {
        DefaultThreadFactory factory = new DefaultThreadFactory("tenant-worker", true, Thread.NORM_PRIORITY);
        AtomicReference<Boolean> isFastThread = new AtomicReference<>();
        AtomicReference<Boolean> willCleanup = new AtomicReference<>();

        Thread worker = factory.newThread(() -> {
            isFastThread.set(Thread.currentThread() instanceof FastThreadLocalThread);
            willCleanup.set(FastThreadLocalThread.willCleanupFastThreadLocals(Thread.currentThread()));
        });
        worker.start();
        worker.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));

        assertThat(worker.isAlive()).isFalse();
        assertThat(worker).isInstanceOf(FastThreadLocalThread.class);
        assertThat(worker.getName()).matches("tenant-worker-\\d+-1");
        assertThat(worker.isDaemon()).isTrue();
        assertThat(worker.getPriority()).isEqualTo(Thread.NORM_PRIORITY);
        assertThat(isFastThread.get()).isTrue();
        assertThat(willCleanup.get()).isTrue();
    }

    @Test
    void eachWorkerLazilyCreatesItsOwnContextAndClosesItPerRequest() throws Exception {
        DefaultThreadFactory factory = new DefaultThreadFactory("tenant-worker", true);
        List<String> handledByAcme = Collections.synchronizedList(new ArrayList<>());
        List<String> handledByGlobex = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch bothInitialized = new CountDownLatch(2);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread acme = factory.newThread(guarded(failure, () -> {
            assertThat(pipeline.context.isSet()).isFalse();
            assertThat(pipeline.context.getIfExists()).isNull();
            RequestContext first = pipeline.context.get();
            bothInitialized.countDown();
            assertThat(bothInitialized.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(pipeline.context.get()).isSameAs(first);
            handledByAcme.add(pipeline.handle("acme", "GET /orders"));
            handledByAcme.add(pipeline.handle("acme", "GET /invoices"));
        }));
        Thread globex = factory.newThread(guarded(failure, () -> {
            RequestContext first = pipeline.context.get();
            bothInitialized.countDown();
            assertThat(bothInitialized.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(pipeline.context.get()).isSameAs(first);
            handledByGlobex.add(pipeline.handle("globex", "POST /shipments"));
        }));
        acme.start();
        globex.start();
        acme.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
        globex.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));

        assertThat(failure.get()).isNull();
        assertThat(acme.isAlive()).isFalse();
        assertThat(globex.isAlive()).isFalse();
        assertThat(handledByAcme).containsExactly(
                "acme#" + acme.getName() + ":GET /orders",
                "acme#" + acme.getName() + ":GET /invoices");
        assertThat(handledByGlobex).containsExactly("globex#" + globex.getName() + ":POST /shipments");
        // Two eager contexts plus one per request, each created lazily on its own worker thread.
        assertThat(pipeline.contextsCreated.get()).isEqualTo(5);
        assertThat(pipeline.closedContexts).containsExactlyInAnyOrder(
                "<unassigned>", "<unassigned>", "acme:GET /orders", "acme:GET /invoices", "globex:POST /shipments");
        // Each worker allocates its scratch buffer once and reuses it for every request.
        assertThat(pipeline.buffersCreated.get()).isEqualTo(2);
    }

    @Test
    void contextLeftBehindByARequestIsClosedWhenTheWorkerExits() throws Exception {
        DefaultThreadFactory factory = new DefaultThreadFactory("tenant-worker", true);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger localsWhileRunning = new AtomicInteger();

        Thread worker = factory.newThread(guarded(failure, () -> {
            RequestContext context = pipeline.context.get();
            context.assign("initech", "PUT /reports/42");
            pipeline.scratch.get().append("unfinished");
            localsWhileRunning.set(FastThreadLocal.size());
            // The request "forgets" to clear its context; the worker's exit cleanup must close it.
        }));
        worker.start();
        worker.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));

        assertThat(failure.get()).isNull();
        assertThat(worker.isAlive()).isFalse();
        assertThat(localsWhileRunning.get()).isEqualTo(2);
        assertThat(pipeline.closedContexts).containsExactly("initech:PUT /reports/42");
        assertThat(pipeline.discardedBuffers).containsExactly("unfinished");
    }

    @Test
    void plainThreadFallsBackToRegularStorageAndNeedsExplicitCleanup() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<String> handled = Collections.synchronizedList(new ArrayList<>());

        Thread plain = new Thread(guarded(failure, () -> {
            Thread current = Thread.currentThread();
            assertThat(current).isNotInstanceOf(FastThreadLocalThread.class);
            assertThat(FastThreadLocalThread.willCleanupFastThreadLocals(current)).isFalse();

            handled.add(pipeline.handle("umbrella", "GET /status"));
            handled.add(pipeline.handle("umbrella", "GET /health"));
            assertThat(pipeline.scratch.isSet()).isTrue();

            pipeline.context.set(new RequestContext("<preset>"));
            assertThat(pipeline.context.get().describe()).isEqualTo("<preset>");
            assertThat(FastThreadLocal.size()).isEqualTo(2);

            FastThreadLocal.removeAll();

            assertThat(pipeline.context.isSet()).isFalse();
            assertThat(pipeline.scratch.isSet()).isFalse();
            assertThat(FastThreadLocal.size()).isZero();
        }), "plain-reporting-thread");
        plain.start();
        plain.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));

        assertThat(failure.get()).isNull();
        assertThat(plain.isAlive()).isFalse();
        assertThat(handled).containsExactly(
                "umbrella#plain-reporting-thread:GET /status",
                "umbrella#plain-reporting-thread:GET /health");
        assertThat(pipeline.buffersCreated.get()).isEqualTo(1);
        assertThat(pipeline.closedContexts).containsExactly(
                "umbrella:GET /status", "umbrella:GET /health", "<preset>");
        assertThat(pipeline.discardedBuffers).containsExactly("");
    }

    private static Runnable guarded(AtomicReference<Throwable> failure, ThrowingRunnable body) {
        return () -> {
            try {
                body.run();
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        };
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /** Mutable per-request state owned by one worker thread at a time. */
    private static final class RequestContext {

        private String tenant;

        private String route;

        private final String placeholder;

        RequestContext(String placeholder) {
            this.placeholder = placeholder;
        }

        void assign(String newTenant, String newRoute) {
            this.tenant = newTenant;
            this.route = newRoute;
        }

        String describe() {
            return tenant == null ? placeholder : tenant + ":" + route;
        }
    }

    /** Handles tenant requests, keeping the context and a scratch buffer in fast thread-locals. */
    private static final class RequestPipeline {

        final AtomicInteger contextsCreated = new AtomicInteger();

        final AtomicInteger buffersCreated = new AtomicInteger();

        final List<String> closedContexts = Collections.synchronizedList(new ArrayList<>());

        final List<String> discardedBuffers = Collections.synchronizedList(new ArrayList<>());

        final FastThreadLocal<RequestContext> context = new FastThreadLocal<RequestContext>() {
            @Override
            protected RequestContext initialValue() {
                contextsCreated.incrementAndGet();
                return new RequestContext("<unassigned>");
            }

            @Override
            protected void onRemoval(RequestContext value) {
                closedContexts.add(value.describe());
            }
        };

        final FastThreadLocal<StringBuilder> scratch = new FastThreadLocal<StringBuilder>() {
            @Override
            protected StringBuilder initialValue() {
                buffersCreated.incrementAndGet();
                return new StringBuilder();
            }

            @Override
            protected void onRemoval(StringBuilder value) {
                discardedBuffers.add(value.toString());
            }
        };

        String handle(String tenant, String route) {
            // Release any context a previous step initialized but never assigned to a request.
            context.remove();
            RequestContext current = context.get();
            current.assign(tenant, route);
            try {
                StringBuilder buffer = scratch.get();
                buffer.setLength(0);
                buffer.append(tenant).append('#').append(Thread.currentThread().getName())
                        .append(':').append(route);
                return buffer.toString();
            } finally {
                context.remove();
                scratch.get().setLength(0);
            }
        }
    }
}
