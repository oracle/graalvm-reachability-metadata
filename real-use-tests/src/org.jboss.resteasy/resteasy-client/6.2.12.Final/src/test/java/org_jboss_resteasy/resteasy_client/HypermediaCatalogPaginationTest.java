/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Link;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/// Exercises RESTEasy's hypermedia link navigation subsystem to retrieve a paginated product catalog.
public class HypermediaCatalogPaginationTest {

    @Test
    @Timeout(60)
    public void followsTheCatalogNextLinkWithItsOpaqueCursorAndMediaType() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/catalog", HypermediaCatalogPaginationTest::serveCatalog);
        server.start();
        try {
            URI catalogUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/catalog");
            ResteasyClientBuilder builder = (ResteasyClientBuilder) ClientBuilder.newBuilder();
            builder.connectTimeout(10, TimeUnit.SECONDS);
            builder.readTimeout(10, TimeUnit.SECONDS);
            try (ResteasyClient client = builder.build()) {
                List<String> products = new ArrayList<>();
                Link nextPage;
                try (Response response = client.target(catalogUri).request(MediaType.TEXT_PLAIN_TYPE).get()) {
                    assertEquals(200, response.getStatus());
                    products.addAll(response.readEntity(String.class).lines().toList());
                    nextPage = response.getLink("next");
                    assertNotNull(nextPage);
                    assertEquals("text/plain", nextPage.getType());
                    assertEquals("cursor=blue%2Bwidget%2F42", nextPage.getUri().getRawQuery());
                    assertEquals(catalogUri, response.getLink("self").getUri());
                }

                // Follow the server's link, rather than reconstructing its opaque pagination cursor.
                try (Response response = client.invocation(nextPage).get()) {
                    assertEquals(200, response.getStatus());
                    products.addAll(response.readEntity(String.class).lines().toList());
                    assertNull(response.getLink("next"));
                    assertEquals(catalogUri, response.getLink("prev").getUri());
                    assertEquals(nextPage.getUri(), response.getLink("self").getUri());
                }
                assertEquals(List.of("blue+widget", "red-widget", "green-widget", "gold-widget"), products);
            }
        } finally {
            server.stop(0);
        }
    }

    private static void serveCatalog(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"GET".equals(exchange.getRequestMethod())
                    || !MediaType.TEXT_PLAIN.equals(exchange.getRequestHeaders().getFirst(HttpHeaders.ACCEPT))) {
                respond(exchange, 406, "Request the catalog as text/plain");
                return;
            }
            String firstPage = "http://127.0.0.1:" + exchange.getLocalAddress().getPort() + "/catalog";
            String secondPage = firstPage + "?cursor=blue%2Bwidget%2F42";
            String cursor = exchange.getRequestURI().getRawQuery();
            if (cursor == null) {
                exchange.getResponseHeaders().add("Link", "<" + firstPage + ">; rel=\"self\"; type=\"text/plain\"");
                exchange.getResponseHeaders().add("Link", "<" + secondPage + ">; rel=\"next\"; type=\"text/plain\"");
                respond(exchange, 200, """
                        blue+widget
                        red-widget
                        """);
            } else if ("cursor=blue%2Bwidget%2F42".equals(cursor)) {
                exchange.getResponseHeaders().add("Link", "<" + secondPage + ">; rel=\"self\"; type=\"text/plain\"");
                exchange.getResponseHeaders().add("Link", "<" + firstPage + ">; rel=\"prev\"; type=\"text/plain\"");
                respond(exchange, 200, """
                        green-widget
                        gold-widget
                        """);
            } else {
                respond(exchange, 400, "Unknown catalog cursor");
            }
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
