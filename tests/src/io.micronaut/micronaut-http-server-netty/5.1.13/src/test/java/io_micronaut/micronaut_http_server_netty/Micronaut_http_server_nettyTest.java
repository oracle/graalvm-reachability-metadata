/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_http_server_netty;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Controller("/netty-test")
public class Micronaut_http_server_nettyTest {
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    @Test
    @Timeout(55)
    void servesAnnotatedRoutesOverHttpOnARandomPort() throws Exception {
        Map<String, Object> properties = Map.of("micronaut.server.port", -1);

        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, properties, Environment.TEST);
                HttpClient client = HttpClient.create(server.getURL(), clientConfiguration())) {
            BlockingHttpClient blockingClient = client.toBlocking();

            assertThat(server.isRunning()).isTrue();
            assertThat(server.getPort()).isPositive();
            assertThat(server.getURL().getPort()).isEqualTo(server.getPort());

            HttpRequest<?> greetingRequest = HttpRequest.GET("/netty-test/greet/Ada?title=Dr")
                    .header("X-Request-Id", "request-42")
                    .accept(MediaType.TEXT_PLAIN_TYPE);
            HttpResponse<String> greetingResponse = blockingClient.exchange(greetingRequest, String.class);

            assertThat(greetingResponse.code()).isEqualTo(HttpStatus.OK.getCode());
            assertThat(greetingResponse.body()).isEqualTo("Dr Ada [request-42]");
            assertThat(greetingResponse.getContentType()).contains(MediaType.TEXT_PLAIN_TYPE);
            assertThat(greetingResponse.getHeaders().get("X-Route")).isEqualTo("greeting");

            HttpRequest<String> echoRequest = HttpRequest.POST("/netty-test/echo", "netty request body")
                    .contentType(MediaType.TEXT_PLAIN_TYPE)
                    .accept(MediaType.TEXT_PLAIN_TYPE);
            HttpResponse<String> echoResponse = blockingClient.exchange(echoRequest, String.class);

            assertThat(echoResponse.code()).isEqualTo(HttpStatus.ACCEPTED.getCode());
            assertThat(echoResponse.body()).isEqualTo("echo: netty request body");
            assertThat(echoResponse.getHeaders().get("X-Body-Length")).isEqualTo("18");
        }
    }

    @Test
    @Timeout(55)
    void acceptsMultipartFormFieldsAndACompletedFileUpload() throws Exception {
        Map<String, Object> properties = Map.of("micronaut.server.port", -1);
        byte[] contents = "native upload".getBytes(StandardCharsets.UTF_8);
        MultipartBody body = MultipartBody.builder()
                .addPart("description", "release notes")
                .addPart("document", "notes.txt", MediaType.TEXT_PLAIN_TYPE, contents)
                .build();

        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, properties, Environment.TEST);
                HttpClient client = HttpClient.create(server.getURL(), clientConfiguration())) {
            HttpRequest<?> request = HttpRequest.POST("/netty-test/upload", body)
                    .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                    .accept(MediaType.TEXT_PLAIN_TYPE);
            HttpResponse<String> response = client.toBlocking().exchange(request, String.class);

            assertThat(response.code()).isEqualTo(HttpStatus.OK.getCode());
            assertThat(response.body()).isEqualTo("release notes|notes.txt|text/plain|native upload");
            assertThat(response.getHeaders().get("X-Upload-Size")).isEqualTo(Long.toString(contents.length));
        }
    }

    @Get(uri = "/greet/{name}", produces = MediaType.TEXT_PLAIN)
    public HttpResponse<String> greet(
            @PathVariable("name") String name,
            @QueryValue("title") String title,
            @Header("X-Request-Id") String requestId) {
        return HttpResponse.ok(title + " " + name + " [" + requestId + "]")
                .contentType(MediaType.TEXT_PLAIN_TYPE)
                .header("X-Route", "greeting");
    }

    @Post(uri = "/echo", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
    public HttpResponse<String> echo(@Body String body) {
        return HttpResponse.accepted()
                .body("echo: " + body)
                .contentType(MediaType.TEXT_PLAIN_TYPE)
                .header("X-Body-Length", Integer.toString(body.length()));
    }

    @Post(uri = "/upload", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    public HttpResponse<String> upload(
            @Part("description") String description, @Part("document") CompletedFileUpload document)
            throws IOException {
        String responseBody = String.join(
                "|",
                description,
                document.getFilename(),
                document.getContentType().map(MediaType::getName).orElse("unknown"),
                new String(document.getBytes(), StandardCharsets.UTF_8));
        return HttpResponse.ok(responseBody)
                .contentType(MediaType.TEXT_PLAIN_TYPE)
                .header("X-Upload-Size", Long.toString(document.getSize()));
    }

    private static DefaultHttpClientConfiguration clientConfiguration() {
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration();
        configuration.setConnectTimeout(HTTP_TIMEOUT);
        configuration.setReadTimeout(HTTP_TIMEOUT);
        configuration.setRequestTimeout(HTTP_TIMEOUT);
        return configuration;
    }
}
