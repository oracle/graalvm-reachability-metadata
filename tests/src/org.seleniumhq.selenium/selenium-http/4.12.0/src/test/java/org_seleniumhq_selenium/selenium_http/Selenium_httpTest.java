/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_http;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.UsernameAndPassword;
import org.openqa.selenium.remote.http.AddSeleniumUserAgent;
import org.openqa.selenium.remote.http.BinaryMessage;
import org.openqa.selenium.remote.http.ClientConfig;
import org.openqa.selenium.remote.http.CloseMessage;
import org.openqa.selenium.remote.http.ConnectionFailedException;
import org.openqa.selenium.remote.http.Contents;
import org.openqa.selenium.remote.http.DumpHttpExchangeFilter;
import org.openqa.selenium.remote.http.Filter;
import org.openqa.selenium.remote.http.FormEncodedData;
import org.openqa.selenium.remote.http.HttpHandler;
import org.openqa.selenium.remote.http.HttpMethod;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.http.HttpResponse;
import org.openqa.selenium.remote.http.Message;
import org.openqa.selenium.remote.http.RemoteCall;
import org.openqa.selenium.remote.http.RetryRequest;
import org.openqa.selenium.remote.http.Route;
import org.openqa.selenium.remote.http.TextMessage;
import org.openqa.selenium.remote.http.UrlPath;
import org.openqa.selenium.remote.http.UrlTemplate;
import org.openqa.selenium.remote.http.WebSocket;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Selenium_httpTest {
    @Test
    public void shouldManageHeadersAttributesQueryParametersAndEncodedContent() {
        HttpRequest request = new HttpRequest(HttpMethod.POST, "/submit")
            .addHeader("X-Test", "first")
            .addHeader("x-test", "second")
            .setAttribute("trace", "abc-123")
            .addQueryParameter("name", "Jane Doe")
            .addQueryParameter("name", "John Roe")
            .setHeader("Content-Type", "text/plain; charset=ISO-8859-1")
            .setContent(Contents.string("caf\u00e9", StandardCharsets.ISO_8859_1));

        assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
        assertThat(request.getUri()).isEqualTo("/submit");
        assertThat(request.getHeader("X-Test")).isEqualTo("first");
        assertThat(request.getHeaders("X-Test")).containsExactly("first", "second");
        assertThat(request.getHeaderNames()).contains("X-Test", "x-test", "Content-Type");
        assertThat(request.getAttribute("trace")).isEqualTo("abc-123");
        assertThat(request.getAttributeNames()).containsExactly("trace");
        assertThat(request.getQueryParameter("name")).isEqualTo("Jane Doe");
        assertThat(request.getQueryParameters("name")).containsExactly("Jane Doe", "John Roe");
        assertThat(request.getQueryParameterNames()).containsExactly("name");
        assertThat(request.getContentEncoding()).isEqualTo(StandardCharsets.ISO_8859_1);
        assertThat(Contents.string(request)).isEqualTo("caf\u00e9");

        request.removeHeader("X-Test").removeHeader("x-test").removeAttribute("trace");

        assertThat(request.getHeader("X-Test")).isNull();
        assertThat(request.getAttribute("trace")).isNull();
        assertThat(request.toString()).isEqualTo("(POST) /submit");
    }

    @Test
    public void shouldReadAndMemoizeContentSuppliers() throws IOException {
        AtomicInteger reads = new AtomicInteger();
        Supplier<ByteArrayInputStream> source = () -> {
            reads.incrementAndGet();
            return new ByteArrayInputStream("repeatable".getBytes(StandardCharsets.UTF_8));
        };

        Supplier<InputStream> memoized = Contents.memoize(source::get);

        assertThat(Contents.utf8String(memoized)).isEqualTo("repeatable");
        assertThat(Contents.utf8String(memoized)).isEqualTo("repeatable");
        assertThat(reads).hasValue(1);

        try (Reader reader = Contents.utf8Reader(Contents.utf8String("abc"))) {
            char[] buffer = new char[3];
            assertThat(reader.read(buffer)).isEqualTo(3);
            assertThat(new String(buffer)).isEqualTo("abc");
        }
    }

    @Test
    public void shouldSerializeAndDeserializeJsonHttpBodies() {
        HttpResponse response = new HttpResponse()
            .setHeader("Content-Type", "application/json; charset=utf-8")
            .setContent(Contents.asJson(Map.of(
                "value", Map.of(
                    "ready", true,
                    "count", 3,
                    "name", "selenium"))));

        Map<?, ?> decoded = Contents.fromJson(response, Map.class);
        Map<?, ?> value = (Map<?, ?>) decoded.get("value");

        assertThat(value.get("ready")).isEqualTo(true);
        assertThat(value.get("name")).isEqualTo("selenium");
        assertThat(((Number) value.get("count")).intValue()).isEqualTo(3);
        assertThat(response.toString()).contains("200:", "selenium");
    }

    @Test
    public void shouldParseFormUrlEncodedDataInRequestOrder() {
        HttpRequest request = new HttpRequest(HttpMethod.POST, "/form")
            .setHeader("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            .setContent(Contents.utf8String("name=Jane+Doe&name=John%20Roe&empty=&encoded=%C3%A9"));

        Optional<Map<String, List<String>>> data = FormEncodedData.getData(request);

        assertThat(data).isPresent();
        assertThat(data.orElseThrow())
            .containsEntry("name", List.of("Jane Doe", "John Roe"))
            .containsEntry("empty", List.of(""))
            .containsEntry("encoded", List.of("\u00e9"));

        HttpRequest jsonRequest = new HttpRequest(HttpMethod.POST, "/json")
            .setHeader("Content-Type", "application/json")
            .setContent(Contents.utf8String("{}"));

        assertThat(FormEncodedData.getData(jsonRequest)).isEmpty();
    }

    @Test
    public void shouldMatchUrlTemplatesAndExposeParameters() {
        UrlTemplate template = new UrlTemplate("/session/{sessionId}/element/{elementId}");

        UrlTemplate.Match match = template.match("/session/abc-123/element/button-1");

        assertThat(match).isNotNull();
        assertThat(match.getUrl()).isEqualTo("/session/abc-123/element/button-1");
        assertThat(match.getParameters())
            .containsEntry("sessionId", "abc-123")
            .containsEntry("elementId", "button-1");
        assertThat(template.match("/session/abc-123/element/button-1/value")).isNull();
        assertThat(template.match(null)).isNull();
    }

    @Test
    public void shouldRouteTemplatizedRequestsAndReturnNotFoundForMisses() {
        Route route = Route.post("/session/{sessionId}/url")
            .to(params -> req -> {
                String body = params.get("sessionId") + ":" + Contents.utf8String(req.getContent());
                return new HttpResponse()
                    .setStatus(HTTP_CREATED)
                    .setContent(Contents.utf8String(body));
            });

        HttpRequest matching = new HttpRequest(HttpMethod.POST, "/session/abc-123/url")
            .setContent(Contents.utf8String("https://example.test"));
        HttpResponse created = route.execute(matching);

        assertThat(route.matches(matching)).isTrue();
        assertThat(created.getStatus()).isEqualTo(HTTP_CREATED);
        assertThat(Contents.utf8String(created.getContent())).isEqualTo("abc-123:https://example.test");

        HttpResponse missing = route.execute(new HttpRequest(HttpMethod.GET, "/session/abc-123/url"));

        assertThat(missing.getStatus()).isEqualTo(HTTP_NOT_FOUND);
        assertThat(Contents.utf8String(missing.getContent()))
            .contains("unknown command", "Unable to find handler");
    }

    @Test
    public void shouldComposeNestedFallbackAndCombinedRoutes() {
        Route nested = Route.prefix("/wd/hub")
            .to(Route.post("/session").to(params -> req -> new HttpResponse()
                .setContent(Contents.utf8String(UrlPath.relativeToContext(req, "/status")))));
        Route fallback = Route.get("/status")
            .to(() -> req -> new HttpResponse().setContent(Contents.utf8String("fallback")));
        Route first = Route.get("/same")
            .to(() -> req -> new HttpResponse().setContent(Contents.utf8String("first")));
        Route second = Route.get("/same")
            .to(() -> req -> new HttpResponse().setContent(Contents.utf8String("second")));

        HttpResponse nestedResponse = nested.execute(new HttpRequest(HttpMethod.POST, "/wd/hub/session"));
        HttpResponse fallbackResponse = nested.fallbackTo(() -> fallback)
            .execute(new HttpRequest(HttpMethod.GET, "/status"));
        HttpResponse combinedResponse = Route.combine(first, second)
            .execute(new HttpRequest(HttpMethod.GET, "/same"));

        assertThat(Contents.utf8String(nestedResponse.getContent())).isEqualTo("/wd/hub/status");
        assertThat(Contents.utf8String(fallbackResponse.getContent())).isEqualTo("fallback");
        assertThat(Contents.utf8String(combinedResponse.getContent())).isEqualTo("second");
    }

    @Test
    public void shouldRouteRequestsUsingCustomPredicates() {
        AtomicInteger handlerCreations = new AtomicInteger();
        Route route = Route.matching(req -> req.getMethod() == HttpMethod.OPTIONS
                && "probe".equals(req.getHeader("X-Route")))
            .to(() -> {
                handlerCreations.incrementAndGet();
                return req -> new HttpResponse()
                    .setContent(Contents.utf8String(req.getMethod() + ":" + req.getUri()));
            });

        HttpRequest matching = new HttpRequest(HttpMethod.OPTIONS, "/custom")
            .setHeader("X-Route", "probe");
        HttpRequest nonMatching = new HttpRequest(HttpMethod.OPTIONS, "/custom")
            .setHeader("X-Route", "other");

        assertThat(route.matches(matching)).isTrue();
        assertThat(route.matches(nonMatching)).isFalse();

        HttpResponse missing = route.execute(nonMatching);
        assertThat(missing.getStatus()).isEqualTo(HTTP_NOT_FOUND);
        assertThat(handlerCreations).hasValue(0);

        HttpResponse response = route.execute(matching);
        assertThat(response.getStatus()).isEqualTo(HTTP_OK);
        assertThat(Contents.utf8String(response.getContent())).isEqualTo("OPTIONS:/custom");
        assertThat(handlerCreations).hasValue(1);
    }

    @Test
    public void shouldApplyFiltersAroundHandlersAndPreserveUserAgent() {
        List<String> calls = new ArrayList<>();
        Filter requestFilter = next -> req -> {
            calls.add("request");
            req.addHeader("X-Filter", "before");
            HttpResponse response = next.execute(req);
            calls.add("response");
            return response.addHeader("X-Filter", "after");
        };
        HttpHandler handler = req -> new HttpResponse()
            .setContent(Contents.utf8String(req.getHeader("X-Filter") + ":" + req.getHeader("User-Agent")));

        HttpResponse response = handler.with(requestFilter.andThen(new AddSeleniumUserAgent()))
            .execute(new HttpRequest(HttpMethod.GET, "/filtered"));

        assertThat(calls).containsExactly("request", "response");
        assertThat(response.getHeader("X-Filter")).isEqualTo("after");
        assertThat(Contents.utf8String(response.getContent()))
            .startsWith("before:selenium/")
            .contains("(java ");

        HttpRequest preset = new HttpRequest(HttpMethod.GET, "/filtered")
            .setHeader("User-Agent", "custom-agent");
        HttpResponse preserved = handler.with(new AddSeleniumUserAgent()).execute(preset);

        assertThat(Contents.utf8String(preserved.getContent())).isEqualTo("null:custom-agent");
    }

    @Test
    public void shouldLogHttpExchangesAndKeepBodiesReadable() {
        Logger logger = DumpHttpExchangeFilter.LOG;
        Level originalLevel = logger.getLevel();
        boolean originalUseParentHandlers = logger.getUseParentHandlers();
        RecordingLogHandler logHandler = new RecordingLogHandler();
        logHandler.setLevel(Level.ALL);
        logger.addHandler(logHandler);
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);

        try {
            AtomicInteger requestReads = new AtomicInteger();
            AtomicInteger responseReads = new AtomicInteger();
            HttpHandler handler = req -> {
                assertThat(Contents.utf8String(req.getContent())).isEqualTo("request-body");
                return new HttpResponse()
                    .setStatus(HTTP_CREATED)
                    .addHeader("X-Response", "handled")
                    .setContent(() -> {
                        int invocation = responseReads.incrementAndGet();
                        String body = invocation == 1 ? "response-body" : "unexpected-response";
                        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
                    });
            };
            HttpRequest request = new HttpRequest(HttpMethod.POST, "/log")
                .addHeader("X-Request", "enabled")
                .setContent(() -> {
                    int invocation = requestReads.incrementAndGet();
                    String body = invocation == 1 ? "request-body" : "unexpected-request";
                    return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
                });

            HttpResponse response = handler.with(new DumpHttpExchangeFilter(Level.INFO)).execute(request);

            assertThat(response.getStatus()).isEqualTo(HTTP_CREATED);
            assertThat(Contents.utf8String(response.getContent())).isEqualTo("response-body");
            assertThat(Contents.utf8String(request.getContent())).isEqualTo("request-body");
            assertThat(requestReads).hasValue(1);
            assertThat(responseReads).hasValue(1);
            assertThat(logHandler.messages).hasSize(2);
            assertThat(logHandler.messages.get(0))
                .contains("HTTP Request: (POST) /log", "X-Request: enabled", "request-body");
            assertThat(logHandler.messages.get(1))
                .contains("HTTP Response: Status code: 201", "X-Response: handled", "response-body");
        } finally {
            logger.removeHandler(logHandler);
            logger.setLevel(originalLevel);
            logger.setUseParentHandlers(originalUseParentHandlers);
            logHandler.close();
        }
    }

    @Test
    public void shouldExposeImmutableStyleClientConfigValues() throws Exception {
        URI baseUri = new URI("http://localhost:4444/wd/hub");
        Proxy proxy = new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.test", 8080));
        UsernameAndPassword credentials = new UsernameAndPassword("user", "secret");
        Filter addTraceHeader = next -> req -> next.execute(req.addHeader("X-Trace", "enabled"));

        ClientConfig config = ClientConfig.defaultConfig()
            .baseUri(baseUri)
            .connectionTimeout(Duration.ofMillis(250))
            .readTimeout(Duration.ofSeconds(2))
            .proxy(proxy)
            .authenticateAs(credentials)
            .withFilter(addTraceHeader);

        assertThat(config.baseUri()).isEqualTo(baseUri);
        assertThat(config.baseUrl().toExternalForm()).isEqualTo("http://localhost:4444/wd/hub");
        assertThat(config.connectionTimeout()).isEqualTo(Duration.ofMillis(250));
        assertThat(config.readTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(config.proxy()).isSameAs(proxy);
        assertThat(config.credentials()).isSameAs(credentials);
        assertThat(config.toString()).contains("baseUri=http://localhost:4444/wd/hub", "connectionTimeout=PT0.25S");

        HttpResponse response = config.filter().andFinally(req -> new HttpResponse()
            .setContent(Contents.utf8String(req.getHeader("X-Trace") + ":" + req.getHeader("User-Agent"))))
            .execute(new HttpRequest(HttpMethod.GET, "/config"));

        assertThat(Contents.utf8String(response.getContent()))
            .startsWith("enabled:selenium/");
        assertThat(ClientConfig.defaultConfig().baseUri()).isNull();
    }

    @Test
    public void shouldRetryTransientServerErrorsUntilSuccessful() {
        AtomicInteger attempts = new AtomicInteger();
        HttpHandler flaky = req -> {
            if (attempts.incrementAndGet() < 3) {
                return new HttpResponse().setStatus(HTTP_UNAVAILABLE);
            }
            return new HttpResponse().setContent(Contents.utf8String("ok"));
        };

        HttpResponse response = new RetryRequest().apply(flaky)
            .execute(new HttpRequest(HttpMethod.GET, "/eventually-ok"));

        assertThat(attempts).hasValue(3);
        assertThat(response.getStatus()).isEqualTo(HTTP_OK);
        assertThat(Contents.utf8String(response.getContent())).isEqualTo("ok");
    }

    @Test
    public void shouldReturnFallbackResponseWhenRetriesAreExhausted() {
        AtomicInteger attempts = new AtomicInteger();
        HttpHandler unavailable = req -> {
            attempts.incrementAndGet();
            return new HttpResponse().setStatus(HTTP_UNAVAILABLE);
        };

        HttpResponse response = new RetryRequest().apply(unavailable)
            .execute(new HttpRequest(HttpMethod.GET, "/always-unavailable"));

        assertThat(attempts).hasValue(3);
        assertThat(response.getStatus()).isEqualTo(HTTP_UNAVAILABLE);
    }

    @Test
    public void shouldModelHttpResponsesRemoteCallsAndConnectionFailures() {
        ClientConfig config = ClientConfig.defaultConfig().baseUri(URI.create("http://localhost:4444"));
        TestRemoteCall remoteCall = new TestRemoteCall(config);

        HttpResponse response = remoteCall.execute(new HttpRequest(HttpMethod.GET, "/ping"));
        response.setTargetHost("grid.local");

        assertThat(remoteCall.getConfig()).isSameAs(config);
        assertThat(response.isSuccessful()).isTrue();
        assertThat(response.getTargetHost()).isEqualTo("grid.local");
        assertThat(response.toString()).isEqualTo("200: pong");

        HttpResponse error = new HttpResponse().setStatus(HTTP_INTERNAL_ERROR);
        assertThat(error.isSuccessful()).isFalse();
        assertThatThrownBy(() -> {
            throw new ConnectionFailedException("Unable to connect", new ConnectException("refused"));
        }).isInstanceOf(ConnectionFailedException.class)
            .hasMessageContaining("Unable to connect");
    }

    @Test
    public void shouldDispatchWebSocketMessagesAndSendConvenienceMessages() {
        RecordingListener listener = new RecordingListener();

        listener.accept(new TextMessage("hello"));
        listener.accept(new BinaryMessage(ByteBuffer.wrap(new byte[] {1, 2, 3})));
        listener.accept(new CloseMessage(1000, "done"));
        listener.accept(new CloseMessage(1001, null));

        assertThat(listener.events).containsExactly(
            "text:hello",
            "binary:[1, 2, 3]",
            "close:1000:done",
            "close:1001:");

        RecordingWebSocket socket = new RecordingWebSocket();
        WebSocket returnedFromText = socket.sendText("payload");
        WebSocket returnedFromBinary = socket.sendBinary(new byte[] {9, 8});
        socket.close();

        assertThat(returnedFromText).isSameAs(socket);
        assertThat(returnedFromBinary).isSameAs(socket);
        assertThat(socket.closed).isTrue();
        assertThat(socket.sent).hasSize(2);
        assertThat(((TextMessage) socket.sent.get(0)).text()).isEqualTo("payload");
        assertThat(((BinaryMessage) socket.sent.get(1)).data()).containsExactly(9, 8);
    }

    @Test
    public void shouldCopyBinaryMessageInputData() {
        byte[] original = new byte[] {4, 5, 6};
        BinaryMessage fromArray = new BinaryMessage(original);
        original[0] = 99;

        BinaryMessage fromBuffer = new BinaryMessage(ByteBuffer.wrap(new byte[] {7, 8, 9}));

        assertThat(fromArray.data()).containsExactly(4, 5, 6);
        assertThat(fromBuffer.data()).containsExactly(7, 8, 9);
        assertThat(new TextMessage(new StringBuilder("text")).text()).isEqualTo("text");
    }

    private static class TestRemoteCall extends RemoteCall {
        TestRemoteCall(ClientConfig config) {
            super(config);
        }

        @Override
        public HttpResponse execute(HttpRequest req) {
            return new HttpResponse().setContent(Contents.utf8String("pong"));
        }
    }

    private static class RecordingLogHandler extends Handler {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (isLoggable(record)) {
                messages.add(record.getMessage());
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    private static class RecordingListener implements WebSocket.Listener {
        private final List<String> events = new ArrayList<>();

        @Override
        public void onBinary(byte[] data) {
            events.add("binary:" + Arrays.toString(data));
        }

        @Override
        public void onClose(int code, String reason) {
            events.add("close:" + code + ":" + reason);
        }

        @Override
        public void onText(CharSequence data) {
            events.add("text:" + data);
        }
    }

    private static class RecordingWebSocket implements WebSocket {
        private final List<Message> sent = new ArrayList<>();
        private boolean closed;

        @Override
        public WebSocket send(Message message) {
            sent.add(message);
            return this;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
