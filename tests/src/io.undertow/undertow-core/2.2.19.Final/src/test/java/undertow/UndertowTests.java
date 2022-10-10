/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package undertow;

import io.undertow.Undertow;
import io.undertow.util.Headers;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

public class UndertowTests {

    private static final int PORT = 8080;

    @Test
    void test() throws Exception {
        // Start server
        Undertow server = Undertow.builder()
                .addHttpListener(PORT, "localhost")
                .setHandler(exchange -> {
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                    exchange.getResponseSender().send("Hello World");
                }).build();
        server.start();
        try {
            // Make request
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(String.format("http://localhost:%d/", PORT)))
                    .GET().header("Accept", "text/plain").timeout(Duration.ofSeconds(1)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("Hello World");
        } finally {
            // Cleanup
            server.stop();
        }
    }
}
