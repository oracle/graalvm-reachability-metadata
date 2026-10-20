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

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Jetty's context routing subsystem to mount storefront and inventory applications on one server.
public class StorefrontContextRoutingTest {

    @Test
    @Timeout(60)
    public void routesNestedApplicationsWithoutLeakingRoutesAcrossContexts() throws Exception {
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        connector.setIdleTimeout(10000);
        server.addConnector(connector);
        server.setStopTimeout(10000);

        ContextHandler storefront = new ContextHandler("/store");
        storefront.setHandler(new StorefrontHandler());
        ContextHandler inventory = new ContextHandler("/store/operations");
        inventory.setHandler(new InventoryHandler());
        server.setHandler(new ContextHandlerCollection(storefront, inventory));

        try {
            server.start();
            URI base = URI.create("http://127.0.0.1:" + connector.getLocalPort());
            try (HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .version(HttpClient.Version.HTTP_1_1)
                    .build()) {
                HttpResponse<String> product = get(client, base.resolve("/store/products?sku=desk%20lamp"));
                assertEquals(200, product.statusCode());
                assertEquals("Desk lamp: USD 24.00\n", product.body());
                assertEquals("text/plain;charset=utf-8",
                        product.headers().firstValue("Content-Type").orElseThrow());

                HttpResponse<String> stock = get(client, base.resolve("/store/operations/stock?sku=desk%20lamp"));
                assertEquals(200, stock.statusCode());
                assertEquals("Desk lamp: 7 available\n", stock.body());

                assertEquals(404, get(client, base.resolve("/store/operations/products?sku=desk%20lamp")).statusCode());
                assertEquals(404, get(client, base.resolve("/store/stock?sku=desk%20lamp")).statusCode());
                assertEquals(404, get(client, base.resolve("/storefront/products?sku=desk%20lamp")).statusCode());

                HttpResponse<String> redirect = get(client, base.resolve("/store"));
                assertEquals(301, redirect.statusCode());
                URI destination = base.resolve(redirect.headers().firstValue("Location").orElseThrow());
                assertEquals(base.resolve("/store/"), destination);
                HttpResponse<String> landing = get(client, destination);
                assertEquals(200, landing.statusCode());
                assertEquals("Welcome to the lighting store\n", landing.body());
            }
        } finally {
            server.stop();
            server.destroy();
        }
    }

    private static HttpResponse<String> get(HttpClient client, URI uri) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static class StorefrontHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            String path = Request.getPathInContext(request);
            String body;
            if ("/".equals(path)) {
                body = "Welcome to the lighting store\n";
            } else if ("/products".equals(path)
                    && "desk lamp".equals(Request.extractQueryParameters(request).getValue("sku"))) {
                body = "Desk lamp: USD 24.00\n";
            } else {
                return false;
            }
            response.setStatus(200);
            response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/plain;charset=utf-8");
            Content.Sink.write(response, true, body, callback);
            return true;
        }
    }

    private static class InventoryHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            if (!"/stock".equals(Request.getPathInContext(request))
                    || !"desk lamp".equals(Request.extractQueryParameters(request).getValue("sku"))) {
                return false;
            }
            response.setStatus(200);
            response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/plain;charset=utf-8");
            Content.Sink.write(response, true, "Desk lamp: 7 available\n", callback);
            return true;
        }
    }
}
