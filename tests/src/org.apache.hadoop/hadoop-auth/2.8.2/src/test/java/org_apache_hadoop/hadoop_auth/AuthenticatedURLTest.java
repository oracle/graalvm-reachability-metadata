/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_auth;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.hadoop.security.authentication.client.AuthenticatedURL;
import org.apache.hadoop.security.authentication.client.Authenticator;
import org.apache.hadoop.security.authentication.client.KerberosAuthenticator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthenticatedURLTest {
    @Test
    void opensConnectionWithDefaultAuthenticatorWhenTokenIsAlreadySet() throws Exception {
        Class<? extends Authenticator> previousAuthenticator = AuthenticatedURL.getDefaultAuthenticator();
        AuthenticatedURL.setDefaultAuthenticator(KerberosAuthenticator.class);
        try {
            AtomicReference<String> cookieHeader = new AtomicReference<>();
            try (TestHttpServer server = TestHttpServer.create(exchange -> {
                cookieHeader.set(exchange.getRequestHeaders().getFirst("Cookie"));
                byte[] response = "ok".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.length);
                exchange.getResponseBody().write(response);
            })) {
                AuthenticatedURL.Token token = new AuthenticatedURL.Token("delegation-token");
                AuthenticatedURL authenticatedURL = new AuthenticatedURL();

                HttpURLConnection connection = authenticatedURL.openConnection(server.url(), token);
                try {
                    connection.setConnectTimeout(5_000);
                    connection.setReadTimeout(5_000);
                    assertThat(connection.getResponseCode()).isEqualTo(HttpURLConnection.HTTP_OK);
                    assertThat(cookieHeader.get()).isEqualTo("hadoop.auth=\"delegation-token\"");
                } finally {
                    connection.disconnect();
                }
            }
        } finally {
            AuthenticatedURL.setDefaultAuthenticator(previousAuthenticator);
        }
    }

    private static final class TestHttpServer implements AutoCloseable {
        private final HttpServer server;

        private TestHttpServer(HttpServer server) {
            this.server = server;
        }

        private static TestHttpServer create(ThrowingHttpHandler handler) throws IOException {
            HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", exchange -> {
                try (exchange) {
                    handler.handle(exchange);
                }
            });
            server.start();
            return new TestHttpServer(server);
        }

        private URL url() throws IOException {
            return new URL("http", server.getAddress().getHostString(),
                server.getAddress().getPort(), "/");
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    @FunctionalInterface
    private interface ThrowingHttpHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
