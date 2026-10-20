/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents.httpclient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises HttpClient's response handler subsystem to download shipping instructions and reject missing shipments.
public class ShipmentInstructionsResponseHandlerTest {
    @Test
    @Timeout(60)
    public void reportsMissingShipmentAndDecodesInstructionsUsingTheServersCharset() throws IOException {
        String instructions = """
                Shipment: SHIP-204
                Recipient: Andr\u00e9 M\u00fcller
                Delivery: Leave at the caf\u00e9 reception.
                """;
        List<String> requests = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/shipments/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requests.add(exchange.getRequestMethod() + " " + path);
            if (!"GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 405, "Only instruction downloads are supported");
            } else if ("/shipments/SHIP-204/instructions".equals(path)) {
                respond(exchange, 200, instructions);
            } else {
                respond(exchange, 404, "No shipping instructions exist for that shipment");
            }
        });
        server.start();

        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(10_000)
                .setSocketTimeout(10_000)
                .setConnectionRequestTimeout(10_000)
                .build();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/shipments/";
        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(config).build()) {
            BasicResponseHandler instructionsHandler = new BasicResponseHandler();
            HttpResponseException missingShipment = assertThrows(HttpResponseException.class,
                    () -> client.execute(new HttpGet(baseUrl + "SHIP-unknown/instructions"), instructionsHandler));
            assertEquals(404, missingShipment.getStatusCode());

            // The handler owns response consumption on both the error and success paths.
            String downloaded = client.execute(new HttpGet(baseUrl + "SHIP-204/instructions"), instructionsHandler);
            assertEquals(instructions, downloaded);
            assertEquals(List.of("Shipment: SHIP-204", "Recipient: Andr\u00e9 M\u00fcller",
                    "Delivery: Leave at the caf\u00e9 reception."), downloaded.lines().toList());
            assertEquals(List.of("GET /shipments/SHIP-unknown/instructions",
                    "GET /shipments/SHIP-204/instructions"), requests);
        } finally {
            server.stop(0);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        try (exchange) {
            byte[] bytes = body.getBytes(StandardCharsets.ISO_8859_1);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=ISO-8859-1");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
