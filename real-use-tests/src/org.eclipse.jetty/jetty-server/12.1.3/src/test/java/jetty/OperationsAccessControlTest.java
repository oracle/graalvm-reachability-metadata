/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jetty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.InetAccessHandler;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Jetty's network access control subsystem to restrict inventory maintenance to an operations connector.
public class OperationsAccessControlTest {

    @Test
    @Timeout(60)
    public void exposesHealthChecksButRestrictsMaintenanceToTheOperationsListener() throws Exception {
        Server server = new Server();
        ServerConnector storefront = connector(server, "storefront");
        ServerConnector operations = connector(server, "operations");
        server.setStopTimeout(10000);

        InetAccessHandler access = new InetAccessHandler(new InventoryOperationsHandler());
        access.include("127.0.0.1|/health");
        access.include("operations@127.0.0.1|/operations/*");
        server.setHandler(access);

        try {
            server.start();
            URI publicBase = URI.create("http://127.0.0.1:" + storefront.getLocalPort());
            URI operationsBase = URI.create("http://127.0.0.1:" + operations.getLocalPort());
            try (HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .version(HttpClient.Version.HTTP_1_1)
                    .build()) {
                HttpResponse<String> health = send(client, publicBase.resolve("/health"), "GET");
                assertEquals(200, health.statusCode());
                assertEquals("Inventory service is running\n", health.body());

                assertEquals(403, send(client, publicBase.resolve("/operations/status"), "GET").statusCode());
                assertStatus(client, operationsBase, "Accepting reservations\n");

                // The public listener must reject the mutation before the application pauses reservations.
                assertEquals(403, send(client, publicBase.resolve("/operations/pause"), "POST").statusCode());
                assertStatus(client, operationsBase, "Accepting reservations\n");

                HttpResponse<String> paused = send(client, operationsBase.resolve("/operations/pause"), "POST");
                assertEquals(200, paused.statusCode());
                assertEquals("Reservations paused for stocktaking\n", paused.body());
                assertStatus(client, operationsBase, "Reservations paused\n");

                HttpResponse<String> stillHealthy = send(client, publicBase.resolve("/health"), "GET");
                assertEquals(200, stillHealthy.statusCode());
                assertEquals("Inventory service is running\n", stillHealthy.body());
            }
        } finally {
            server.stop();
            server.destroy();
        }
    }

    private static ServerConnector connector(Server server, String name) {
        ServerConnector connector = new ServerConnector(server);
        connector.setName(name);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        connector.setIdleTimeout(10000);
        server.addConnector(connector);
        return connector;
    }

    private static HttpResponse<String> send(HttpClient client, URI uri, String method) throws Exception {
        return client.send(HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertStatus(HttpClient client, URI base, String expected) throws Exception {
        HttpResponse<String> status = send(client, base.resolve("/operations/status"), "GET");
        assertEquals(200, status.statusCode());
        assertEquals("text/plain;charset=utf-8", status.headers().firstValue("Content-Type").orElseThrow());
        assertEquals(expected, status.body());
    }

    private static class InventoryOperationsHandler extends Handler.Abstract {
        private final AtomicBoolean paused = new AtomicBoolean();

        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            String path = Request.getPathInContext(request);
            String body;
            if ("GET".equals(request.getMethod()) && "/health".equals(path)) {
                body = "Inventory service is running\n";
            } else if ("GET".equals(request.getMethod()) && "/operations/status".equals(path)) {
                body = paused.get() ? "Reservations paused\n" : "Accepting reservations\n";
            } else if ("POST".equals(request.getMethod()) && "/operations/pause".equals(path)) {
                paused.set(true);
                body = "Reservations paused for stocktaking\n";
            } else {
                return false;
            }
            response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/plain;charset=utf-8");
            Content.Sink.write(response, true, body, callback);
            return true;
        }
    }
}
