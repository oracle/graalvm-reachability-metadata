/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_http_server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.type.Argument;
import io.micronaut.http.BasicAuth;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.http.server.types.files.SystemFile;
import io.micronaut.runtime.server.EmbeddedServer;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Controller("/server-test")
public class Micronaut_http_serverTest {
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final Argument<Map<String, Object>> JSON_MAP = Argument.mapOf(String.class, Object.class);
    private static final byte[] STREAM_CONTENT = "streamed server content".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(55)
    void bindsJsonAuthenticationLocaleAndRequestDataThroughAnAnnotatedRoute() {
        Map<String, Object> properties = Map.ofEntries(
                Map.entry("micronaut.server.port", -1),
                Map.entry("micronaut.server.locale-resolution.header", true),
                Map.entry("micronaut.server.locale-resolution.default-locale", "en-US"));

        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, properties, Environment.TEST);
                HttpClient client = HttpClient.create(server.getURL(), clientConfiguration())) {
            HttpRequest<?> request = HttpRequest.POST(
                            "/server-test/orders/17?priority=high",
                            Map.of("item", "notebook", "quantity", 3))
                    .basicAuth("ada", "correct-horse")
                    .header(HttpHeaders.ACCEPT_LANGUAGE, "fr-CA,fr;q=0.8")
                    .header("X-Request-Id", "request-17")
                    .contentType(MediaType.APPLICATION_JSON_TYPE)
                    .accept(MediaType.APPLICATION_JSON_TYPE);

            HttpResponse<Map<String, Object>> response = client.toBlocking().exchange(request, JSON_MAP);

            assertThat(response.code()).isEqualTo(HttpStatus.CREATED.getCode());
            assertThat(response.getContentType()).contains(MediaType.APPLICATION_JSON_TYPE);
            assertThat(response.getHeaders().get("X-Observed-By")).isEqualTo("server-filter");
            assertThat(response.body())
                    .containsEntry("orderId", 17)
                    .containsEntry("priority", "high")
                    .containsEntry("item", "notebook")
                    .containsEntry("quantity", 3)
                    .containsEntry("username", "ada")
                    .containsEntry("password", "correct-horse")
                    .containsEntry("locale", "fr-CA")
                    .containsEntry("requestId", "request-17")
                    .containsEntry("method", "POST");
        }
    }

    @Test
    @Timeout(55)
    void rendersSupportedClientErrorsForUnsatisfiedMissingAndDisallowedRoutes() {
        Map<String, Object> properties = Map.of("micronaut.server.port", -1);

        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, properties, Environment.TEST);
                HttpClient client = HttpClient.create(server.getURL(), clientConfiguration())) {
            BlockingHttpClient blockingClient = client.toBlocking();

            HttpClientResponseException unsatisfied =
                    exchangeError(blockingClient, HttpRequest.GET("/server-test/required"));
            assertThat(unsatisfied.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
            assertThat(unsatisfied.getResponse().getContentType()).contains(MediaType.APPLICATION_JSON_TYPE);
            assertThat(unsatisfied.getResponse().getBody(String.class))
                    .hasValueSatisfying(body -> assertThat(body).contains("Required QueryValue", "count"));

            HttpClientResponseException disallowed = exchangeError(
                    blockingClient, HttpRequest.POST("/server-test/required", "ignored"));
            assertThat(disallowed.getStatus().getCode())
                    .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.getCode());

            HttpClientResponseException missing =
                    exchangeError(blockingClient, HttpRequest.GET("/server-test/does-not-exist"));
            assertThat(missing.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
            assertThat(missing.getResponse().getBody(String.class))
                    .hasValueSatisfying(body -> assertThat(body).contains("Not Found"));
        }
    }

    @Test
    @Timeout(55)
    void appliesConfiguredCorsPolicyToPreflightAndActualRequests() {
        String origin = "https://client.example";
        Map<String, Object> properties = Map.ofEntries(
                Map.entry("micronaut.server.port", -1),
                Map.entry("micronaut.server.cors.enabled", true),
                Map.entry("micronaut.server.cors.single-header", true),
                Map.entry("micronaut.server.cors.configurations.browser.allowed-origins", List.of(origin)),
                Map.entry("micronaut.server.cors.configurations.browser.allowed-methods", List.of("GET")),
                Map.entry(
                        "micronaut.server.cors.configurations.browser.allowed-headers", List.of("X-Trace")),
                Map.entry(
                        "micronaut.server.cors.configurations.browser.exposed-headers", List.of("X-Route")),
                Map.entry("micronaut.server.cors.configurations.browser.allow-credentials", true),
                Map.entry("micronaut.server.cors.configurations.browser.max-age", 600));

        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, properties, Environment.TEST);
                HttpClient client = HttpClient.create(server.getURL(), clientConfiguration())) {
            BlockingHttpClient blockingClient = client.toBlocking();
            HttpRequest<?> preflight = HttpRequest.create(HttpMethod.OPTIONS, "/server-test/cors")
                    .header(HttpHeaders.ORIGIN, origin)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Trace");

            HttpResponse<String> preflightResponse = blockingClient.exchange(preflight, String.class);

            assertThat(preflightResponse.code()).isEqualTo(HttpStatus.OK.getCode());
            assertThat(preflightResponse.getHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                    .isEqualTo(origin);
            assertThat(preflightResponse.getHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
                    .contains(HttpMethod.GET.name());
            assertThat(preflightResponse.getHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
                    .containsIgnoringCase("X-Trace");
            assertThat(preflightResponse.getHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
                    .isEqualTo("true");
            assertThat(preflightResponse.getHeaders().get(HttpHeaders.ACCESS_CONTROL_MAX_AGE))
                    .isEqualTo("600");

            HttpRequest<?> actual = HttpRequest.GET("/server-test/cors")
                    .header(HttpHeaders.ORIGIN, origin)
                    .header("X-Trace", "trace-9")
                    .accept(MediaType.TEXT_PLAIN_TYPE);
            HttpResponse<String> actualResponse = blockingClient.exchange(actual, String.class);

            assertThat(actualResponse.code()).isEqualTo(HttpStatus.OK.getCode());
            assertThat(actualResponse.body()).isEqualTo("cors-response");
            assertThat(actualResponse.getHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                    .isEqualTo(origin);
            assertThat(actualResponse.getHeaders().get(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
                    .containsIgnoringCase("X-Route");
            assertThat(actualResponse.getHeaders().get("X-Route")).isEqualTo("cors");
        }
    }

    @Test
    @Timeout(55)
    void servesARequestedByteRangeFromASystemFile() throws IOException {
        Path file = temporaryDirectory.resolve("range-source.txt");
        Files.writeString(file, "0123456789abcdef", StandardCharsets.UTF_8);
        String encodedPath = URLEncoder.encode(file.toString(), StandardCharsets.UTF_8);
        Map<String, Object> properties = Map.of("micronaut.server.port", -1);

        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, properties, Environment.TEST);
                HttpClient client = HttpClient.create(server.getURL(), clientConfiguration())) {
            HttpRequest<?> request = HttpRequest.GET("/server-test/file?path=" + encodedPath)
                    .header(HttpHeaders.RANGE, "bytes=4-9")
                    .accept(MediaType.TEXT_PLAIN_TYPE);

            HttpResponse<byte[]> response = client.toBlocking().exchange(request, byte[].class);

            assertThat(response.code()).isEqualTo(HttpStatus.PARTIAL_CONTENT.getCode());
            assertThat(response.body()).containsExactly("456789".getBytes(StandardCharsets.UTF_8));
            assertThat(response.getHeaders().get(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 4-9/16");
            assertThat(response.getHeaders().get(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
            assertThat(response.getHeaders().get(HttpHeaders.CONTENT_DISPOSITION))
                    .contains("attachment", "range-result.txt");
        }
    }

    @Test
    @Timeout(55)
    void streamsAnAttachmentThroughTheServerBodyWriter() {
        Map<String, Object> properties = Map.of("micronaut.server.port", -1);

        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, properties, Environment.TEST);
                HttpClient client = HttpClient.create(server.getURL(), clientConfiguration())) {
            HttpRequest<?> request = HttpRequest.GET("/server-test/stream")
                    .accept(MediaType.APPLICATION_OCTET_STREAM_TYPE);

            HttpResponse<byte[]> response = client.toBlocking().exchange(request, byte[].class);

            assertThat(response.code()).isEqualTo(HttpStatus.OK.getCode());
            assertThat(response.body()).containsExactly(STREAM_CONTENT);
            assertThat(response.getContentType()).contains(MediaType.APPLICATION_OCTET_STREAM_TYPE);
            assertThat(response.getHeaders().get(HttpHeaders.CONTENT_DISPOSITION))
                    .contains("attachment", "server report.txt");
        }
    }

    @Post(uri = "/orders/{orderId}", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public HttpResponse<Map<String, Object>> createOrder(
            @PathVariable("orderId") int orderId,
            @QueryValue("priority") String priority,
            @Body Map<String, Object> order,
            BasicAuth authentication,
            Locale locale,
            @Header("X-Request-Id") String requestId,
            HttpRequest<?> request) {
        Map<String, Object> response = Map.ofEntries(
                Map.entry("orderId", orderId),
                Map.entry("priority", priority),
                Map.entry("item", order.get("item")),
                Map.entry("quantity", order.get("quantity")),
                Map.entry("username", authentication.getUsername()),
                Map.entry("password", authentication.getPassword()),
                Map.entry("locale", locale.toLanguageTag()),
                Map.entry("requestId", requestId),
                Map.entry("method", request.getMethodName()));
        return HttpResponse.created(response);
    }

    @Get(uri = "/required", produces = MediaType.TEXT_PLAIN)
    public String required(@QueryValue("count") int count) {
        return "count=" + count;
    }

    @Get(uri = "/cors", produces = MediaType.TEXT_PLAIN)
    public HttpResponse<String> cors() {
        return HttpResponse.ok("cors-response").header("X-Route", "cors");
    }

    @Get(uri = "/file", produces = MediaType.TEXT_PLAIN)
    public SystemFile file(@QueryValue("path") String path) {
        return new SystemFile(new File(path), MediaType.TEXT_PLAIN_TYPE).attach("range-result.txt");
    }

    @Get(uri = "/stream", produces = MediaType.APPLICATION_OCTET_STREAM)
    public StreamedFile stream() {
        return new StreamedFile(
                        new ByteArrayInputStream(STREAM_CONTENT),
                        MediaType.APPLICATION_OCTET_STREAM_TYPE,
                        STREAM_CONTENT.length)
                .attach("server report.txt");
    }

    private static HttpClientResponseException exchangeError(
            BlockingHttpClient client, HttpRequest<?> request) {
        return assertThrows(
                HttpClientResponseException.class, () -> client.exchange(request, String.class));
    }

    private static DefaultHttpClientConfiguration clientConfiguration() {
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration();
        configuration.setConnectTimeout(HTTP_TIMEOUT);
        configuration.setReadTimeout(HTTP_TIMEOUT);
        configuration.setRequestTimeout(HTTP_TIMEOUT);
        return configuration;
    }

    @ServerFilter("/server-test/**")
    public static final class ObservingServerFilter {
        @ResponseFilter
        public void addObservationHeader(MutableHttpResponse<?> response) {
            response.header("X-Observed-By", "server-filter");
        }
    }
}
