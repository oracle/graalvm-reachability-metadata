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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import groovy.lang.Closure;
import groovy.lang.GroovySystem;
import groovy.lang.Interceptor;
import groovy.lang.MetaClass;
import groovy.lang.MetaClassRegistry;
import groovy.lang.ProxyMetaClass;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JohnzonMapperInner_serialize_closure1Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.mapping.JohnzonMapper"
            + "$_serialize_closure1";

    @Test
    void serializesRequestBodyThroughJohnzonClosure() throws Throwable {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/johnzon-closure", JohnzonMapperInner_serialize_closure1Test::handleJohnzonPayload);
        server.start();
        RestAssured.reset();
        AtomicReference<Closure<?>> generatedJohnzonClosure = new AtomicReference<>();
        Class<?> closureClass = JohnzonMapperInner_serialize_closure1Test.class
                .getClassLoader()
                .loadClass(CLOSURE_CLASS_NAME);
        MetaClassRegistry registry = GroovySystem.getMetaClassRegistry();
        MetaClass originalMetaClass = registry.getMetaClass(closureClass);
        ProxyMetaClass proxyMetaClass = ProxyMetaClass.getInstance(closureClass);
        proxyMetaClass.setInterceptor(new CapturingClosureCallInterceptor(generatedJohnzonClosure));
        registry.setMetaClass(closureClass, proxyMetaClass);

        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("message", "closure");
            requestBody.put("count", 3);

            Response response = given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .contentType(ContentType.JSON)
                    .body(requestBody, ObjectMapperType.JOHNZON)
                    .when()
                    .post("/johnzon-closure");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString()).isEqualTo("johnzon closure serialized request");
            assertThat(invokeGeneratedClassLookup(generatedJohnzonClosure.get(), RestAssured.class.getName()))
                    .isEqualTo(RestAssured.class);
        } finally {
            registry.setMetaClass(closureClass, originalMetaClass);
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(Closure<?> closure, String className) {
        assertNotNull(closure);
        assertThat(closure.getClass().getName()).isEqualTo(CLOSURE_CLASS_NAME);
        Object resolvedClass = closure.getMetaClass()
                .invokeStaticMethod(closure, "class$", new Object[] {className});
        return (Class<?>) resolvedClass;
    }

    private static void handleJohnzonPayload(HttpExchange exchange) throws IOException {
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean closureSerializedRequest = "POST".equals(exchange.getRequestMethod())
                    && body.contains("\"message\"")
                    && body.contains("\"closure\"")
                    && body.contains("\"count\"")
                    && body.contains("3");
            byte[] responseBytes = (closureSerializedRequest
                    ? "johnzon closure serialized request"
                    : "johnzon closure did not serialize request").getBytes(StandardCharsets.UTF_8);

            exchange.sendResponseHeaders(
                    closureSerializedRequest ? HttpURLConnection.HTTP_OK : HttpURLConnection.HTTP_BAD_REQUEST,
                    responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } finally {
            exchange.close();
        }
    }

    private static final class CapturingClosureCallInterceptor implements Interceptor {
        private final AtomicReference<Closure<?>> generatedJohnzonClosure;

        private CapturingClosureCallInterceptor(AtomicReference<Closure<?>> generatedJohnzonClosure) {
            this.generatedJohnzonClosure = generatedJohnzonClosure;
        }

        @Override
        public Object beforeInvoke(Object object, String methodName, Object[] arguments) {
            if (object instanceof Closure<?> closure && CLOSURE_CLASS_NAME.equals(closure.getClass().getName())) {
                generatedJohnzonClosure.set(closure);
            }
            return null;
        }

        @Override
        public Object afterInvoke(Object object, String methodName, Object[] arguments, Object result) {
            return result;
        }

        @Override
        public boolean doInvoke() {
            return true;
        }
    }
}
