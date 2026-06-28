/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_elastic_clients.elasticsearch_java;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import co.elastic.clients.transport.rest5_client.low_level.Request;
import co.elastic.clients.transport.rest5_client.low_level.Response;
import co.elastic.clients.transport.rest5_client.low_level.ResponseListener;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import kotlin.KotlinVersion;
import org.junit.jupiter.api.Test;
import scala.util.Properties;

public class LanguageRuntimeVersionsTest {
    private static final long REQUEST_TIMEOUT_SECONDS = 10;

    @Test
    void rest5ClientMetaHeaderIncludesDetectedJvmLanguageRuntimeVersions() throws Exception {
        AtomicReference<String> clientMetaHeader = new AtomicReference<>();

        try (TestHttpServer server = TestHttpServer.start(exchange -> {
            clientMetaHeader.set(exchange.getRequestHeaders().getFirst("X-Elastic-Client-Meta"));
            sendJson(exchange, "{}");
        }); Rest5Client client = Rest5Client.builder(server.uri()).build()) {
            Response response = execute(client, new Request("GET", "/"));

            assertThat(response.getStatusCode()).isEqualTo(200);
        }

        assertThat(clientMetaHeader.get())
            .contains(",kt=" + keepMajorMinor(KotlinVersion.CURRENT.toString()))
            .contains(",sc=" + keepMajorMinor(Properties.versionNumberString()));
    }

    private static Response execute(Rest5Client client, Request request) throws Exception {
        CountDownLatch responseReceived = new CountDownLatch(1);
        AtomicReference<Response> responseRef = new AtomicReference<>();
        AtomicReference<Exception> failureRef = new AtomicReference<>();

        client.performRequestAsync(request, new ResponseListener() {
            @Override
            public void onSuccess(Response response) {
                responseRef.set(response);
                responseReceived.countDown();
            }

            @Override
            public void onFailure(Exception exception) {
                failureRef.set(exception);
                responseReceived.countDown();
            }
        });

        assertThat(responseReceived.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        if (failureRef.get() != null) {
            throw failureRef.get();
        }
        Response response = responseRef.get();
        assertThat(response).isNotNull();
        return response;
    }

    private static String keepMajorMinor(String version) {
        int firstDot = version.indexOf('.');
        int secondDot = version.indexOf('.', firstDot + 1);
        if (secondDot < 0) {
            return version;
        }
        return version.substring(0, secondDot);
    }

    private static void sendJson(HttpExchange exchange, String body) throws IOException {
        byte[] responseBody = body.getBytes(StandardCharsets.UTF_8);
        Headers responseHeaders = exchange.getResponseHeaders();
        responseHeaders.set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, responseBody.length);
        try (OutputStream responseStream = exchange.getResponseBody()) {
            responseStream.write(responseBody);
        }
    }

    private static final class TestHttpServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;

        private TestHttpServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        private static TestHttpServer start(HttpHandler handler) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            server.createContext("/", handler);
            server.setExecutor(executor);
            server.start();
            return new TestHttpServer(server, executor);
        }

        private URI uri() throws URISyntaxException {
            InetSocketAddress address = server.getAddress();
            return new URI("http", null, address.getHostString(), address.getPort(), null, null, null);
        }

        @Override
        public void close() throws InterruptedException {
            server.stop(0);
            executor.shutdown();
            assertThat(executor.awaitTermination(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
    }
}
