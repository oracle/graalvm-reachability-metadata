/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.common.mapper.DataToDeserialize;
import io.restassured.mapper.ObjectMapper;
import io.restassured.mapper.ObjectMapperDeserializationContext;
import io.restassured.mapper.ObjectMapperSerializationContext;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

public class RestAssuredResponseOptionsGroovyImplAnonymous1Test {
    private static final String RESPONSE_BODY = "custom mapper payload";
    private static final String CONTENT_TYPE = "text/plain; charset=UTF-8";

    @Test
    void customResponseObjectMapperReadsDataThroughResponseDeserializationContext() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/payload", RestAssuredResponseOptionsGroovyImplAnonymous1Test::sendTextResponse);
        server.setExecutor(executor);
        server.start();

        try {
            Response response = given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/payload");

            CapturedResponseBody capturedResponseBody = response.as(
                    CapturedResponseBody.class, new CapturingObjectMapper());

            assertThat(capturedResponseBody.stringBody()).isEqualTo(RESPONSE_BODY);
            assertThat(capturedResponseBody.byteArrayBody()).isEqualTo(RESPONSE_BODY);
            assertThat(capturedResponseBody.inputStreamBody()).isEqualTo(RESPONSE_BODY);
            assertThat(capturedResponseBody.resolvedDataType()).isEqualTo(DataToDeserialize.class);
        } finally {
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void sendTextResponse(HttpExchange exchange) throws IOException {
        try {
            byte[] body = RESPONSE_BODY.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }

    private record CapturedResponseBody(
            String stringBody,
            String byteArrayBody,
            String inputStreamBody,
            Class<?> resolvedDataType) {
    }

    private static final class CapturingObjectMapper implements ObjectMapper {
        @Override
        public Object deserialize(ObjectMapperDeserializationContext context) {
            assertThat(context.getType()).isEqualTo(CapturedResponseBody.class);
            assertThat(context.getContentType()).contains("text/plain");
            assertThat(context.getCharset()).isEqualToIgnoringCase(StandardCharsets.UTF_8.name());

            String stringBody = context.getDataToDeserialize().asString();
            byte[] responseBytes = context.getDataToDeserialize().asByteArray();
            String byteArrayBody = new String(responseBytes, StandardCharsets.UTF_8);
            DataToDeserialize dataToDeserialize = context.getDataToDeserialize();
            Class<?> resolvedDataType = resolveDataTypeWithGeneratedClassHelper(dataToDeserialize);
            String inputStreamBody = readBody(context);
            return new CapturedResponseBody(
                    stringBody, byteArrayBody, inputStreamBody, resolvedDataType);
        }

        @Override
        public Object serialize(ObjectMapperSerializationContext context) {
            throw new UnsupportedOperationException(
                    "This mapper is only used for response deserialization");
        }

        private static Class<?> resolveDataTypeWithGeneratedClassHelper(
                DataToDeserialize dataToDeserialize) {
            try {
                Class<?> implementationClass = dataToDeserialize.getClass();
                assertThat(implementationClass.getName())
                        .endsWith("RestAssuredResponseOptionsGroovyImpl$1");
                Method classHelper = implementationClass.getDeclaredMethod("class$", String.class);
                classHelper.setAccessible(true);
                return (Class<?>) classHelper.invoke(null, DataToDeserialize.class.getName());
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Could not invoke generated Groovy class helper", e);
            }
        }

        private static String readBody(ObjectMapperDeserializationContext context) {
            try (InputStream inputStream = context.getDataToDeserialize().asInputStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new AssertionError("Could not read mapped response body", e);
            }
        }
    }
}
