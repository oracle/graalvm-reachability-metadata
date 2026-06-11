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
    void customizesRequestFromForwardedHeaders() throws Exception {
        Server server = new Server(0);
        addForwardedRequestCustomizer(server);
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) {
                response.setStatus(200);
                response.getHeaders().add("Content-Type", "text/plain");
                String content = "host=" + Request.getServerName(request)
                        + ";port=" + Request.getServerPort(request)
                        + ";remote=" + Request.getRemoteAddr(request)
                        + ";scheme=" + request.getHttpURI().getScheme()
                        + ";secure=" + request.isSecure();
                response.write(true, ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8)), callback);
                return true;
            }
        });

        server.start();
        try {
            HttpResponse<String> response = doHttpRequest(server,
                    "Forwarded", "for=203.0.113.9;proto=https;host=forwarded.example:8443",
                    "X-Forwarded-Server", "forwarded-server.example",
                    "X-Proxied-Https", "on",
                    "X-Forwarded-For", "203.0.113.10",
                    "X-Forwarded-Host", "forwarded-host.example",
                    "X-Forwarded-Port", "9443",
                    "X-Forwarded-Proto", "https",
                    "Proxy-auth-cert", "TLS_AES_128_GCM_SHA256",
                    "Proxy-ssl-id", "ssl-session-id");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body())
                    .containsAnyOf("host=forwarded-host.example", "host=forwarded-server.example",
                            "host=forwarded.example")
                    .containsAnyOf("port=9443", "port=8443", "port=443")
                    .containsAnyOf("remote=203.0.113.10", "remote=203.0.113.9")
                    .contains("scheme=https")
                    .contains("secure=true");
        } finally {
            server.stop();
        }
    }

    private static void addForwardedRequestCustomizer(Server server) {
        for (Connector connector : server.getConnectors()) {
            for (ConnectionFactory connectionFactory : connector.getConnectionFactories()) {
                if (connectionFactory instanceof HttpConfiguration.ConnectionFactory) {
                    HttpConfiguration.ConnectionFactory httpConnectionFactory =
                            (HttpConfiguration.ConnectionFactory) connectionFactory;
                    httpConnectionFactory.getHttpConfiguration().addCustomizer(new ForwardedRequestCustomizer());
                }
            }
        }
    }

    private static HttpResponse<String> doHttpRequest(Server server, String... headers)
            throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
            HttpRequest request = HttpRequest.newBuilder(localUri(server))
                    .GET()
                    .header("Accept", "text/plain")
                    .timeout(Duration.ofSeconds(2))
                    .headers(headers)
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private static URI localUri(Server server) {
        NetworkConnector connector = (NetworkConnector) server.getConnectors()[0];
        return URI.create("http://localhost:" + connector.getLocalPort() + "/");
    }
}
