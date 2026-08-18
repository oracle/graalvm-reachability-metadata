/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jetty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ForwardedRequestCustomizerTest {

    @Test
    void forwardedHeadersCustomizeRequestAuthority() throws Exception {
        Server server = new Server(0);
        for (Connector connector : server.getConnectors()) {
            for (ConnectionFactory connectionFactory : connector.getConnectionFactories()) {
                if (connectionFactory instanceof HttpConfiguration.ConnectionFactory) {
                    ((HttpConfiguration.ConnectionFactory) connectionFactory).getHttpConfiguration()
                            .addCustomizer(new ForwardedRequestCustomizer());
                }
            }
        }
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) {
                response.setStatus(200);
                response.getHeaders().add("Content-Type", "text/plain");
                String content = "I am " + Request.getServerName(request) + ":" + Request.getServerPort(request);
                response.write(true, ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8)), callback);
                return true;
            }
        });

        server.start();
        try {
            int localPort = ((NetworkConnector) server.getConnectors()[0]).getLocalPort();
            HttpResponse<String> response = doHttpRequest(localPort,
                    "X-Forwarded-Host", "some-host",
                    "X-Forwarded-Port", "12345");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("I am some-host:12345");
        } finally {
            server.stop();
        }
    }

    private static HttpResponse<String> doHttpRequest(int port, String... headers)
            throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(String.format("http://localhost:%d/", port)))
                .GET()
                .header("Accept", "text/plain")
                .timeout(Duration.ofSeconds(10))
                .headers(headers)
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
