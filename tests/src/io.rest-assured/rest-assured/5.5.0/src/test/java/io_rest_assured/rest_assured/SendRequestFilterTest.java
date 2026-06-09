/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.internal.filter.SendRequestFilter;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SendRequestFilterTest {
    private static final String OBSERVED_HEADER = "X-Send-Request-Filter";
    private static final String OBSERVED_VALUE = "request-reached-server";

    @Test
    void terminalFilterSendsPreparedRequest() throws Throwable {
        assertThat(invokeGeneratedClassLookup(SendRequestFilter.class.getName()))
                .isSameAs(SendRequestFilter.class);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/send-request-filter", SendRequestFilterTest::sendObservedHeader);
        server.start();
        RestAssured.reset();

        try {
            Response response = RestAssured
                    .given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .header(OBSERVED_HEADER, OBSERVED_VALUE)
                    .when()
                    .get("/send-request-filter");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString()).isEqualTo(OBSERVED_VALUE);
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        MethodHandle generatedClassHelper = MethodHandles.privateLookupIn(
                SendRequestFilter.class,
                MethodHandles.lookup())
                .findStatic(
                        SendRequestFilter.class,
                        "class$",
                        MethodType.methodType(Class.class, String.class));

        return (Class<?>) generatedClassHelper.invoke(className);
    }

    private static void sendObservedHeader(HttpExchange exchange) throws IOException {
        byte[] responseBytes = exchange.getRequestHeaders()
                .getFirst(OBSERVED_HEADER)
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        } finally {
            exchange.close();
        }
    }
}
