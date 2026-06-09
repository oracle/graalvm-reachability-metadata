/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.apache.http.params.CoreProtocolPNames;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.config.HttpClientConfig.httpClientConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplInner_applyHttpClientConfig_closure22Test {
    private static final String USER_AGENT_VALUE = "rest-assured-http-client-config-test";

    @Test
    void configuredHttpClientParameterIsAppliedToRequest() throws Throwable {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/client-params",
                RequestSpecificationImplInner_applyHttpClientConfig_closure22Test::echoUserAgent);
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();

        try {
            Response response = given()
                    .config(config().httpClient(httpClientConfig()
                            .setParam(CoreProtocolPNames.USER_AGENT, USER_AGENT_VALUE)))
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/client-params");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString()).isEqualTo(USER_AGENT_VALUE);
        } finally {
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void echoUserAgent(HttpExchange exchange) throws IOException {
        String response = exchange.getRequestHeaders().getFirst("User-Agent");
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }
}
