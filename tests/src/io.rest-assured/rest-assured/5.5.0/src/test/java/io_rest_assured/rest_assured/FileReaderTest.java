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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.internal.support.FileReader;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class FileReaderTest {
    @Test
    void resolvesRuntimeSelectedClassThroughGeneratedGroovyClassLookup() {
        MetaClass metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(FileReader.class);

        Class<?> resolvedClass = (Class<?>) metaClass.invokeStaticMethod(
                FileReader.class,
                "class$",
                new Object[] {runtimeSelectedRestAssuredTypeName()});

        assertThat(resolvedClass.getName()).isEqualTo("io.restassured.http.ContentType");
    }

    @Test
    void sendsTextFileBodyThroughPublicDsl(@TempDir Path tempDir) throws IOException {
        Path requestFile = tempDir.resolve("request-body.txt");
        Files.writeString(requestFile, "file body from rest assured", StandardCharsets.UTF_8);

        AtomicReference<String> observedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", exchange -> captureBody(exchange, observedBody));
        server.start();
        RestAssured.reset();

        try {
            Response response = RestAssured
                    .given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .contentType(ContentType.TEXT)
                    .body(requestFile.toFile())
                    .when()
                    .post("/echo");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(observedBody.get()).isEqualTo("file body from rest assured");
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static String runtimeSelectedRestAssuredTypeName() {
        String packageName = String.join(".", "io", "restassured", "http");
        String simpleName = new StringBuilder("Content").append("Type").toString();
        return packageName + "." + simpleName;
    }

    private static void captureBody(HttpExchange exchange, AtomicReference<String> observedBody) throws IOException {
        observedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] responseBytes = "ok".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }
}
