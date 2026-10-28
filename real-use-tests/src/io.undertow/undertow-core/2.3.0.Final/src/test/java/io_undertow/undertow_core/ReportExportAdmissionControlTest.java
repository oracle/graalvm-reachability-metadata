/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_undertow.undertow_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.GracefulShutdownHandler;
import io.undertow.server.handlers.MetricsHandler;
import io.undertow.server.handlers.RequestLimit;
import io.undertow.server.handlers.RequestLimitingHandler;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Request admission control with `RequestLimitingHandler`, `GracefulShutdownHandler`, and
 * `MetricsHandler`: a bounded concurrency limit with a wait queue and a custom overflow handler,
 * raising the limit at runtime to admit queued requests, draining in-flight requests on shutdown
 * while new ones are refused, resuming service, and request and error statistics.
 *
 * A reporting service renders expensive exports one at a time. One more export may wait in line;
 * anything beyond that is told to retry later. Each export blocks on a latch the test releases, so
 * which exports run, wait, or are refused is decided by the test, never by timing. The server runs
 * a single IO thread, so a request read after another is always admitted or queued after it.
 */
public class ReportExportAdmissionControlTest {

    private static final long WAIT_SECONDS = 10;

    private Undertow server;
    private RequestLimit limit;
    private GracefulShutdownHandler shutdownGate;
    private MetricsHandler metrics;
    private ReportRenderer renderer;
    private BlockingQueue<String> passedLimiter;
    private HttpClient client;
    private URI base;

    @BeforeEach
    void startServer() {
        renderer = new ReportRenderer();
        passedLimiter = new LinkedBlockingQueue<>();
        limit = new RequestLimit(1, 1);
        limit.setFailureHandler(ReportExportAdmissionControlTest::exportQueueFull);
        HttpHandler limiter = new RequestLimitingHandler(limit, new BlockingHandler(renderer));
        HttpHandler admission = exchange -> {
            limiter.handleRequest(exchange);
            passedLimiter.add(exchange.getQueryString());
        };
        shutdownGate = new GracefulShutdownHandler(admission);
        metrics = new MetricsHandler(shutdownGate);
        server = Undertow.builder()
                .setIoThreads(1)
                .setWorkerThreads(4)
                .addHttpListener(0, "127.0.0.1")
                .setHandler(metrics)
                .build();
        server.start();
        InetSocketAddress address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
        base = URI.create("http://127.0.0.1:" + address.getPort());
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(WAIT_SECONDS))
                .build();
    }

    @AfterEach
    void stopServer() {
        renderer.releaseAll();
        server.stop();
    }

    @Test
    void exportsBeyondTheLimitWaitInLineAndOverflowIsRefused() throws Exception {
        CompletableFuture<HttpResponse<String>> first = exportAsync("q1-revenue");
        renderer.awaitStarted("q1-revenue");
        assertEquals("name=q1-revenue", passedLimiter.poll(WAIT_SECONDS, TimeUnit.SECONDS));

        CompletableFuture<HttpResponse<String>> second = exportAsync("q2-revenue");
        assertEquals("name=q2-revenue", passedLimiter.poll(WAIT_SECONDS, TimeUnit.SECONDS));
        assertFalse(renderer.hasStarted("q2-revenue"));

        HttpResponse<String> overflow = export("q3-revenue");
        assertEquals(StatusCodes.SERVICE_UNAVAILABLE, overflow.statusCode());
        assertEquals("5", overflow.headers().firstValue("Retry-After").orElseThrow());
        assertEquals("export queue full", overflow.body());
        assertFalse(first.isDone());
        assertFalse(second.isDone());

        renderer.release("q1-revenue");
        assertReady(first, "q1-revenue");
        renderer.awaitStarted("q2-revenue");
        renderer.release("q2-revenue");
        assertReady(second, "q2-revenue");

        assertFalse(renderer.hasStarted("q3-revenue"));
        assertEquals(1, limit.getMaximumConcurrentRequests());
        awaitCount(3, () -> metrics.getMetrics().getTotalRequests());
        assertEquals(1, metrics.getMetrics().getTotalErrors());
    }

    @Test
    void raisingTheLimitAdmitsTheWaitingExport() throws Exception {
        CompletableFuture<HttpResponse<String>> running = exportAsync("payroll");
        renderer.awaitStarted("payroll");
        CompletableFuture<HttpResponse<String>> waiting = exportAsync("invoices");
        assertEquals("name=payroll", passedLimiter.poll(WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals("name=invoices", passedLimiter.poll(WAIT_SECONDS, TimeUnit.SECONDS));
        assertFalse(renderer.hasStarted("invoices"));

        assertEquals(1, limit.setMaximumConcurrentRequests(2));

        renderer.awaitStarted("invoices");
        assertFalse(running.isDone());
        renderer.release("invoices");
        assertReady(waiting, "invoices");
        renderer.release("payroll");
        assertReady(running, "payroll");
        assertEquals(2, limit.getMaximumConcurrentRequests());
    }

    @Test
    void shutdownDrainsInFlightExportsRefusesNewOnesAndCanResume() throws Exception {
        BlockingQueue<Boolean> drained = new LinkedBlockingQueue<>();
        CompletableFuture<HttpResponse<String>> inFlight = exportAsync("year-end");
        renderer.awaitStarted("year-end");

        shutdownGate.shutdown();
        shutdownGate.addShutdownListener(drained::add);

        HttpResponse<String> refused = export("late-request");
        assertEquals(StatusCodes.SERVICE_UNAVAILABLE, refused.statusCode());
        assertFalse(renderer.hasStarted("late-request"));
        assertFalse(shutdownGate.awaitShutdown(200));
        assertNull(drained.poll());
        assertFalse(inFlight.isDone());

        renderer.release("year-end");
        assertReady(inFlight, "year-end");
        assertTrue(shutdownGate.awaitShutdown(TimeUnit.SECONDS.toMillis(WAIT_SECONDS)));
        assertEquals(Boolean.TRUE, drained.poll(WAIT_SECONDS, TimeUnit.SECONDS));

        shutdownGate.start();
        renderer.release("after-restart");
        assertEquals("report after-restart ready", export("after-restart").body());
    }

    @Test
    void metricsRecordRequestsErrorsAndTimings() throws Exception {
        renderer.release("daily");
        renderer.release("weekly");

        assertEquals("report daily ready", export("daily").body());
        assertEquals("report weekly ready", export("weekly").body());
        HttpResponse<String> missingName = send(HttpRequest.newBuilder(base.resolve("/export")));
        assertEquals(StatusCodes.BAD_REQUEST, missingName.statusCode());
        assertEquals("missing report name", missingName.body());

        awaitCount(3, () -> metrics.getMetrics().getTotalRequests());
        MetricsHandler.MetricResult result = metrics.getMetrics();
        assertEquals(1, result.getTotalErrors());
        assertNotNull(result.getMetricsStartDate());
        assertTrue(result.getMinRequestTime() >= 0);
        assertTrue(result.getMinRequestTime() <= result.getMaxRequestTime());
        assertTrue(result.getTotalRequestTime() >= result.getMaxRequestTime());

        metrics.reset();
        MetricsHandler.MetricResult cleared = metrics.getMetrics();
        assertEquals(0, cleared.getTotalRequests());
        assertEquals(0, cleared.getTotalErrors());
        assertEquals(0, cleared.getTotalRequestTime());
    }

    /// Answers an export that found both the running slot and the queue taken.
    private static void exportQueueFull(HttpServerExchange exchange) {
        exchange.setStatusCode(StatusCodes.SERVICE_UNAVAILABLE);
        exchange.getResponseHeaders().put(Headers.RETRY_AFTER, "5");
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
        exchange.getResponseSender().send("export queue full");
    }

    private CompletableFuture<HttpResponse<String>> exportAsync(String name) {
        return client.sendAsync(exportRequest(name).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> export(String name) throws Exception {
        return send(exportRequest(name));
    }

    private HttpRequest.Builder exportRequest(String name) {
        return HttpRequest.newBuilder(base.resolve("/export?name=" + name));
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return client.send(request.timeout(Duration.ofSeconds(WAIT_SECONDS)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static void assertReady(CompletableFuture<HttpResponse<String>> pending, String name)
            throws Exception {
        HttpResponse<String> response = pending.get(WAIT_SECONDS, TimeUnit.SECONDS);
        assertEquals(StatusCodes.OK, response.statusCode());
        assertEquals("report " + name + " ready", response.body());
    }

    /// Polls a counter updated by exchange-completion listeners, which may run after the client
    /// has already read the response.
    private static void awaitCount(long expected, LongSupplier counter) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (counter.getAsLong() != expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(expected, counter.getAsLong());
    }

    /// Renders a named report on a worker thread once the test releases that name.
    private static final class ReportRenderer implements HttpHandler {

        private final Map<String, CountDownLatch> releases = new ConcurrentHashMap<>();
        private final Map<String, CountDownLatch> starts = new ConcurrentHashMap<>();

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            Deque<String> names = exchange.getQueryParameters().get("name");
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
            if (names == null) {
                exchange.setStatusCode(StatusCodes.BAD_REQUEST);
                exchange.getResponseSender().send("missing report name");
                return;
            }
            String name = names.getFirst();
            starts(name).countDown();
            if (!releases(name).await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
                exchange.getResponseSender().send("report " + name + " was never released");
                return;
            }
            exchange.getResponseSender().send("report " + name + " ready");
        }

        void awaitStarted(String name) throws InterruptedException {
            assertTrue(starts(name).await(WAIT_SECONDS, TimeUnit.SECONDS), name + " never started");
        }

        boolean hasStarted(String name) {
            return starts(name).getCount() == 0;
        }

        void release(String name) {
            releases(name).countDown();
        }

        void releaseAll() {
            releases.values().forEach(CountDownLatch::countDown);
        }

        private CountDownLatch starts(String name) {
            return starts.computeIfAbsent(name, key -> new CountDownLatch(1));
        }

        private CountDownLatch releases(String name) {
            return releases.computeIfAbsent(name, key -> new CountDownLatch(1));
        }
    }
}
