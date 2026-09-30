/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_http_netty;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class PrivateLoomSupportTest {
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    @Test
    @Timeout(55)
    void servesRequestsOnTheConfiguredVirtualThreadEventLoop() throws Exception {
        Map<String, Object> properties = Map.of(
                "micronaut.server.port", -1,
                "micronaut.netty.event-loops.default.executor", "virtual",
                "micronaut.executors.virtual.type", "thread-per-task",
                "micronaut.executors.virtual.virtual", true);

        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, properties, Environment.TEST);
                HttpClient client = HttpClient.create(server.getURL(), clientConfiguration())) {
            HttpResponse<String> response = client.toBlocking().exchange(
                    HttpRequest.GET("/loom-test/thread").accept(MediaType.TEXT_PLAIN_TYPE), String.class);

            assertThat(response.body()).isEqualTo("virtual");
        }
    }

    private static DefaultHttpClientConfiguration clientConfiguration() {
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration();
        configuration.setConnectTimeout(HTTP_TIMEOUT);
        configuration.setReadTimeout(HTTP_TIMEOUT);
        configuration.setRequestTimeout(HTTP_TIMEOUT);
        return configuration;
    }

    @Controller("/loom-test")
    public static class LoomController {
        @Get(uri = "/thread", produces = MediaType.TEXT_PLAIN)
        public HttpResponse<String> thread() {
            return HttpResponse.ok(Thread.currentThread().isVirtual() ? "virtual" : "platform")
                    .contentType(MediaType.TEXT_PLAIN_TYPE);
        }
    }
}
