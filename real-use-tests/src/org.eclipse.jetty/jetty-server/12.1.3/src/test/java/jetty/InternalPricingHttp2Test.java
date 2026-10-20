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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Jetty's cleartext HTTP/2 connector subsystem for an internal pricing API with HTTP/1.1 compatibility.
public class InternalPricingHttp2Test {

    @Test
    @Timeout(60)
    public void servesParallelPriceLookupsOverHttp2AndSupportsLegacyClients() throws Exception {
        Server server = new Server();
        HttpConfiguration http = new HttpConfiguration();
        // This listener is for a trusted internal network behind the application's TLS ingress.
        ServerConnector connector = new ServerConnector(server,
                new HttpConnectionFactory(http), new HTTP2CServerConnectionFactory(http));
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        connector.setIdleTimeout(10000);
        server.addConnector(connector);
        server.setStopTimeout(10000);
        server.setHandler(new PricingHandler());

        try {
            server.start();
            URI base = URI.create("http://127.0.0.1:" + connector.getLocalPort());
            try (HttpClient quotingService = client(HttpClient.Version.HTTP_2);
                    HttpClient legacyClient = client(HttpClient.Version.HTTP_1_1)) {
                // The first lookup upgrades HTTP/1.1 to h2c; subsequent lookups use HTTP/2 streams.
                HttpResponse<String> initial = quotingService.send(priceRequest(base, "desk-lamp"),
                        HttpResponse.BodyHandlers.ofString());
                assertPrice(initial, HttpClient.Version.HTTP_2, "desk-lamp,USD,24.00\n");

                CompletableFuture<HttpResponse<String>> lamp = quotingService.sendAsync(
                        priceRequest(base, "desk-lamp"), HttpResponse.BodyHandlers.ofString());
                CompletableFuture<HttpResponse<String>> clock = quotingService.sendAsync(
                        priceRequest(base, "wall-clock"), HttpResponse.BodyHandlers.ofString());
                assertPrice(lamp.get(10, TimeUnit.SECONDS), HttpClient.Version.HTTP_2, "desk-lamp,USD,24.00\n");
                assertPrice(clock.get(10, TimeUnit.SECONDS), HttpClient.Version.HTTP_2, "wall-clock,USD,18.00\n");

                HttpResponse<String> legacy = legacyClient.send(priceRequest(base, "wall-clock"),
                        HttpResponse.BodyHandlers.ofString());
                assertPrice(legacy, HttpClient.Version.HTTP_1_1, "wall-clock,USD,18.00\n");

                HttpResponse<String> missing = quotingService.send(priceRequest(base, "unknown"),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(HttpClient.Version.HTTP_2, missing.version());
                assertEquals(404, missing.statusCode());
                assertEquals("Unknown product\n", missing.body());
            }
        } finally {
            server.stop();
            server.destroy();
        }
    }

    private static HttpClient client(HttpClient.Version version) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(version)
                .build();
    }

    private static HttpRequest priceRequest(URI base, String sku) {
        return HttpRequest.newBuilder(base.resolve("/prices/" + sku))
                .timeout(Duration.ofSeconds(10)).GET().build();
    }

    private static void assertPrice(HttpResponse<String> response, HttpClient.Version version, String price) {
        assertEquals(version, response.version());
        assertEquals(200, response.statusCode());
        assertEquals("text/csv;charset=utf-8", response.headers().firstValue("Content-Type").orElseThrow());
        assertEquals(price, response.body());
    }

    private static class PricingHandler extends Handler.Abstract {
        private final Map<String, String> prices = Map.of(
                "/prices/desk-lamp", "desk-lamp,USD,24.00\n",
                "/prices/wall-clock", "wall-clock,USD,18.00\n");

        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            String path = Request.getPathInContext(request);
            if (!"GET".equals(request.getMethod()) || !path.startsWith("/prices/")) {
                return false;
            }
            String price = prices.get(path);
            if (price == null) {
                response.setStatus(404);
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/plain;charset=utf-8");
                Content.Sink.write(response, true, "Unknown product\n", callback);
            } else {
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/csv;charset=utf-8");
                Content.Sink.write(response, true, price, callback);
            }
            return true;
        }
    }
}
