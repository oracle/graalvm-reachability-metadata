/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j_http_client;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.FormDataFile;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.http.client.sse.DefaultServerSentEventParser;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventContext;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.AbstractLogger;

public class Langchain4j_http_clientTest {

    @Test
    @Timeout(55)
    void buildsRequestsWithEncodedQueriesCaseInsensitiveHeadersAndBodies() {
        HttpRequest request = HttpRequest.builder()
                .method(HttpMethod.POST)
                .url("https://localhost/api/", "/messages")
                .addQueryParam("search term", "native image")
                .addQueryParams(Map.of("page", "2"))
                .addHeader("Content-Type", "application/json")
                .addHeaders(Map.of("X-Request-Id", "request-17"))
                .body("{\"message\":\"hello\"}")
                .build();

        assertThat(request.method()).isEqualTo(HttpMethod.POST);
        assertThat(request.url()).isEqualTo("https://localhost/api/messages?search+term=native+image&page=2");
        assertThat(request.headers().get("content-type")).containsExactly("application/json");
        assertThat(request.headers().get("X-REQUEST-ID")).containsExactly("request-17");
        assertThat(request.body()).isEqualTo("{\"message\":\"hello\"}");
    }

    @Test
    @Timeout(55)
    void buildsMultipartFormRequestsWithTextAndBinaryParts() {
        byte[] content = "document body".getBytes(UTF_8);
        HttpRequest request = HttpRequest.builder()
                .method(HttpMethod.POST)
                .url("https://localhost/upload")
                .addFormDataField("description", "integration document")
                .addFormDataFile("document", "document.txt", "text/plain", content)
                .build();

        assertThat(request.formDataFields()).containsEntry("description", "integration document");
        assertThat(request.formDataFiles())
                .containsEntry("document", new FormDataFile("document.txt", "text/plain", content));
        FormDataFile file = request.formDataFiles().get("document");
        assertThat(file.fileName()).isEqualTo("document.txt");
        assertThat(file.contentType()).isEqualTo("text/plain");
        assertThat(file.content()).containsExactly(content);
        assertThat(request.body()).isNull();
    }

    @Test
    @Timeout(55)
    void decodesSuccessfulResponsesUsingContentTypeCharset() {
        byte[] body = "ol\u00E1 mundo".getBytes(ISO_8859_1);
        SuccessfulHttpResponse response = SuccessfulHttpResponse.builder()
                .statusCode(201)
                .headers(Map.of(
                        "content-type", List.of("text/plain; charset=\"ISO-8859-1\""),
                        "X-Response-Id", List.of("response-23")))
                .body(body)
                .build();

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.contentType()).isEqualTo("text/plain; charset=\"ISO-8859-1\"");
        assertThat(response.headers().get("CONTENT-TYPE"))
                .containsExactly("text/plain; charset=\"ISO-8859-1\"");
        assertThat(response.headers().get("x-response-id")).containsExactly("response-23");
        assertThat(response.body()).isEqualTo("ol\u00E1 mundo");
        assertThat(response.bodyBytes()).containsExactly(body);
    }

    @Test
    @Timeout(55)
    void executesSynchronousAndStreamingRequestsThroughHttpClientApi() {
        HttpRequest request = HttpRequest.builder()
                .method(HttpMethod.POST)
                .url("https://localhost/events")
                .body("start")
                .build();
        InMemoryHttpClient client = new InMemoryHttpClient();

        SuccessfulHttpResponse response = client.execute(request);
        RecordingListener listener = new RecordingListener();
        client.execute(request, listener);

        assertThat(client.requests()).containsExactly(request, request);
        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.body()).isEqualTo("accepted: start");
        assertThat(listener.openedResponse().statusCode()).isEqualTo(202);
        assertThat(listener.openedResponse().body()).isEqualTo("accepted: start");
        assertThat(listener.events())
                .containsExactly(new ServerSentEvent("message", "first line\nsecond line"));
        assertThat(listener.contexts()).hasSize(1);
        assertThat(listener.contexts().get(0).parsingHandle().isCancelled()).isFalse();
        assertThat(listener.error()).isNull();
        assertThat(listener.closed()).isTrue();
    }

    @Test
    @Timeout(55)
    void logsRequestsAndResponsesWhileDelegatingHttpExecution() {
        InMemoryHttpClient delegate = new InMemoryHttpClient();
        RecordingLogger logger = new RecordingLogger();
        HttpClient client = new LoggingHttpClient(delegate, true, true, logger);
        HttpRequest request = HttpRequest.builder()
                .method(HttpMethod.POST)
                .url("https://localhost/messages")
                .addHeader("Authorization", "secret-token")
                .body("hello")
                .build();

        SuccessfulHttpResponse response = client.execute(request);

        assertThat(delegate.requests()).containsExactly(request);
        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.body()).isEqualTo("accepted: hello");
        assertThat(logger.events()).hasSize(2);
        assertThat(logger.events().get(0).level()).isEqualTo(Level.INFO);
        assertThat(logger.events().get(0).message()).contains("HTTP request");
        assertThat(logger.events().get(0).arguments().get(2))
                .asString()
                .contains("[Authorization: secre...en]")
                .doesNotContain("secret-token");
        assertThat(logger.events().get(1).level()).isEqualTo(Level.INFO);
        assertThat(logger.events().get(1).message()).contains("HTTP response");
        assertThat(logger.events().get(1).arguments()).contains(202, "accepted: hello");
    }

    @Test
    @Timeout(55)
    void logsAsynchronousRequestsAndResponsesWhileDelegatingExecution() throws Exception {
        InMemoryHttpClient delegate = new InMemoryHttpClient();
        RecordingLogger logger = new RecordingLogger();
        HttpClient client = new LoggingHttpClient(delegate, true, true, logger);
        HttpRequest request = HttpRequest.builder()
                .method(HttpMethod.POST)
                .url("https://localhost/messages")
                .body("hello asynchronously")
                .build();

        SuccessfulHttpResponse response = client.executeAsync(request).get(10, TimeUnit.SECONDS);

        assertThat(delegate.requests()).containsExactly(request);
        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.body()).isEqualTo("accepted: hello asynchronously");
        assertThat(logger.events()).hasSize(2);
        assertThat(logger.events().get(0).message()).contains("HTTP request");
        assertThat(logger.events().get(1).message()).contains("HTTP response");
        assertThat(logger.events().get(1).arguments()).contains(202, "accepted: hello asynchronously");
    }

    @Test
    @Timeout(55)
    void cancelsBlockingSseParsingFromListenerContext() {
        String stream = "data: first\n\ndata: second\n\n";
        List<ServerSentEvent> events = new ArrayList<>();
        AtomicReference<ServerSentEventContext> contextSeen = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        new DefaultServerSentEventParser().parse(
                new ByteArrayInputStream(stream.getBytes(UTF_8)), new ServerSentEventListener() {
                    @Override
                    public void onEvent(ServerSentEvent event, ServerSentEventContext context) {
                        events.add(event);
                        contextSeen.set(context);
                        context.parsingHandle().cancel();
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        error.set(throwable);
                    }
                });

        assertThat(events).containsExactly(new ServerSentEvent(null, "first"));
        assertThat(contextSeen.get()).isNotNull();
        assertThat(contextSeen.get().parsingHandle().isCancelled()).isTrue();
        assertThat(error.get()).isNull();
    }

    @Test
    @Timeout(55)
    void incrementallyParsesSplitUtf8AndFlushesTrailingEvents() {
        ServerSentEventParser.Incremental parser = new DefaultServerSentEventParser().incremental();
        byte[] firstEvent = "event: update\r\ndata: caf\u00E9\r\n\r\n".getBytes(UTF_8);
        int splitInsideAccent = firstEvent.length - 5;

        assertThat(parser.feed(ByteBuffer.wrap(Arrays.copyOfRange(firstEvent, 0, splitInsideAccent))))
                .isEmpty();
        assertThat(parser.feed(ByteBuffer.wrap(Arrays.copyOfRange(firstEvent, splitInsideAccent, firstEvent.length))))
                .containsExactly(new ServerSentEvent("update", "caf\u00E9"));
        assertThat(parser.feed(ByteBuffer.wrap("data: trailing event".getBytes(UTF_8))))
                .isEmpty();
        assertThat(parser.flush()).containsExactly(new ServerSentEvent(null, "trailing event"));
    }

    @Test
    @Timeout(55)
    void reportsInputFailuresToSseListener() {
        AtomicReference<Throwable> error = new AtomicReference<>();
        ServerSentEventListener listener = new ServerSentEventListener() {
            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
            }
        };

        new DefaultServerSentEventParser().parse(new FailingInputStream(), listener);

        assertThat(error.get()).isInstanceOf(IOException.class).hasMessage("stream interrupted");
    }

    private static final class InMemoryHttpClient implements HttpClient {

        private final List<HttpRequest> requests = new ArrayList<>();

        @Override
        public SuccessfulHttpResponse execute(HttpRequest request) {
            requests.add(request);
            return SuccessfulHttpResponse.builder()
                    .statusCode(202)
                    .headers(Map.of("Content-Type", List.of("text/plain; charset=UTF-8")))
                    .body("accepted: " + request.body())
                    .build();
        }

        @Override
        public CompletableFuture<SuccessfulHttpResponse> executeAsync(HttpRequest request) {
            return CompletableFuture.completedFuture(execute(request));
        }

        @Override
        public void execute(
                HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
            SuccessfulHttpResponse streamingResponse = execute(request);
            listener.onOpen(streamingResponse);
            parser.parse(
                    new ByteArrayInputStream(
                            "event: message\ndata: first line\ndata: second line\n\n".getBytes(UTF_8)),
                    listener);
            listener.onClose();
        }

        List<HttpRequest> requests() {
            return List.copyOf(requests);
        }
    }

    private static final class RecordingListener implements ServerSentEventListener {

        private final List<ServerSentEvent> events = new ArrayList<>();
        private final List<ServerSentEventContext> contexts = new ArrayList<>();
        private SuccessfulHttpResponse openedResponse;
        private Throwable error;
        private boolean closed;

        @Override
        public void onOpen(SuccessfulHttpResponse response) {
            openedResponse = response;
        }

        @Override
        public void onEvent(ServerSentEvent event, ServerSentEventContext context) {
            events.add(event);
            contexts.add(context);
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
        }

        @Override
        public void onClose() {
            closed = true;
        }

        SuccessfulHttpResponse openedResponse() {
            return openedResponse;
        }

        List<ServerSentEvent> events() {
            return List.copyOf(events);
        }

        List<ServerSentEventContext> contexts() {
            return List.copyOf(contexts);
        }

        Throwable error() {
            return error;
        }

        boolean closed() {
            return closed;
        }
    }

    private static final class RecordingLogger extends AbstractLogger {

        private final List<LogEvent> events = new ArrayList<>();

        private RecordingLogger() {
            name = "recording-logger";
        }

        @Override
        public boolean isTraceEnabled() {
            return false;
        }

        @Override
        public boolean isTraceEnabled(Marker marker) {
            return false;
        }

        @Override
        public boolean isDebugEnabled() {
            return false;
        }

        @Override
        public boolean isDebugEnabled(Marker marker) {
            return false;
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public boolean isInfoEnabled(Marker marker) {
            return true;
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public boolean isWarnEnabled(Marker marker) {
            return true;
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public boolean isErrorEnabled(Marker marker) {
            return true;
        }

        @Override
        protected String getFullyQualifiedCallerName() {
            return getClass().getName();
        }

        @Override
        protected void handleNormalizedLoggingCall(
                Level level, Marker marker, String message, Object[] arguments, Throwable throwable) {
            events.add(new LogEvent(level, message, arguments == null ? List.of() : Arrays.asList(arguments)));
        }

        List<LogEvent> events() {
            return List.copyOf(events);
        }
    }

    private record LogEvent(Level level, String message, List<Object> arguments) {}

    private static final class FailingInputStream extends InputStream {

        @Override
        public int read() throws IOException {
            throw new IOException("stream interrupted");
        }
    }
}
