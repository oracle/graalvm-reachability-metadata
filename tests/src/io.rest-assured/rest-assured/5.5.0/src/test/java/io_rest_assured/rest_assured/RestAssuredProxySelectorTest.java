/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.internal.proxy.RestAssuredProxySelector;
import io.restassured.response.Response;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

public class RestAssuredProxySelectorTest {
    private static final String DYNAMIC_CLASS_NAME = "io.restassured.internal.proxy."
            + "RestAssuredProxySelectorRoutePlanner";

    @Test
    void delegatesToConfiguredJreProxySelectorForDirectRequest() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/proxy-selector", RestAssuredProxySelectorTest::handleRequest);
        server.start();

        int port = server.getAddress().getPort();
        ProxySelector originalProxySelector = ProxySelector.getDefault();
        RecordingProxySelector proxySelector = new RecordingProxySelector();
        ProxySelector.setDefault(proxySelector);
        RestAssured.reset();

        try {
            Response response = given()
                    .baseUri("http://127.0.0.1")
                    .port(port)
                    .when()
                    .get("/proxy-selector");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString()).isEqualTo("request used the JRE proxy selector");
            assertThat(proxySelector.selectedUris())
                    .anyMatch(uri -> "127.0.0.1".equals(uri.getHost()) && uri.getPort() == port);
            assertThat(invokeGeneratedClassLookup(DYNAMIC_CLASS_NAME).getName()).isEqualTo(DYNAMIC_CLASS_NAME);
        } finally {
            RestAssured.reset();
            ProxySelector.setDefault(originalProxySelector);
            server.stop(0);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) {
        return (Class<?>) InvokerHelper.invokeStaticMethod(
                RestAssuredProxySelector.class,
                "class$",
                className);
    }

    private static void handleRequest(HttpExchange exchange) throws IOException {
        try {
            boolean expectedRequest = "GET".equals(exchange.getRequestMethod())
                    && "/proxy-selector".equals(exchange.getRequestURI().getPath());
            byte[] responseBytes = (expectedRequest
                    ? "request used the JRE proxy selector"
                    : "unexpected request").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(
                    expectedRequest ? HttpURLConnection.HTTP_OK : HttpURLConnection.HTTP_BAD_REQUEST,
                    responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } finally {
            exchange.close();
        }
    }

    private static final class RecordingProxySelector extends ProxySelector {
        private final List<URI> selectedUris = Collections.synchronizedList(new ArrayList<>());

        @Override
        public List<Proxy> select(URI uri) {
            selectedUris.add(uri);
            return List.of(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            throw new AssertionError("Direct local request should not report proxy connection failures", ioe);
        }

        private List<URI> selectedUris() {
            synchronized (selectedUris) {
                return List.copyOf(selectedUris);
            }
        }
    }
}
