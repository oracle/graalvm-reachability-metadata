/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents.httpclient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises HttpClient's redirect handling subsystem by submitting an order and retrieving its receipt.
public class CheckoutRedirectTest {
    @Test
    @Timeout(60)
    public void followsPostToGetAndRelativeRedirectsWithoutResubmittingTheOrder() throws IOException {
        List<String> requests = new CopyOnWriteArrayList<>();
        String receipt = """
                Order ORD-1042
                Item: notebook
                Quantity: 2
                Total: 12.00
                """;
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/checkout", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " " + body);
            if (!"POST".equals(exchange.getRequestMethod()) || !"item=notebook&quantity=2".equals(body)) {
                respond(exchange, 400, "Invalid order submission");
                return;
            }
            redirect(exchange, 303, "/orders/ORD-1042");
        });
        server.createContext("/orders/ORD-1042", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            if (!isEmptyGet(exchange)) {
                respond(exchange, 400, "The order must not be submitted again");
                return;
            }
            redirect(exchange, 302, "ORD-1042/receipt?format=text");
        });
        server.createContext("/orders/ORD-1042/receipt", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            if (!isEmptyGet(exchange) || !"format=text".equals(exchange.getRequestURI().getRawQuery())) {
                respond(exchange, 400, "Invalid receipt request");
                return;
            }
            respond(exchange, 200, receipt);
        });
        server.start();

        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(10_000)
                .setSocketTimeout(10_000)
                .setConnectionRequestTimeout(10_000)
                .setMaxRedirects(5)
                .build();
        String checkoutUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/checkout";
        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(config).build()) {
            HttpPost checkout = new HttpPost(checkoutUrl);
            checkout.setEntity(new UrlEncodedFormEntity(List.of(
                    new BasicNameValuePair("item", "notebook"),
                    new BasicNameValuePair("quantity", "2")), StandardCharsets.UTF_8));
            try (CloseableHttpResponse response = client.execute(checkout)) {
                assertEquals(200, response.getStatusLine().getStatusCode());
                assertEquals(receipt, EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
            }
            assertEquals(List.of(
                    "POST /checkout item=notebook&quantity=2",
                    "GET /orders/ORD-1042",
                    "GET /orders/ORD-1042/receipt?format=text"), requests);
        } finally {
            server.stop(0);
        }
    }

    private static boolean isEmptyGet(HttpExchange exchange) throws IOException {
        return "GET".equals(exchange.getRequestMethod()) && exchange.getRequestBody().readAllBytes().length == 0;
    }

    private static void redirect(HttpExchange exchange, int status, String location) throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().set("Location", location);
            exchange.sendResponseHeaders(status, -1);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        try (exchange) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
