/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.filter.Filter;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.SpecificationQuerier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplInner_getURI_closure39Test {
    @Test
    void specificationQuerierReadsUriAfterNamedPathParametersAreApplied() {
        RestAssured.reset();

        try {
            RequestSpecification specification = RestAssured
                    .given()
                    .baseUri("http://example.com")
                    .basePath("/api")
                    .pathParam("orderId", "first");
            ((FilterableRequestSpecification) specification).path("/orders/{orderId}");

            String requestUri = SpecificationQuerier.query(specification).getURI();

            assertThat(requestUri).isEqualTo("http://example.com/api/orders/first");
        } finally {
            RestAssured.reset();
        }
    }

    @Test
    void filterCanReadUriAfterUnnamedPathParametersAreApplied() throws IOException {
        AtomicReference<String> requestUri = new AtomicReference<>();
        Filter captureRequestUri = (requestSpecification, responseSpecification, context) -> {
            requestUri.set(requestSpecification.getURI());
            return context.next(requestSpecification, responseSpecification);
        };

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/orders/first/items/second",
                RequestSpecificationImplInner_getURI_closure39Test::sendObservedPath);
        server.start();
        RestAssured.reset();

        try {
            int serverPort = server.getAddress().getPort();

            Response response = RestAssured
                    .given()
                    .baseUri("http://127.0.0.1")
                    .port(serverPort)
                    .basePath("/api")
                    .filter(captureRequestUri)
                    .when()
                    .get("/orders/{orderId}/items/{itemId}", "first", "second");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString()).isEqualTo("/api/orders/first/items/second");
            assertThat(requestUri.get())
                    .isEqualTo("http://127.0.0.1:" + serverPort + "/api/orders/first/items/second");
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static void sendObservedPath(HttpExchange exchange) throws IOException {
        byte[] responseBytes = exchange.getRequestURI().getPath().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }
}
