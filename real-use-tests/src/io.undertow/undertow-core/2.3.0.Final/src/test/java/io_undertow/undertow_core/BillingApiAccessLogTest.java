/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_undertow.undertow_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.attribute.ExchangeAttributes;
import io.undertow.predicate.Predicates;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.accesslog.AccessLogHandler;
import io.undertow.server.handlers.accesslog.AccessLogReceiver;
import io.undertow.server.handlers.accesslog.DefaultAccessLogReceiver;
import io.undertow.server.handlers.accesslog.ExtendedAccessLogParser;
import io.undertow.server.handlers.accesslog.ExtendedAccessLogParser.ExtendedAccessLogHeaderGenerator;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Request access logging with `AccessLogHandler`: custom `%`-patterns over request headers,
 * response headers, cookies, query strings and byte counts, the `common` and `combined` named
 * formats, a predicate that keeps health checks out of the log, response times recorded through
 * `UndertowOptions.RECORD_REQUEST_START_TIME`, and W3C extended-format lines written to a file by
 * a `DefaultAccessLogReceiver` with an `ExtendedAccessLogHeaderGenerator` header.
 *
 * A billing API runs on an embedded Undertow server bound to an ephemeral port. It lists invoices,
 * accepts new ones over POST, answers 404 for unknown invoices, and exposes a health endpoint for
 * the load balancer. Access lines are written once each exchange completes, which can be after
 * the client has read the response, so the test waits for them with a bounded poll.
 */
public class BillingApiAccessLogTest {

    private static final HttpString REQUEST_ID = new HttpString("X-Request-Id");
    private static final Duration WAIT = Duration.ofSeconds(10);

    private final LinkedBlockingQueue<String> lines = new LinkedBlockingQueue<>();
    private final AccessLogReceiver collector = lines::add;

    private Undertow server;
    private HttpClient client;
    private URI base;

    @BeforeEach
    void createClient() {
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(WAIT)
                .build();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void customPatternCapturesRequestAndResponseDetails() throws Exception {
        start(new AccessLogHandler(api(), collector,
                "%m %U q=%q %s %b %B \"%{i,User-Agent}\" id=%{o,X-Request-Id} tenant=%{c,tenant} user=%u",
                getClass().getClassLoader()));

        HttpResponse<String> listed = send(HttpRequest.newBuilder(base.resolve("/invoices?status=open"))
                .header("User-Agent", "billing-cli/3.1")
                .header("Cookie", "tenant=acme")
                .header("X-Request-Id", "req-17")
                .GET());
        assertEquals(200, listed.statusCode());
        assertEquals("{\"invoices\":2}", listed.body());
        assertEquals("GET /invoices q=?status=open 200 14 14 \"billing-cli/3.1\" id=req-17 tenant=acme user=-",
                nextLine());

        HttpResponse<String> missing = send(HttpRequest.newBuilder(base.resolve("/invoices/INV-404"))
                .header("User-Agent", "billing-cli/3.1")
                .GET());
        assertEquals(404, missing.statusCode());
        assertEquals("GET /invoices/INV-404 q=- 404 - 0 \"billing-cli/3.1\" id=generated tenant=- user=-",
                nextLine());
    }

    @Test
    void namedCommonAndCombinedFormatsDescribeEachExchange() throws Exception {
        LinkedBlockingQueue<String> combined = new LinkedBlockingQueue<>();
        HttpHandler combinedLog = new AccessLogHandler(api(), combined::add, "combined",
                getClass().getClassLoader());
        start(new AccessLogHandler(combinedLog, collector, "common", getClass().getClassLoader()));

        HttpResponse<String> created = send(HttpRequest.newBuilder(base.resolve("/invoices"))
                .header("Referer", "https://portal.example/invoices/new")
                .header("User-Agent", "portal/2.0")
                .POST(HttpRequest.BodyPublishers.ofString("amount=42")));
        assertEquals(201, created.statusCode());
        assertEquals("created INV-3 for amount=42", created.body());

        String common = nextLine();
        assertTrue(common.matches(
                "127\\.0\\.0\\.1 - - \\[[^\\]]+\\] \"POST /invoices HTTP/1\\.1\" 201 27"), common);

        String combinedLine = combined.poll(WAIT.toSeconds(), TimeUnit.SECONDS);
        assertNotNull(combinedLine, "the combined log should receive the exchange");
        assertTrue(combinedLine.startsWith(common.substring(0, common.indexOf(']') + 1)), combinedLine);
        assertTrue(combinedLine.endsWith(
                "\"POST /invoices HTTP/1.1\" 201 27 \"https://portal.example/invoices/new\" \"portal/2.0\""),
                combinedLine);
    }

    @Test
    void predicateKeepsHealthChecksOutOfTheLog() throws Exception {
        // The predicate runs when the exchange completes, after the path handler has consumed the
        // relative path, so it matches the full request URL instead.
        start(new AccessLogHandler(api(), collector, "%m %U %s", getClass().getClassLoader(),
                Predicates.not(Predicates.regex(ExchangeAttributes.requestURL(), "^/health$"))));

        for (int i = 0; i < 3; i++) {
            HttpResponse<String> health = send(HttpRequest.newBuilder(base.resolve("/health")).GET());
            assertEquals(200, health.statusCode());
            assertEquals("UP", health.body());
        }
        assertEquals(200, send(HttpRequest.newBuilder(base.resolve("/invoices")).GET()).statusCode());

        assertEquals("GET /invoices 200", nextLine(),
                "health checks served on the same connection would have been logged first");
        assertTrue(lines.isEmpty(), "only the invoice request should be logged: " + lines);
    }

    @Test
    void recordsResponseTimesWhenRequestStartTimesAreTracked() throws Exception {
        start(new AccessLogHandler(api(), collector, "%U %s %D ms %T s", getClass().getClassLoader()),
                true);

        assertEquals(200, send(HttpRequest.newBuilder(base.resolve("/invoices")).GET()).statusCode());

        String line = nextLine();
        assertTrue(line.matches("/invoices 200 \\d+ ms \\d+\\.\\d+ s"), line);
    }

    @Test
    void writesExtendedFormatLinesToALogFile(@TempDir Path logDirectory) throws Exception {
        String pattern = "c-ip cs-method cs-uri-stem cs-uri-query sc-status bytes cs(X-Tenant)";
        ExecutorService writer = Executors.newSingleThreadExecutor();
        DefaultAccessLogReceiver receiver = DefaultAccessLogReceiver.builder()
                .setLogWriteExecutor(writer)
                .setOutputDirectory(logDirectory)
                .setLogBaseName("billing-access.")
                .setLogNameSuffix("log")
                .setRotate(false)
                .setLogFileHeaderGenerator(new ExtendedAccessLogHeaderGenerator(pattern))
                .build();
        try {
            start(new AccessLogHandler(api(), receiver, pattern,
                    new ExtendedAccessLogParser(getClass().getClassLoader()).parse(pattern)));

            assertEquals(200, send(HttpRequest.newBuilder(base.resolve("/invoices?page=2"))
                    .header("X-Tenant", "acme")
                    .GET()).statusCode());
            assertEquals(201, send(HttpRequest.newBuilder(base.resolve("/invoices"))
                    .header("X-Tenant", "globex")
                    .POST(HttpRequest.BodyPublishers.ofString("amount=7"))).statusCode());
            assertEquals(404, send(HttpRequest.newBuilder(base.resolve("/invoices/INV-9"))
                    .GET()).statusCode());

            Path logFile = logDirectory.resolve("billing-access.log");
            List<String> entries = awaitEntries(logFile, 3);
            assertEquals(List.of(
                    "127.0.0.1 GET /invoices page=2 200 14 'acme'",
                    "127.0.0.1 POST /invoices - 201 26 'globex'",
                    "127.0.0.1 GET /invoices/INV-9 - 404 - -"), entries);

            List<String> header = Files.readAllLines(logFile, StandardCharsets.UTF_8).stream()
                    .filter(line -> line.startsWith("#"))
                    .toList();
            assertTrue(header.contains("#Fields: " + pattern), header.toString());
        } finally {
            receiver.close();
            writer.shutdown();
            assertTrue(writer.awaitTermination(WAIT.toSeconds(), TimeUnit.SECONDS));
        }
    }

    private void start(HttpHandler handler) {
        start(handler, false);
    }

    private void start(HttpHandler handler, boolean recordStartTimes) {
        server = Undertow.builder()
                .addHttpListener(0, "127.0.0.1")
                .setServerOption(UndertowOptions.RECORD_REQUEST_START_TIME, recordStartTimes)
                .setHandler(handler)
                .build();
        server.start();
        InetSocketAddress address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
        base = URI.create("http://127.0.0.1:" + address.getPort());
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return client.send(request.timeout(WAIT).build(), HttpResponse.BodyHandlers.ofString());
    }

    private String nextLine() throws InterruptedException {
        String line = lines.poll(WAIT.toSeconds(), TimeUnit.SECONDS);
        assertNotNull(line, "no access log line arrived within " + WAIT);
        return line;
    }

    /// Polls the log file until it holds `count` non-header lines, bounded by `WAIT`.
    private static List<String> awaitEntries(Path logFile, int count) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        List<String> entries = List.of();
        while (System.nanoTime() < deadline) {
            if (Files.exists(logFile)) {
                entries = Files.readAllLines(logFile, StandardCharsets.UTF_8).stream()
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .toList();
                if (entries.size() >= count) {
                    return entries;
                }
            }
            Thread.sleep(20);
        }
        throw new AssertionError("expected " + count + " access log entries in " + logFile + " but saw " + entries);
    }

    /// The billing API: invoice listing and creation, a 404 for unknown invoices, and a health check.
    private static HttpHandler api() {
        return Handlers.path()
                .addExactPath("/health", exchange -> text(exchange, StatusCodes.OK, "UP"))
                .addExactPath("/invoices", BillingApiAccessLogTest::invoices)
                .addPrefixPath("/invoices/", exchange -> {
                    echoRequestId(exchange);
                    exchange.setStatusCode(StatusCodes.NOT_FOUND);
                    exchange.endExchange();
                });
    }

    private static void invoices(HttpServerExchange exchange) {
        echoRequestId(exchange);
        if (Methods.POST.equals(exchange.getRequestMethod())) {
            exchange.getRequestReceiver().receiveFullString((ex, body) ->
                    text(ex, StatusCodes.CREATED, "created INV-3 for " + body), StandardCharsets.UTF_8);
            return;
        }
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
        exchange.getResponseSender().send("{\"invoices\":2}", StandardCharsets.UTF_8);
    }

    private static void echoRequestId(HttpServerExchange exchange) {
        String requestId = exchange.getRequestHeaders().getFirst(REQUEST_ID);
        exchange.getResponseHeaders().put(REQUEST_ID, requestId == null ? "generated" : requestId);
    }

    private static void text(HttpServerExchange exchange, int status, String body) {
        exchange.setStatusCode(status);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=UTF-8");
        exchange.getResponseSender().send(body, StandardCharsets.UTF_8);
    }
}
