/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import groovy.lang.Closure;
import io.restassured.RestAssured;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ResponseSpecificationImplInnerHamcrestAssertionClosure_getClosure_closure1Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal."
            + "ResponseSpecificationImpl$_HamcrestAssertionClosure_getClosure_closure1";

    @Test
    void responseExpectationParsesResponseThroughGeneratedAssertionClosure() throws Throwable {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/message", this::sendJsonResponse);
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();

        try {
            AtomicReference<Closure<?>> generatedClosure = new AtomicReference<>();
            Response response = given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .filter(new CapturingAssertionClosureFilter(generatedClosure))
                    .expect()
                    .statusCode(HttpURLConnection.HTTP_OK)
                    .contentType(ContentType.JSON)
                    .body("message", equalTo("hello"))
                    .when()
                    .get("/message");

            assertEquals("hello", response.path("message"));
            String lookupClassName = "io.restassured.internal.support.FileReader";
            Class<?> resolvedLookupClass = invokeGeneratedClassLookup(
                    generatedClosure.get(),
                    lookupClassName);
            assertEquals(lookupClassName, resolvedLookupClass.getName());
        } finally {
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(Closure<?> closure, String className) throws Throwable {
        assertNotNull(closure);
        assertEquals(CLOSURE_CLASS_NAME, closure.getClass().getName());
        return (Class<?>) closure.getMetaClass()
                .invokeStaticMethod(closure, "class$", new Object[] {className});
    }

    private static final class CapturingAssertionClosureFilter implements Filter {
        private final AtomicReference<Closure<?>> generatedClosure;

        private CapturingAssertionClosureFilter(AtomicReference<Closure<?>> generatedClosure) {
            this.generatedClosure = generatedClosure;
        }

        @Override
        public Response filter(
                FilterableRequestSpecification requestSpecification,
                FilterableResponseSpecification responseSpecification,
                FilterContext context) {
            Object assertionClosure = InvokerHelper.getProperty(responseSpecification, "assertionClosure");
            Closure<?> parsingClosure = (Closure<?>) InvokerHelper.invokeMethod(
                    assertionClosure,
                    "getClosure",
                    new Object[0]);
            generatedClosure.set(parsingClosure);
            return context.next(requestSpecification, responseSpecification);
        }
    }

    private void sendJsonResponse(HttpExchange exchange) throws IOException {
        try {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", ContentType.JSON.toString());
            byte[] body = "{\"message\":\"hello\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }
}
