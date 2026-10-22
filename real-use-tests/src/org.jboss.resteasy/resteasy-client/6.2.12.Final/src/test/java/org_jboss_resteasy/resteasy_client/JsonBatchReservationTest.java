/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericEntity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises RESTEasy's JSON entity provider subsystem to submit a batch and read typed stock allocations.
public class JsonBatchReservationTest {

    @Test
    @Timeout(60)
    public void submitsTypedOrderLinesAndReadsPartialFulfillmentResults() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/stock/reservations/batch", JsonBatchReservationTest::reserveBatch);
        server.start();
        try {
            ResteasyClientBuilder builder = (ResteasyClientBuilder) ClientBuilder.newBuilder();
            builder.connectTimeout(10, TimeUnit.SECONDS);
            builder.readTimeout(10, TimeUnit.SECONDS);
            builder.register(new ResteasyJackson2Provider());
            try (ResteasyClient client = builder.build()) {
                List<OrderLine> lines = List.of(
                        new OrderLine("blue-widget", 3, "Caf\u00e9 counter"),
                        new OrderLine("red-widget", 5, "Back room"));
                GenericEntity<List<OrderLine>> batch = new GenericEntity<List<OrderLine>>(lines) { };
                try (Response response = client.target("http://127.0.0.1:" + server.getAddress().getPort())
                        .path("stock/reservations/batch").request(MediaType.APPLICATION_JSON_TYPE)
                        .post(Entity.entity(batch, MediaType.APPLICATION_JSON_TYPE))) {
                    assertEquals(201, response.getStatus());
                    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
                    assertEquals("batch-42", response.getHeaderString("X-Reservation-Batch"));
                    List<Allocation> allocations = response.readEntity(new GenericType<List<Allocation>>() { });
                    assertEquals(List.of(
                            new Allocation("blue-widget", 3, 0, "Caf\u00e9 counter"),
                            new Allocation("red-widget", 2, 3, "Back room")), allocations);
                    assertEquals(5, allocations.stream().mapToInt(Allocation::reserved).sum());
                    assertEquals(3, allocations.stream().mapToInt(Allocation::backordered).sum());
                }
            }
        } finally {
            server.stop(0);
        }
    }

    public record OrderLine(String sku, int quantity, String destination) {
    }

    public record Allocation(String sku, int reserved, int backordered, String destination) {
    }

    private static void reserveBatch(HttpExchange exchange) throws IOException {
        try (exchange) {
            String contentType = exchange.getRequestHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
            if (!"POST".equals(exchange.getRequestMethod()) || contentType == null
                    || !MediaType.APPLICATION_JSON_TYPE.isCompatible(MediaType.valueOf(contentType))
                    || !MediaType.APPLICATION_JSON.equals(exchange.getRequestHeaders().getFirst(HttpHeaders.ACCEPT))) {
                exchange.sendResponseHeaders(415, -1);
                return;
            }
            ObjectMapper mapper = new ObjectMapper();
            JsonNode submitted = mapper.readTree(exchange.getRequestBody());
            JsonNode expected = mapper.readTree("""
                    [
                      {"sku":"blue-widget","quantity":3,"destination":"Caf\u00e9 counter"},
                      {"sku":"red-widget","quantity":5,"destination":"Back room"}
                    ]
                    """);
            if (!expected.equals(submitted)) {
                exchange.sendResponseHeaders(422, -1);
                return;
            }
            byte[] result = """
                    [
                      {"sku":"blue-widget","reserved":3,"backordered":0,"destination":"Caf\u00e9 counter"},
                      {"sku":"red-widget","reserved":2,"backordered":3,"destination":"Back room"}
                    ]
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
            exchange.getResponseHeaders().set("X-Reservation-Batch", "batch-42");
            exchange.sendResponseHeaders(201, result.length);
            exchange.getResponseBody().write(result);
        }
    }
}
