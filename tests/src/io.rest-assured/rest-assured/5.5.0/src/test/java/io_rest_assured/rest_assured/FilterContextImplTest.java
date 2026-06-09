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
import java.util.ArrayList;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.filter.Filter;
import io.restassured.internal.filter.FilterContextImpl;
import io.restassured.response.Response;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FilterContextImplTest {
    @Test
    void filterContextPassesSharedValuesToNextFilter() throws Throwable {
        assertThat(invokeGeneratedClassLookup(FilterContextImpl.class.getName()))
                .isSameAs(FilterContextImpl.class);

        List<String> invocations = new ArrayList<>();
        Filter seedContextValue = (requestSpecification, responseSpecification, context) -> {
            context.setValue("correlation-id", "ctx-123");
            invocations.add("seed:" + context.hasValue("correlation-id"));

            return context.next(requestSpecification, responseSpecification);
        };
        Filter continueWithContextValue = (requestSpecification, responseSpecification, context) -> {
            String correlationId = context.getValue("correlation-id");
            assertThat(context.hasValue("correlation-id")).isTrue();
            assertThat(context.hasValue("correlation-id", "ctx-123")).isTrue();
            assertThat(context.hasValue("missing-value")).isFalse();
            invocations.add("continue:" + correlationId);

            requestSpecification.header("X-Filter-Context", correlationId);
            return context.next(requestSpecification, responseSpecification);
        };

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/filter-context", FilterContextImplTest::sendObservedHeader);
        server.start();
        RestAssured.reset();

        try {
            Response response = RestAssured
                    .given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .filters(seedContextValue, continueWithContextValue)
                    .when()
                    .get("/filter-context");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString()).isEqualTo("ctx-123");
            assertThat(invocations).containsExactly("seed:true", "continue:ctx-123");
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) {
        return (Class<?>) InvokerHelper.invokeStaticMethod(FilterContextImpl.class, "class$", className);
    }

    private static void sendObservedHeader(HttpExchange exchange) throws IOException {
        String observedHeader = exchange.getRequestHeaders().getFirst("X-Filter-Context");
        byte[] responseBytes = observedHeader.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }
}
