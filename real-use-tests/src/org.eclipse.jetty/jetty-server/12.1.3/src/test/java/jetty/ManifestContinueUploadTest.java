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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Promise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Jetty's HTTP 100-continue subsystem to authorize warehouse manifest uploads before sending their content.
public class ManifestContinueUploadTest {

    @Test
    @Timeout(60)
    public void rejectsUnauthorizedUploadBeforeOpeningItsBodyAndThenAcceptsAuthorizedContent() throws Exception {
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        connector.setIdleTimeout(10000);
        server.addConnector(connector);
        server.setStopTimeout(10000);
        server.setHandler(new ManifestHandler());

        String manifest = "sku,quantity\n" + "desk-lamp,7\nwall-clock,4\n".repeat(2048);
        AtomicInteger openedBodies = new AtomicInteger();
        try {
            server.start();
            URI endpoint = URI.create("http://127.0.0.1:" + connector.getLocalPort() + "/warehouse/manifest");
            try (HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .version(HttpClient.Version.HTTP_1_1)
                    .build()) {
                HttpResponse<String> rejected = upload(client, endpoint, "expired-upload-token", manifest,
                        openedBodies);
                assertEquals(403, rejected.statusCode());
                assertEquals("A valid warehouse upload token is required\n", rejected.body());
                assertEquals(0, openedBodies.get(), "A rejected upload must not open the manifest stream");
                assertManifest(client, endpoint, "sku,quantity\n");

                // Jetty sends 100 Continue when the authorized handler starts reading the request.
                HttpResponse<String> accepted = upload(client, endpoint, "warehouse-upload-token", manifest,
                        openedBodies);
                assertEquals(201, accepted.statusCode());
                assertEquals("/warehouse/manifest", accepted.headers().firstValue("Location").orElseThrow());
                assertEquals("Manifest imported\n", accepted.body());
                assertEquals(1, openedBodies.get(), "The authorized upload must consume its stream once");
                assertManifest(client, endpoint, manifest);

                HttpResponse<String> deniedReplacement = upload(client, endpoint, "expired-upload-token",
                        "sku,quantity\ndesk-lamp,0\n", openedBodies);
                assertEquals(403, deniedReplacement.statusCode());
                assertEquals(1, openedBodies.get(), "A rejected replacement must also withhold its body");
                assertManifest(client, endpoint, manifest);
            }
        } finally {
            server.stop();
            server.destroy();
        }
    }

    private static HttpResponse<String> upload(HttpClient client, URI endpoint, String token, String manifest,
            AtomicInteger openedBodies) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .expectContinue(true)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "text/csv;charset=utf-8")
                .PUT(HttpRequest.BodyPublishers.ofInputStream(() -> {
                    openedBodies.incrementAndGet();
                    return new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8));
                }))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void assertManifest(HttpClient client, URI endpoint, String expected) throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("text/csv;charset=utf-8", response.headers().firstValue("Content-Type").orElseThrow());
        assertEquals(expected, response.body());
    }

    private static class ManifestHandler extends Handler.Abstract {
        private final AtomicReference<String> manifest = new AtomicReference<>("sku,quantity\n");

        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            if (!"/warehouse/manifest".equals(Request.getPathInContext(request))) {
                return false;
            }
            if ("GET".equals(request.getMethod())) {
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/csv;charset=utf-8");
                Content.Sink.write(response, true, manifest.get(), callback);
                return true;
            }
            if (!"PUT".equals(request.getMethod())) {
                return false;
            }
            response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/plain;charset=utf-8");
            // Authorize using headers alone, before demanding content and triggering 100 Continue.
            if (!"Bearer warehouse-upload-token".equals(request.getHeaders().get(HttpHeader.AUTHORIZATION))) {
                response.setStatus(403);
                Content.Sink.write(response, true, "A valid warehouse upload token is required\n", callback);
                return true;
            }
            Content.Source.asString(request, StandardCharsets.UTF_8,
                    Promise.Invocable.from(getInvocationType(), csv -> {
                        manifest.set(csv);
                        response.setStatus(201);
                        response.getHeaders().put(HttpHeader.LOCATION, "/warehouse/manifest");
                        Content.Sink.write(response, true, "Manifest imported\n", callback);
                    }, callback::failed));
            return true;
        }
    }
}
