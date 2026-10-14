/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_projectreactor_netty.reactor_netty_http;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.multipart.HttpData;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;
import reactor.netty.http.server.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;

public class Reactor_netty_httpTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String LOOPBACK_ADDRESS = "127.0.0.1";

    @Test
    void routesPathParametersAndResponseHeaders() {
        DisposableServer server = bind(HttpServer.create()
                .route(routes -> routes
                        .get("/hello/{name}", (request, response) -> response
                                .header("X-Route-Name", request.param("name"))
                                .sendString(Mono.just("Hello " + request.param("name"))))
                        .get("/health", (request, response) -> response
                                .status(HttpResponseStatus.NO_CONTENT)
                                .send())));
        try {
            ClientResult result = clientFor(server)
                    .get()
                    .uri("/hello/Reactor")
                    .responseSingle((response, content) -> content.asString(StandardCharsets.UTF_8)
                            .map(body -> ClientResult.from(response, body)))
                    .block(TIMEOUT);

            Integer healthStatus = clientFor(server)
                    .get()
                    .uri("/health")
                    .response()
                    .map(response -> response.status().code())
                    .block(TIMEOUT);

            assertThat(result).isNotNull();
            assertThat(result.statusCode).isEqualTo(200);
            assertThat(result.header("X-Route-Name")).isEqualTo("Reactor");
            assertThat(result.body).isEqualTo("Hello Reactor");
            assertThat(healthStatus).isEqualTo(204);
        } finally {
            server.disposeNow(TIMEOUT);
        }
    }

    @Test
    void postsAggregatedRequestBodyAndCustomStatus() {
        DisposableServer server = bind(HttpServer.create()
                .route(routes -> routes.post("/uppercase", (request, response) -> request.receive()
                        .aggregate()
                        .asString(StandardCharsets.UTF_8)
                        .flatMap(body -> response
                                .status(HttpResponseStatus.CREATED)
                                .header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN)
                                .sendString(Mono.just(body.toUpperCase(Locale.ROOT)), StandardCharsets.UTF_8)
                                .then()))));
        try {
            ClientResult result = clientFor(server)
                    .headers(headers -> headers.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN))
                    .post()
                    .uri("/uppercase")
                    .send(ByteBufFlux.fromString(Mono.just("reactor netty")))
                    .responseSingle((response, content) -> content.asString(StandardCharsets.UTF_8)
                            .map(body -> ClientResult.from(response, body)))
                    .block(TIMEOUT);

            assertThat(result).isNotNull();
            assertThat(result.statusCode).isEqualTo(201);
            assertThat(result.header(HttpHeaderNames.CONTENT_TYPE.toString())).contains("text/plain");
            assertThat(result.body).isEqualTo("REACTOR NETTY");
        } finally {
            server.disposeNow(TIMEOUT);
        }
    }

    @Test
    void exchangesCookiesAndRequestMetadata() {
        DisposableServer server = bind(HttpServer.create()
                .route(routes -> routes.get("/inspect", (request, response) -> {
                    String sessionId = request.cookies().get("session").iterator().next().value();
                    String requestId = request.requestHeaders().get("X-Request-Id");
                    String body = request.method().name() + " " + request.path() + " " + requestId + " " + sessionId;
                    return response
                            .addCookie(new DefaultCookie("seen", sessionId))
                            .header("X-Observed-Uri", request.uri())
                            .sendString(Mono.just(body));
                })));
        try {
            ClientResult result = clientFor(server)
                    .cookie(new DefaultCookie("session", "abc123"))
                    .headers(headers -> headers.set("X-Request-Id", "request-42"))
                    .get()
                    .uri("/inspect?mode=fast")
                    .responseSingle((response, content) -> content.asString(StandardCharsets.UTF_8)
                            .map(body -> ClientResult.from(response, body)))
                    .block(TIMEOUT);

            assertThat(result).isNotNull();
            assertThat(result.statusCode).isEqualTo(200);
            assertThat(result.body).isEqualTo("GET inspect request-42 abc123");
            assertThat(result.header("X-Observed-Uri")).isEqualTo("/inspect?mode=fast");
            assertThat(result.header(HttpHeaderNames.SET_COOKIE.toString())).contains("seen=abc123");
        } finally {
            server.disposeNow(TIMEOUT);
        }
    }

    @Test
    void decodesFormUrlencodedRequest() {
        DisposableServer server = bind(HttpServer.create()
                .route(routes -> routes.post("/submit", (request, response) -> request.receiveForm()
                        .collectMap(HttpData::getName, Reactor_netty_httpTest::dataValue)
                        .flatMap(form -> response
                                .header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN)
                                .sendString(Mono.just(formResponse(request.isFormUrlencoded(), form)))
                                .then()))));
        try {
            ClientResult result = clientFor(server)
                    .post()
                    .uri("/submit")
                    .sendForm((request, form) -> form.attr("name", "Reactor Netty"))
                    .responseSingle((response, content) -> content.asString(StandardCharsets.UTF_8)
                            .map(body -> ClientResult.from(response, body)))
                    .block(TIMEOUT);

            assertThat(result).isNotNull();
            assertThat(result.statusCode).isEqualTo(200);
            assertThat(result.header(HttpHeaderNames.CONTENT_TYPE.toString())).contains("text/plain");
            assertThat(result.body).isEqualTo("form=true;name=Reactor Netty");
        } finally {
            server.disposeNow(TIMEOUT);
        }
    }

    @Test
    void followsRedirectsAndReceivesCompressedContent() {
        String payload = "reactor-netty-native-image ".repeat(200);
        DisposableServer server = bind(HttpServer.create()
                .compress(true)
                .route(routes -> routes
                        .get("/redirect", (request, response) -> response.sendRedirect("/compressed"))
                        .get("/compressed", (request, response) -> response.sendString(Mono.just(payload)))));
        try {
            ClientResult result = clientFor(server)
                    .compress(true)
                    .followRedirect(true)
                    .get()
                    .uri("/redirect")
                    .responseSingle((response, content) -> content.asString(StandardCharsets.UTF_8)
                            .map(body -> ClientResult.from(response, body)))
                    .block(TIMEOUT);

            assertThat(result).isNotNull();
            assertThat(result.statusCode).isEqualTo(200);
            assertThat(result.body).isEqualTo(payload);
        } finally {
            server.disposeNow(TIMEOUT);
        }
    }

    @Test
    void streamsServerSentEvents() {
        DisposableServer server = bind(HttpServer.create()
                .route(routes -> routes.get("/events", (request, response) -> response.sse()
                        .sendString(Flux.just("data:first\n\n", "data:second\n\n"), StandardCharsets.UTF_8))));
        try {
            ClientResult result = clientFor(server)
                    .get()
                    .uri("/events")
                    .responseSingle((response, content) -> content.asString(StandardCharsets.UTF_8)
                            .map(body -> ClientResult.from(response, body)))
                    .block(TIMEOUT);

            assertThat(result).isNotNull();
            assertThat(result.statusCode).isEqualTo(200);
            assertThat(result.header(HttpHeaderNames.CONTENT_TYPE.toString()))
                    .contains(HttpHeaderValues.TEXT_EVENT_STREAM.toString());
            assertThat(result.body).isEqualTo("data:first\n\ndata:second\n\n");
        } finally {
            server.disposeNow(TIMEOUT);
        }
    }

    @Test
    void servesStaticFileRoute() throws IOException {
        Path staticFile = Files.createTempFile("reactor-netty-http", ".txt");
        Files.writeString(staticFile, "static file from reactor netty", StandardCharsets.UTF_8);

        DisposableServer server = bind(HttpServer.create()
                .route(routes -> routes.file("/asset.txt", staticFile)));
        try {
            ClientResult result = clientFor(server)
                    .get()
                    .uri("/asset.txt")
                    .responseSingle((response, content) -> content.asString(StandardCharsets.UTF_8)
                            .map(body -> ClientResult.from(response, body)))
                    .block(TIMEOUT);

            assertThat(result).isNotNull();
            assertThat(result.statusCode).isEqualTo(200);
            assertThat(result.body).isEqualTo("static file from reactor netty");
        } finally {
            server.disposeNow(TIMEOUT);
            Files.deleteIfExists(staticFile);
        }
    }

    @Test
    void exchangesWebSocketMessages() {
        DisposableServer server = bind(HttpServer.create()
                .route(routes -> routes.ws("/ws", (inbound, outbound) -> outbound.sendString(
                        inbound.receive()
                                .asString(StandardCharsets.UTF_8)
                                .map(message -> message.toUpperCase(Locale.ROOT)),
                        StandardCharsets.UTF_8))));
        try {
            String reply = HttpClient.create()
                    .responseTimeout(TIMEOUT)
                    .websocket()
                    .uri("ws://" + LOOPBACK_ADDRESS + ":" + server.port() + "/ws")
                    .handle((inbound, outbound) -> outbound
                            .sendString(Mono.just("websocket"), StandardCharsets.UTF_8)
                            .then()
                            .thenMany(inbound.receive().asString(StandardCharsets.UTF_8).take(1)))
                    .next()
                    .block(TIMEOUT);

            assertThat(reply).isEqualTo("WEBSOCKET");
        } finally {
            server.disposeNow(TIMEOUT);
        }
    }

    private static DisposableServer bind(HttpServer server) {
        return server.host(LOOPBACK_ADDRESS)
                .port(0)
                .bindNow(TIMEOUT);
    }

    private static HttpClient clientFor(DisposableServer server) {
        return HttpClient.create()
                .baseUrl("http://" + LOOPBACK_ADDRESS + ":" + server.port())
                .responseTimeout(TIMEOUT);
    }

    private static String dataValue(HttpData data) {
        try {
            return data.getString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read form field " + data.getName(), e);
        }
    }

    private static String formResponse(boolean formUrlencoded, Map<String, String> form) {
        return "form=" + formUrlencoded + ";name=" + form.get("name");
    }

    private static final class ClientResult {
        private final int statusCode;
        private final HttpHeaders headers;
        private final String body;

        private ClientResult(int statusCode, HttpHeaders headers, String body) {
            this.statusCode = statusCode;
            this.headers = headers;
            this.body = body;
        }

        private static ClientResult from(HttpClientResponse response, String body) {
            HttpHeaders headers = new DefaultHttpHeaders().set(response.responseHeaders());
            return new ClientResult(response.status().code(), headers, body);
        }

        private String header(String name) {
            return headers.get(name);
        }
    }
}
