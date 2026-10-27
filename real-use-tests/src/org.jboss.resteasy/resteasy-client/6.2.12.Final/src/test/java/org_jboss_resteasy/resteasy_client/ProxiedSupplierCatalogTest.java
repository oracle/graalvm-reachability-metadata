/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises RESTEasy's HTTP forward-proxy routing subsystem to retrieve a supplier catalog.
public class ProxiedSupplierCatalogTest {
    private static final String VIA = "1.1 purchasing-gateway";
    private static final String CATALOG = "blue-widget,EUR,12.50\nred-widget,EUR,8.00\n";

    @Test
    @Timeout(60)
    public void routesCatalogRequestsThroughTheGatewayAndPreservesSupplierErrors() throws IOException {
        HttpServer supplier = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        HttpServer gateway = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        URI supplierUri = URI.create("http://127.0.0.1:" + supplier.getAddress().getPort());
        ForwardProxy proxy = new ForwardProxy(supplierUri);
        supplier.createContext("/catalog", ProxiedSupplierCatalogTest::serveCatalog);
        gateway.createContext("/", proxy);
        supplier.start();
        gateway.start();
        try {
            ResteasyClientBuilder builder = (ResteasyClientBuilder) ClientBuilder.newBuilder();
            builder.connectTimeout(10, TimeUnit.SECONDS);
            builder.readTimeout(10, TimeUnit.SECONDS);
            builder.defaultProxy("127.0.0.1", gateway.getAddress().getPort(), "http");
            try (ResteasyClient client = builder.build()) {
                ResteasyWebTarget catalog = client.target(supplierUri).path("catalog");
                try (Response response = catalog.queryParam("currency", "EUR")
                        .request("text/csv").get()) {
                    assertEquals(200, response.getStatus());
                    assertEquals("text/csv", response.getMediaType().toString());
                    assertEquals(VIA, response.getHeaderString("Via"));
                    assertEquals(CATALOG, response.readEntity(String.class));
                }
                try (Response response = catalog.path("retired-widget").request(MediaType.TEXT_PLAIN_TYPE).get()) {
                    assertEquals(410, response.getStatus());
                    assertEquals(VIA, response.getHeaderString("Via"));
                    assertEquals("retired-widget is no longer supplied", response.readEntity(String.class));
                }
                // A forward proxy receives absolute origin URIs, not paths relative to the gateway.
                assertEquals(List.of(supplierUri.resolve("/catalog?currency=EUR"),
                        supplierUri.resolve("/catalog/retired-widget")), List.copyOf(proxy.requests));
            }
        } finally {
            gateway.stop(0);
            supplier.stop(0);
        }
    }

    private static void serveCatalog(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"purchasing-gateway".equals(exchange.getRequestHeaders().getFirst("X-Purchasing-Gateway"))) {
                respond(exchange, 403, MediaType.TEXT_PLAIN, "Use the purchasing gateway");
            } else if ("/catalog".equals(exchange.getRequestURI().getPath())
                    && "currency=EUR".equals(exchange.getRequestURI().getRawQuery())
                    && "text/csv".equals(exchange.getRequestHeaders().getFirst(HttpHeaders.ACCEPT))) {
                respond(exchange, 200, "text/csv", CATALOG);
            } else if ("/catalog/retired-widget".equals(exchange.getRequestURI().getPath())) {
                respond(exchange, 410, MediaType.TEXT_PLAIN, "retired-widget is no longer supplied");
            } else {
                respond(exchange, 400, MediaType.TEXT_PLAIN, "Unsupported catalog request");
            }
        }
    }

    private static void respond(HttpExchange exchange, int status, String mediaType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, mediaType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static final class ForwardProxy implements HttpHandler {
        private final URI supplier;
        private final ConcurrentLinkedQueue<URI> requests = new ConcurrentLinkedQueue<>();

        private ForwardProxy(URI supplier) {
            this.supplier = supplier;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                URI destination = exchange.getRequestURI();
                if (!"GET".equals(exchange.getRequestMethod()) || !"http".equals(destination.getScheme())
                        || !supplier.getRawAuthority().equals(destination.getRawAuthority())
                        || !supplier.getRawAuthority().equals(exchange.getRequestHeaders().getFirst("Host"))) {
                    respond(exchange, 400, MediaType.TEXT_PLAIN, "Gateway requires an absolute supplier URI");
                    return;
                }
                requests.add(destination);
                HttpURLConnection upstream = (HttpURLConnection) destination.toURL().openConnection(Proxy.NO_PROXY);
                upstream.setConnectTimeout(10000);
                upstream.setReadTimeout(10000);
                upstream.setInstanceFollowRedirects(false);
                upstream.setRequestProperty(HttpHeaders.ACCEPT,
                        exchange.getRequestHeaders().getFirst(HttpHeaders.ACCEPT));
                upstream.setRequestProperty("X-Purchasing-Gateway", "purchasing-gateway");
                try {
                    int status = upstream.getResponseCode();
                    byte[] body;
                    try (InputStream input = status >= 400 ? upstream.getErrorStream() : upstream.getInputStream()) {
                        body = input == null ? new byte[0] : input.readAllBytes();
                    }
                    exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, upstream.getContentType());
                    exchange.getResponseHeaders().set("Via", VIA);
                    exchange.sendResponseHeaders(status, body.length);
                    exchange.getResponseBody().write(body);
                } finally {
                    upstream.disconnect();
                }
            }
        }
    }
}
