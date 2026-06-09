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

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import groovy.lang.GroovyObject;
import io.restassured.specification.RedirectSpecification;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class RedirectSpecificationImplTest {
    @Test
    void groovyObjectDispatchResolvesClassesForRedirectSpecification() {
        RedirectSpecification redirectSpecification = given().redirects();
        GroovyObject groovyRedirectSpecification = (GroovyObject) redirectSpecification;

        Object resolvedClass = groovyRedirectSpecification.getMetaClass().invokeStaticMethod(
                redirectSpecification.getClass(), "class$", new Object[] {redirectSpecification.getClass().getName()});

        assertThat(resolvedClass).isEqualTo(redirectSpecification.getClass());
    }

    @Test
    void followsRelativeRedirectConfiguredThroughRedirectSpecification() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/redirect", this::sendRedirect);
        server.createContext("/target", this::sendTarget);
        server.setExecutor(executor);
        server.start();

        try {
            given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .redirects().follow(true)
                    .redirects().max(2)
                    .redirects().allowCircular(false)
                    .redirects().rejectRelative(false)
                    .when()
                    .get("/redirect")
                    .then()
                    .statusCode(HttpURLConnection.HTTP_OK)
                    .body(equalTo("redirect target"));
        } finally {
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void sendRedirect(HttpExchange exchange) throws IOException {
        try {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Location", "/target");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_MOVED_TEMP, -1);
        } finally {
            exchange.close();
        }
    }

    private void sendTarget(HttpExchange exchange) throws IOException {
        try {
            byte[] body = "redirect target".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }
}
