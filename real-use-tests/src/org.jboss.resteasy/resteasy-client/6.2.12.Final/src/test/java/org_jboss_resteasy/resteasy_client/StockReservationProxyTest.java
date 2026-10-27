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
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises RESTEasy's annotated client proxy subsystem for stock reservation management.
public class StockReservationProxyTest {

    @Test
    @Timeout(60)
    public void createsReadsAndCancelsAReservationThroughATypedClient() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/reservations", new ReservationResource());
        server.start();
        try {
            URI baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            ResteasyClientBuilder builder = (ResteasyClientBuilder) ClientBuilder.newBuilder();
            builder.connectTimeout(10, TimeUnit.SECONDS);
            builder.readTimeout(10, TimeUnit.SECONDS);
            try (ResteasyClient client = builder.build()) {
                ReservationClient reservations = client.target(baseUri).proxy(ReservationClient.class);
                try (Response created = reservations.reserve("blue-widget", 3, "customer-42")) {
                    assertEquals(201, created.getStatus());
                    assertEquals(baseUri.resolve("/reservations/blue-widget"), baseUri.resolve(created.getLocation()));
                    assertEquals("customer-42 reserved 3 blue-widget", created.readEntity(String.class));
                }

                assertEquals("customer-42 reserved 3 blue-widget", reservations.find("blue-widget"));

                try (Response cancelled = reservations.cancel("blue-widget")) {
                    assertEquals(204, cancelled.getStatus());
                }

                NotFoundException missing = assertThrows(NotFoundException.class,
                        () -> reservations.find("blue-widget"));
                try (Response response = missing.getResponse()) {
                    assertEquals(404, response.getStatus());
                    assertEquals("Reservation not found", response.readEntity(String.class));
                }
            }
        } finally {
            server.stop(0);
        }
    }

    @Path("/reservations")
    @Produces(MediaType.TEXT_PLAIN)
    public interface ReservationClient {
        @POST
        @Path("/{sku}")
        Response reserve(@PathParam("sku") String sku, @QueryParam("quantity") int quantity,
                @HeaderParam("X-Customer") String customer);

        @GET
        @Path("/{sku}")
        String find(@PathParam("sku") String sku);

        @DELETE
        @Path("/{sku}")
        Response cancel(@PathParam("sku") String sku);
    }

    private static final class ReservationResource implements HttpHandler {
        private final AtomicReference<String> reservation = new AtomicReference<>();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                if (!"/reservations/blue-widget".equals(exchange.getRequestURI().getPath())) {
                    respond(exchange, 404, "Unknown stock item");
                    return;
                }
                switch (exchange.getRequestMethod()) {
                    case "POST" -> {
                        if (!"quantity=3".equals(exchange.getRequestURI().getRawQuery())
                                || !"customer-42".equals(exchange.getRequestHeaders().getFirst("X-Customer"))) {
                            respond(exchange, 400, "Quantity and customer are required");
                            return;
                        }
                        String description = "customer-42 reserved 3 blue-widget";
                        reservation.set(description);
                        exchange.getResponseHeaders().set(HttpHeaders.LOCATION, "/reservations/blue-widget");
                        respond(exchange, 201, description);
                    }
                    case "GET" -> {
                        String description = reservation.get();
                        respond(exchange, description == null ? 404 : 200,
                                description == null ? "Reservation not found" : description);
                    }
                    case "DELETE" -> {
                        reservation.set(null);
                        exchange.sendResponseHeaders(204, -1);
                    }
                    default -> respond(exchange, 405, "Method not allowed");
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
}
