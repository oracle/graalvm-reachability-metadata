/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import io.restassured.internal.mapping.ObjectMapperSerializationContextImpl;
import io.restassured.mapper.ObjectMapper;
import io.restassured.mapper.ObjectMapperDeserializationContext;
import io.restassured.mapper.ObjectMapperSerializationContext;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.SpecificationQuerier;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ObjectMapperSerializationContextImplTest {
    @Test
    void requestBodySerializationSuppliesCustomMapperContext() throws Throwable {
        Class<?> dynamicallyResolvedClass = invokeGeneratedClassLookup(String.class.getName());
        assertSame(String.class, dynamicallyResolvedClass);

        SerializedPayload payload = new SerializedPayload("accepted");
        ContextReadingMapper mapper = new ContextReadingMapper();
        String contentType = "application/vnd.rest-assured-test+json; charset=UTF-8";

        RequestSpecification specification = given()
                .contentType(contentType)
                .body(payload, mapper);

        String requestBody = SpecificationQuerier.query(specification).getBody();
        assertEquals("{\"message\":\"accepted\"}", requestBody);
        assertSame(ObjectMapperSerializationContextImpl.class, mapper.contextClass);
        assertSame(payload, mapper.objectToSerialize);
        assertSame(payload, mapper.typedObjectToSerialize);
        assertEquals(contentType, mapper.contentType);
        assertEquals(StandardCharsets.UTF_8.name(), mapper.charset);
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Exception {
        Class<?> contextClass = ObjectMapperSerializationContextImpl.class;
        Method classHelper = contextClass.getDeclaredMethod("class$", String.class);
        classHelper.setAccessible(true);
        return (Class<?>) classHelper.invoke(null, className);
    }

    public static class SerializedPayload {
        private final String message;

        SerializedPayload(String message) {
            this.message = message;
        }
    }

    private static class ContextReadingMapper implements ObjectMapper {
        private Object objectToSerialize;
        private SerializedPayload typedObjectToSerialize;
        private String contentType;
        private String charset;
        private Class<?> contextClass;

        @Override
        public Object deserialize(ObjectMapperDeserializationContext context) {
            throw new UnsupportedOperationException("Deserialization is not used by this test");
        }

        @Override
        public Object serialize(ObjectMapperSerializationContext context) {
            objectToSerialize = context.getObjectToSerialize();
            typedObjectToSerialize = context.getObjectToSerializeAs(SerializedPayload.class);
            contentType = context.getContentType();
            charset = context.getCharset();
            contextClass = context.getClass();
            return "{\"message\":\"" + typedObjectToSerialize.message + "\"}";
        }
    }
}
