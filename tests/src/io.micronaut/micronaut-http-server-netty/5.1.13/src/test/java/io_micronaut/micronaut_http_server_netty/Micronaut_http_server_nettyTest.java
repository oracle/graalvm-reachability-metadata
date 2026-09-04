/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_http_server_netty;

import static org.assertj.core.api.Assertions.assertThat;

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
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
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

    private static DefaultHttpClientConfiguration clientConfiguration() {
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration();
        configuration.setConnectTimeout(HTTP_TIMEOUT);
        configuration.setReadTimeout(HTTP_TIMEOUT);
        configuration.setRequestTimeout(HTTP_TIMEOUT);
        return configuration;
    }
}
