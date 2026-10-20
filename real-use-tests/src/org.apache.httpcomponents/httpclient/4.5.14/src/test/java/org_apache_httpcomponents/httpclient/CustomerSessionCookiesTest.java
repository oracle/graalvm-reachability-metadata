/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents.httpclient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises HttpClient's cookie management subsystem through a customer login and logout session.
public class CustomerSessionCookiesTest {
    private static final String SESSION_COOKIE = "customerSession=customer-42";

    @Test
    @Timeout(60)
    public void maintainsSessionOnlyWithinItsPathAndRemovesItOnLogout() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/account/login", exchange -> {
            String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (!"POST".equals(exchange.getRequestMethod())
                    || !"username=alice&password=local-secret".equals(form)) {
                respond(exchange, 400, "Invalid login");
                return;
            }
            exchange.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE + "; Path=/account; HttpOnly");
            respond(exchange, 200, "Signed in");
        });
        server.createContext("/account/profile", exchange -> {
            if (hasSession(exchange)) {
                respond(exchange, 200, "Alice: loyalty balance 125");
            } else {
                respond(exchange, 401, "Sign in required");
            }
        });
        server.createContext("/account/logout", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod()) || !hasSession(exchange)) {
                respond(exchange, 401, "Sign in required");
                return;
            }
            exchange.getResponseHeaders().add("Set-Cookie",
                    "customerSession=; Path=/account; Max-Age=0; HttpOnly");
            respond(exchange, 200, "Signed out");
        });
        server.createContext("/public/catalog", exchange -> {
            if (hasSession(exchange)) {
                respond(exchange, 400, "Private session leaked to public catalog");
            } else {
                respond(exchange, 200, "Public catalog");
            }
        });
        server.start();

        RequestConfig config = RequestConfig.custom()
                .setCookieSpec(CookieSpecs.STANDARD)
                .setConnectTimeout(10_000)
                .setSocketTimeout(10_000)
                .setConnectionRequestTimeout(10_000)
                .build();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(config).build()) {
            assertResponse(client, new HttpGet(baseUrl + "/account/profile"), 401, "Sign in required");

            HttpPost login = new HttpPost(baseUrl + "/account/login");
            login.setEntity(new UrlEncodedFormEntity(List.of(
                    new BasicNameValuePair("username", "alice"),
                    new BasicNameValuePair("password", "local-secret")), StandardCharsets.UTF_8));
            assertResponse(client, login, 200, "Signed in");
            assertResponse(client, new HttpGet(baseUrl + "/account/profile"),
                    200, "Alice: loyalty balance 125");
            assertResponse(client, new HttpGet(baseUrl + "/public/catalog"), 200, "Public catalog");

            assertResponse(client, new HttpPost(baseUrl + "/account/logout"), 200, "Signed out");
            assertResponse(client, new HttpGet(baseUrl + "/account/profile"), 401, "Sign in required");
        } finally {
            server.stop(0);
        }
    }

    private static boolean hasSession(HttpExchange exchange) {
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
        return cookies != null && cookies.stream()
                .flatMap(value -> Arrays.stream(value.split(";")))
                .map(String::trim)
                .anyMatch(SESSION_COOKIE::equals);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        try (exchange) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private static void assertResponse(CloseableHttpClient client, HttpUriRequest request,
                                       int expectedStatus, String expectedBody) throws IOException {
        try (CloseableHttpResponse response = client.execute(request)) {
            assertEquals(expectedStatus, response.getStatusLine().getStatusCode(), request.getURI().toString());
            assertEquals(expectedBody, EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
        }
    }
}
