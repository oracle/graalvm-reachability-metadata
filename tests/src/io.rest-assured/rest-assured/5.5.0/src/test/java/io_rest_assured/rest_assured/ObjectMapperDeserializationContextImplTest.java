/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import io.restassured.builder.ResponseBuilder;
import io.restassured.internal.mapping.ObjectMapperDeserializationContextImpl;
import io.restassured.mapper.ObjectMapper;
import io.restassured.mapper.ObjectMapperDeserializationContext;
import io.restassured.mapper.ObjectMapperSerializationContext;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ObjectMapperDeserializationContextImplTest {
    @Test
    void responseDeserializationSuppliesCustomMapperContext() {
        String contentType = "application/vnd.rest-assured-test+json; charset=UTF-8";
        Response response = new ResponseBuilder()
                .setStatusCode(200)
                .setContentType(contentType)
                .setBody("{\"message\":\"accepted\"}")
                .build();

        DeserializedPayload payload = response.as(DeserializedPayload.class, new ContextReadingMapper());

        assertEquals(contentType, payload.contentType);
        assertEquals(StandardCharsets.UTF_8.name(), payload.charset);
        assertEquals(DeserializedPayload.class, payload.type);
        assertEquals("{\"message\":\"accepted\"}", payload.body);
        assertSame(ObjectMapperDeserializationContextImpl.class, payload.contextClass);
    }

    public static class DeserializedPayload {
        final String contentType;
        final String charset;
        final Type type;
        final String body;
        final Class<?> contextClass;

        DeserializedPayload(
                String contentType,
                String charset,
                Type type,
                String body,
                Class<?> contextClass) {
            this.contentType = contentType;
            this.charset = charset;
            this.type = type;
            this.body = body;
            this.contextClass = contextClass;
        }
    }

    private static class ContextReadingMapper implements ObjectMapper {
        @Override
        public Object deserialize(ObjectMapperDeserializationContext context) {
            return new DeserializedPayload(
                    context.getContentType(),
                    context.getCharset(),
                    context.getType(),
                    context.getDataToDeserialize().asString(),
                    context.getClass());
        }

        @Override
        public Object serialize(ObjectMapperSerializationContext context) {
            throw new UnsupportedOperationException("Serialization is not used by this test");
        }
    }
}
