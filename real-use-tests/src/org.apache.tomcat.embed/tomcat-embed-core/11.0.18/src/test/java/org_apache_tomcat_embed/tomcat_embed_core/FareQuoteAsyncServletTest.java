/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quotes airline fares through asynchronous servlet processing, where `AsyncContext` work
 * started with `AsyncContext.start`, async timeouts reported to an `AsyncListener`,
 * `AsyncContext.dispatch` to a rendering servlet and a non-blocking `ReadListener` upload
 * run behind an async-supported `Filter`.
 *
 * A travel site answers fare lookups without holding a request thread while the pricing
 * work runs: the lookup servlet hands the work to the container's async executor and
 * completes the response from there. A supplier that never answers is cut off by the async
 * timeout and turned into a `504` by the listener. Search results are priced asynchronously
 * and then dispatched to a separate view servlet, which sees the `ASYNC` dispatcher type and
 * the original request URI. Bulk fare files are read with non-blocking I/O and acknowledged
 * with their size and CRC-32. The trace filter runs on both the original and the async
 * dispatch, and every exchange ends with the listener's `onComplete` event.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FareQuoteAsyncServletTest {

    private static final String REQUEST_ID = "X-Request-Id";

    private static final String FARE_ATTRIBUTE = "travel.fare";

    private static final Map<String, Integer> FARES = Map.of(
            "LHR-JFK", 412,
            "CDG-NRT", 958,
            "BEG-VIE", 89);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final Map<String, BlockingQueue<String>> events = new ConcurrentHashMap<>();

    private final TraceFilter traceFilter = new TraceFilter();

    private Tomcat tomcat;

    private Path baseDir;

    private String baseUrl;

    @BeforeAll
    void startServer() throws IOException, LifecycleException {
        baseDir = Files.createTempDirectory("fare-quote-tomcat");
        tomcat = new Tomcat();
        tomcat.setBaseDir(baseDir.toString());
        tomcat.setPort(0);
        tomcat.getConnector();

        Context context = tomcat.addContext("/travel", baseDir.toString());
        asyncServlet(context, "fares", new FareLookupServlet(), "/fares/*");
        asyncServlet(context, "supplier", new StalledSupplierServlet(), "/supplier/*");
        asyncServlet(context, "search", new FareSearchServlet(), "/search");
        asyncServlet(context, "bulk", new BulkFareUploadServlet(), "/bulk");
        Tomcat.addServlet(context, "view", new FareViewServlet());
        context.addServletMappingDecoded("/view", "view");

        FilterDef filterDef = new FilterDef();
        filterDef.setFilterName("trace");
        filterDef.setFilter(traceFilter);
        filterDef.setAsyncSupported("true");
        context.addFilterDef(filterDef);
        FilterMap filterMap = new FilterMap();
        filterMap.setFilterName("trace");
        filterMap.addURLPattern("/*");
        filterMap.setDispatcher(DispatcherType.REQUEST.name());
        filterMap.setDispatcher(DispatcherType.ASYNC.name());
        context.addFilterMap(filterMap);

        tomcat.start();
        baseUrl = "http://localhost:" + tomcat.getConnector().getLocalPort() + "/travel";
    }

    @AfterAll
    void stopServer() throws LifecycleException, IOException {
        tomcat.stop();
        tomcat.destroy();
        try (var paths = Files.walk(baseDir)) {
            paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void quotesAFareFromWorkStartedOnTheAsyncContext() throws Exception {
        HttpResponse<String> response = send(get("/fares/LHR-JFK", "lookup-1"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("X-Trace")).hasValue("lookup-1");
        assertThat(response.body()).isEqualTo("route=LHR-JFK fare=412 EUR asyncStarted=true\n");
        assertThat(awaitEvent("lookup-1")).isEqualTo("complete");
    }

    @Test
    void answersNotFoundAsynchronouslyForAnUnknownRoute() throws Exception {
        HttpResponse<String> response = send(get("/fares/AAA-BBB", "lookup-2"));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).isEqualTo("no fare for AAA-BBB\n");
        assertThat(awaitEvent("lookup-2")).isEqualTo("complete");
    }

    @Test
    void turnsAStalledSupplierIntoAGatewayTimeout() throws Exception {
        HttpResponse<String> response = send(get("/supplier/acme", "stalled-1"));

        assertThat(response.statusCode()).isEqualTo(504);
        assertThat(response.body()).isEqualTo("supplier acme did not answer within 300 ms\n");
        assertThat(awaitEvent("stalled-1")).isEqualTo("timeout");
        assertThat(awaitEvent("stalled-1")).isEqualTo("complete");
    }

    @Test
    void dispatchesPricedSearchResultsToTheViewServlet() throws Exception {
        HttpResponse<String> response = send(get("/search?from=BEG&to=VIE", "search-1"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(
                "view dispatcher=ASYNC original=/travel/search query=from=BEG&to=VIE fare=89 EUR\n");
        assertThat(traceFilter.dispatches("search-1")).containsExactly("REQUEST", "ASYNC");
        assertThat(awaitEvent("search-1")).isEqualTo("complete");
    }

    @Test
    void acknowledgesABulkFareFileReadWithNonBlockingIo() throws Exception {
        StringBuilder csv = new StringBuilder("route,fare\n");
        for (int i = 0; i < 20_000; i++) {
            csv.append("R").append(i).append("-X,").append(100 + i % 900).append('\n');
        }
        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        CRC32 expected = new CRC32();
        expected.update(body);

        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(baseUrl + "/bulk"))
                .header(REQUEST_ID, "bulk-1")
                .header("Content-Type", "text/csv")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)));

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.body()).isEqualTo("bytes=" + body.length + " lines=20001 crc32="
                + Long.toHexString(expected.getValue()) + "\n");
        assertThat(awaitEvent("bulk-1")).isEqualTo("complete");
    }

    private static void asyncServlet(Context context, String name, HttpServlet servlet, String pattern) {
        Wrapper wrapper = Tomcat.addServlet(context, name, servlet);
        wrapper.setAsyncSupported(true);
        context.addServletMappingDecoded(pattern, name);
    }

    private HttpRequest.Builder get(String path, String requestId) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).header(REQUEST_ID, requestId).GET();
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return client.send(request.timeout(Duration.ofSeconds(20)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String awaitEvent(String requestId) throws InterruptedException {
        String event = queue(requestId).poll(20, TimeUnit.SECONDS);
        assertThat(event).as("async event for %s", requestId).isNotNull();
        return event;
    }

    private BlockingQueue<String> queue(String requestId) {
        return events.computeIfAbsent(requestId, id -> new LinkedBlockingQueue<>());
    }

    /** Starts async processing with a listener that records lifecycle events per request id. */
    private AsyncContext startTracked(HttpServletRequest request, long timeoutMillis) {
        BlockingQueue<String> requestEvents = queue(request.getHeader(REQUEST_ID));
        AsyncContext async = request.startAsync();
        async.setTimeout(timeoutMillis);
        async.addListener(new AsyncListener() {
            @Override
            public void onComplete(AsyncEvent event) {
                requestEvents.add("complete");
            }

            @Override
            public void onTimeout(AsyncEvent event) throws IOException {
                requestEvents.add("timeout");
                HttpServletResponse response = (HttpServletResponse) event.getAsyncContext().getResponse();
                String supplier = ((HttpServletRequest) event.getSuppliedRequest()).getPathInfo().substring(1);
                response.setStatus(HttpServletResponse.SC_GATEWAY_TIMEOUT);
                plainText(response);
                response.getWriter().println("supplier " + supplier + " did not answer within "
                        + event.getAsyncContext().getTimeout() + " ms");
                event.getAsyncContext().complete();
            }

            @Override
            public void onError(AsyncEvent event) {
                requestEvents.add("error:" + event.getThrowable());
            }

            @Override
            public void onStartAsync(AsyncEvent event) {
                requestEvents.add("restart");
            }
        });
        return async;
    }

    private static void plainText(HttpServletResponse response) {
        response.setContentType("text/plain");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    }

    /** Echoes the request id and remembers which dispatcher types reached it. */
    static final class TraceFilter implements Filter {

        private final Map<String, Queue<String>> dispatches = new ConcurrentHashMap<>();

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            String requestId = ((HttpServletRequest) request).getHeader(REQUEST_ID);
            dispatches.computeIfAbsent(requestId, id -> new ConcurrentLinkedQueue<>())
                    .add(request.getDispatcherType().name());
            ((HttpServletResponse) response).setHeader("X-Trace", requestId);
            chain.doFilter(request, response);
        }

        List<String> dispatches(String requestId) {
            return new ArrayList<>(dispatches.getOrDefault(requestId, new ConcurrentLinkedQueue<>()));
        }
    }

    /** Prices one route on the container's async executor and completes the response there. */
    final class FareLookupServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            AsyncContext async = startTracked(request, 10_000);
            String route = request.getPathInfo().substring(1);
            boolean started = request.isAsyncStarted();
            async.start(() -> {
                HttpServletResponse asyncResponse = (HttpServletResponse) async.getResponse();
                try {
                    plainText(asyncResponse);
                    Integer fare = FARES.get(route);
                    if (fare == null) {
                        asyncResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        asyncResponse.getWriter().println("no fare for " + route);
                    } else {
                        asyncResponse.getWriter().println("route=" + route + " fare=" + fare
                                + " EUR asyncStarted=" + started);
                    }
                } catch (IOException e) {
                    asyncResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                } finally {
                    async.complete();
                }
            });
        }
    }

    /** Waits on a supplier that never answers, leaving the async timeout to end the request. */
    final class StalledSupplierServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            startTracked(request, 300);
        }
    }

    /** Prices a search asynchronously, then dispatches to the view servlet for rendering. */
    final class FareSearchServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            AsyncContext async = startTracked(request, 10_000);
            String route = request.getParameter("from") + "-" + request.getParameter("to");
            async.start(() -> {
                async.getRequest().setAttribute(FARE_ATTRIBUTE, FARES.get(route));
                async.dispatch("/view");
            });
        }
    }

    /** Renders a priced search after the async dispatch. */
    static final class FareViewServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            plainText(response);
            response.getWriter().println("view dispatcher=" + request.getDispatcherType()
                    + " original=" + request.getAttribute(AsyncContext.ASYNC_REQUEST_URI)
                    + " query=" + request.getAttribute(AsyncContext.ASYNC_QUERY_STRING)
                    + " fare=" + request.getAttribute(FARE_ATTRIBUTE) + " EUR");
        }
    }

    /** Reads a fare file with a non-blocking `ReadListener` and acknowledges its checksum. */
    final class BulkFareUploadServlet extends HttpServlet {

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
            AsyncContext async = startTracked(request, 20_000);
            ServletInputStream input = request.getInputStream();
            input.setReadListener(new ReadListener() {
                private final byte[] buffer = new byte[4096];

                private final CRC32 crc = new CRC32();

                private long bytes;

                private long lines;

                @Override
                public void onDataAvailable() throws IOException {
                    while (input.isReady() && !input.isFinished()) {
                        int read = input.read(buffer);
                        if (read > 0) {
                            crc.update(buffer, 0, read);
                            bytes += read;
                            for (int i = 0; i < read; i++) {
                                if (buffer[i] == '\n') {
                                    lines++;
                                }
                            }
                        }
                    }
                }

                @Override
                public void onAllDataRead() throws IOException {
                    response.setStatus(HttpServletResponse.SC_ACCEPTED);
                    plainText(response);
                    response.getWriter().println("bytes=" + bytes + " lines=" + lines
                            + " crc32=" + Long.toHexString(crc.getValue()));
                    async.complete();
                }

                @Override
                public void onError(Throwable failure) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    async.complete();
                }
            });
        }
    }
}
