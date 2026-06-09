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
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.filter.Filter;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.config.MultiPartConfig.multiPartConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplInner_registerRestAssuredEncoders_closure24_closure51Test {
    private static final String BOUNDARY = "RestAssuredClosure51Boundary";
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.RequestSpecificationImpl"
            + "$_registerRestAssuredEncoders_closure24_closure51";

    @Test
    void serializesMultipartPartsWithRestAssuredEntityBuilder() throws Throwable {
        MethodHandle classHelper = generatedClassLookup();
        AtomicReference<Class<?>> dynamicallyResolvedClass = new AtomicReference<>();
        Filter resolveGeneratedClassHelper = (requestSpecification, responseSpecification, context) -> {
            try {
                dynamicallyResolvedClass.set(invokeGeneratedClassLookup(classHelper, RestAssured.class.getName()));
            } catch (Throwable throwable) {
                throw new AssertionError(throwable);
            }
            return context.next(requestSpecification, responseSpecification);
        };

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/multipart",
                RequestSpecificationImplInner_registerRestAssuredEncoders_closure24_closure51Test::handleMultipart);
        server.start();
        RestAssured.reset();

        try {
            Response response = given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .config(config().multiPartConfig(multiPartConfig()
                            .defaultSubtype("form-data")
                            .defaultBoundary(BOUNDARY)))
                    .filter(resolveGeneratedClassHelper)
                    .multiPart("document", "document.txt", "closure51 multipart".getBytes(StandardCharsets.UTF_8),
                            "text/plain")
                    .when()
                    .post("/multipart");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString()).isEqualTo("multipart part serialized");
            assertThat(dynamicallyResolvedClass.get()).isEqualTo(RestAssured.class);
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static MethodHandle generatedClassLookup() throws ClassNotFoundException, NoSuchMethodException,
            IllegalAccessException {
        Class<?> closureClass = RequestSpecificationImplInner_registerRestAssuredEncoders_closure24_closure51Test.class
                .getClassLoader()
                .loadClass(CLOSURE_CLASS_NAME);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(closureClass, MethodHandles.lookup());
        return lookup.findStatic(closureClass, "class$", MethodType.methodType(Class.class, String.class));
    }

    private static Class<?> invokeGeneratedClassLookup(MethodHandle classHelper, String className) throws Throwable {
        return (Class<?>) classHelper.invokeExact(className);
    }

    private static void handleMultipart(HttpExchange exchange) throws IOException {
        try {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
            boolean multipartPartWasSerialized = "POST".equals(exchange.getRequestMethod())
                    && contentType != null
                    && contentType.startsWith("multipart/form-data")
                    && contentType.contains(BOUNDARY)
                    && body.contains("--" + BOUNDARY)
                    && body.contains("name=\"document\"")
                    && body.contains("filename=\"document.txt\"")
                    && body.contains("Content-Type: text/plain")
                    && body.contains("closure51 multipart")
                    && !body.contains("Content-Transfer-Encoding");
            byte[] responseBytes = (multipartPartWasSerialized
                    ? "multipart part serialized"
                    : "multipart part not serialized").getBytes(StandardCharsets.UTF_8);

            exchange.sendResponseHeaders(
                    multipartPartWasSerialized ? HttpURLConnection.HTTP_OK : HttpURLConnection.HTTP_BAD_REQUEST,
                    responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } finally {
            exchange.close();
        }
    }
}
