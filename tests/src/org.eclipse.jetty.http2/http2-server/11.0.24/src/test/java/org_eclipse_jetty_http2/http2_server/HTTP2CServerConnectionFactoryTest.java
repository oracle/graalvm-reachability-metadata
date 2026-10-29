/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty_http2.http2_server;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class HTTP2CServerConnectionFactoryTest {
    private final Server server = new Server();
    private ServerConnector connector;

    @BeforeEach
    void startServer() throws Exception {
        HttpConfiguration configuration = new HttpConfiguration();
        connector = new ServerConnector(server, new HttpConnectionFactory(configuration), new HTTP2CServerConnectionFactory(configuration));
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        connector.setIdleTimeout(30000);
        server.addConnector(connector);
        server.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("text/plain");
                response.setHeader("X-Request-Protocol", request.getProtocol());
                response.getWriter().write(request.getMethod() + " " + target + "?" + request.getQueryString());
                baseRequest.setHandled(true);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() throws Exception {
        server.stop();
    }

    @Test
    void upgradesHttp1RequestsToHttp2() throws Exception {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).version(HttpClient.Version.HTTP_2).build()) {
            HttpRequest request = HttpRequest.newBuilder(uri()).timeout(Duration.ofSeconds(30)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.version()).isEqualTo(HttpClient.Version.HTTP_2);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("GET /greeting?name=jetty");

            HttpResponse<String> next = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(next.version()).isEqualTo(HttpClient.Version.HTTP_2);
            assertThat(next.statusCode()).isEqualTo(200);
            assertThat(next.headers().firstValue("X-Request-Protocol")).contains("HTTP/2.0");
            assertThat(next.body()).isEqualTo("GET /greeting?name=jetty");
        }
    }

    @Test
    void keepsHttp1AvailableOnTheSameConnector() throws Exception {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).version(HttpClient.Version.HTTP_1_1).build()) {
            HttpRequest request = HttpRequest.newBuilder(uri()).timeout(Duration.ofSeconds(30)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("X-Request-Protocol")).contains("HTTP/1.1");
            assertThat(response.body()).isEqualTo("GET /greeting?name=jetty");
        }
    }

    private URI uri() {
        return URI.create("http://127.0.0.1:" + connector.getLocalPort() + "/greeting?name=jetty");
    }
}
