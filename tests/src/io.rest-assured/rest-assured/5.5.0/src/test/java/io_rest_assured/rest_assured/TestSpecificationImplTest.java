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
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.Method;
import io.restassured.response.Response;
import io.restassured.specification.RequestSender;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestSpecificationImplTest {
    private static final String DYNAMICALLY_LOADED_CLASS_NAME = "io.restassured.http.Method";

    @Test
    void sendsRequestFromCombinedSpecificationsUsingMethodEnum() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/combined", TestSpecificationImplTest::echoRequestDetails);
        server.start();
        RestAssured.reset();

        try {
            RequestSpecification requestSpecification = new RequestSpecBuilder()
                    .setBaseUri("http://127.0.0.1")
                    .setPort(server.getAddress().getPort())
                    .addHeader("X-Test-Spec", "combined")
                    .build();
            ResponseSpecification responseSpecification = new ResponseSpecBuilder()
                    .expectStatusCode(200)
                    .build();

            RequestSender requestSender = RestAssured.given(
                    requestSpecification,
                    responseSpecification);
            Object dynamicallyResolvedClass = InvokerHelper.invokeStaticMethod(
                    requestSender.getClass(),
                    "class$",
                    DYNAMICALLY_LOADED_CLASS_NAME);
            Response response = requestSender.request(Method.GET, "/combined");

            assertThat(dynamicallyResolvedClass).isSameAs(Method.class);
            assertThat(response.asString()).isEqualTo("GET combined");
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static void echoRequestDetails(HttpExchange exchange) throws IOException {
        List<String> headerValues = exchange.getRequestHeaders().get("X-Test-Spec");
        String headerValue = headerValues == null ? "" : String.join(",", headerValues);
        String response = exchange.getRequestMethod() + " " + headerValue;
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);

        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }
}
