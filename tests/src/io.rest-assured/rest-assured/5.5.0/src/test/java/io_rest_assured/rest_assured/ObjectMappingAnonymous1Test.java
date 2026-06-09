/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.common.mapper.DataToDeserialize;
import io.restassured.config.RestAssuredConfig;
import io.restassured.mapper.ObjectMapper;
import io.restassured.mapper.ObjectMapperDeserializationContext;
import io.restassured.mapper.ObjectMapperSerializationContext;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.config.ObjectMapperConfig.objectMapperConfig;
import static org.assertj.core.api.Assertions.assertThat;

public class ObjectMappingAnonymous1Test {
    private static final String RESPONSE_BODY = "object mapping payload";
    private static final String CONTENT_TYPE = "application/json; charset=UTF-8";

    @Test
    void defaultObjectMapperReceivesObjectMappingDataToDeserialize() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/payload", ObjectMappingAnonymous1Test::sendJsonResponse);
        server.setExecutor(executorService);
        server.start();
        RestAssured.reset();

        try {
            RestAssuredConfig config = RestAssuredConfig.config().objectMapperConfig(
                    objectMapperConfig().defaultObjectMapper(new CapturingObjectMapper()));

            Response response = given()
                    .config(config)
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/payload");

            CapturedResponseBody capturedResponseBody = response.as(CapturedResponseBody.class);

            assertThat(capturedResponseBody.stringBody()).isEqualTo(RESPONSE_BODY);
            assertThat(capturedResponseBody.byteArrayBody()).isEqualTo(RESPONSE_BODY);
            assertThat(capturedResponseBody.inputStreamBody()).isEqualTo(RESPONSE_BODY);
            assertThat(capturedResponseBody.resolvedDataType().getName())
                    .isEqualTo("io.restassured.internal.mapping.ObjectMapping$1");
        } finally {
            RestAssured.reset();
            server.stop(0);
            executorService.shutdownNow();
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void sendJsonResponse(HttpExchange exchange) throws IOException {
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
            assertThat(context.getContentType()).contains("application/json");
            assertThat(context.getCharset()).isEqualToIgnoringCase(StandardCharsets.UTF_8.name());

            DataToDeserialize dataToDeserialize = context.getDataToDeserialize();
            assertThat(dataToDeserialize.getClass().getName())
                    .endsWith("ObjectMapping$1");

            String stringBody = dataToDeserialize.asString();
            String byteArrayBody = new String(dataToDeserialize.asByteArray(), StandardCharsets.UTF_8);
            String inputStreamBody = readBody(dataToDeserialize);
            return new CapturedResponseBody(
                    stringBody, byteArrayBody, inputStreamBody, dataToDeserialize.getClass());
        }

        @Override
        public Object serialize(ObjectMapperSerializationContext context) {
            throw new UnsupportedOperationException(
                    "This mapper is only used for response deserialization");
        }

        private static String readBody(DataToDeserialize dataToDeserialize) {
            try (InputStream inputStream = dataToDeserialize.asInputStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new AssertionError("Could not read mapped response body", e);
            }
        }
    }
}
