/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jetty;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.SizeLimitHandler;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Promise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Jetty's message size limiting subsystem to protect a catalog import endpoint from oversized uploads.
public class CatalogImportSizeLimitTest {

    @Test
    @Timeout(60)
    public void rejectsOversizedFixedLengthAndStreamedImportsWithoutReplacingTheCatalog() throws Exception {
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        connector.setIdleTimeout(10000);
        server.addConnector(connector);
        server.setStopTimeout(10000);

        SizeLimitHandler limits = new SizeLimitHandler(128, -1);
        limits.setHandler(new CatalogHandler());
        server.setHandler(limits);

        String catalog = """
                sku,price
                desk-lamp,24.00
                wall-clock,18.00
                """;
        String oversized = "sku,price\n" + "floor-lamp,49.00\n".repeat(30);
        String replacement = """
                sku,price
                reading-light,32.00
                """;

        try {
            server.start();
            URI endpoint = URI.create("http://127.0.0.1:" + connector.getLocalPort() + "/catalog");
            try (HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .version(HttpClient.Version.HTTP_1_1)
                    .build()) {
                HttpResponse<String> imported = upload(client, endpoint, HttpRequest.BodyPublishers.ofString(catalog));
                assertEquals(204, imported.statusCode());
                assertEquals("", imported.body());
                assertCatalog(client, endpoint, catalog);

                HttpResponse<String> rejected = upload(client, endpoint,
                        HttpRequest.BodyPublishers.ofString(oversized));
                assertEquals(413, rejected.statusCode());
                assertCatalog(client, endpoint, catalog);

                // Input-stream publishers omit Content-Length, so the limit must also apply while reading.
                HttpResponse<String> streamedRejection = upload(client, endpoint,
                        HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(
                                oversized.getBytes(StandardCharsets.UTF_8))));
                assertEquals(413, streamedRejection.statusCode());
                assertCatalog(client, endpoint, catalog);

                HttpResponse<String> replaced = upload(client, endpoint,
                        HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(
                                replacement.getBytes(StandardCharsets.UTF_8))));
                assertEquals(204, replaced.statusCode());
                assertCatalog(client, endpoint, replacement);
            }
        } finally {
            server.stop();
            server.destroy();
        }
    }

    private static HttpResponse<String> upload(HttpClient client, URI uri, HttpRequest.BodyPublisher body)
            throws Exception {
        return client.send(HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "text/csv;charset=utf-8")
                .PUT(body).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertCatalog(HttpClient client, URI uri, String expected) throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("text/csv;charset=utf-8", response.headers().firstValue("Content-Type").orElseThrow());
        assertEquals(expected, response.body());
    }

    private static class CatalogHandler extends Handler.Abstract {
        private final AtomicReference<String> catalog = new AtomicReference<>("sku,price\n");

        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            if (!"/catalog".equals(Request.getPathInContext(request))) {
                return false;
            }
            if ("GET".equals(request.getMethod())) {
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/csv;charset=utf-8");
                Content.Sink.write(response, true, catalog.get(), callback);
                return true;
            }
            if (!"PUT".equals(request.getMethod())) {
                return false;
            }
            Content.Source.asString(request, StandardCharsets.UTF_8,
                    Promise.Invocable.from(getInvocationType(), csv -> {
                        if (!csv.startsWith("sku,price\n")) {
                            Response.writeError(request, response, callback, 422, "Expected sku,price CSV header");
                            return;
                        }
                        // Publish a new snapshot only after the entire request has been read successfully.
                        catalog.set(csv);
                        response.setStatus(204);
                        callback.succeeded();
                    }, callback::failed));
            return true;
        }
    }
}
