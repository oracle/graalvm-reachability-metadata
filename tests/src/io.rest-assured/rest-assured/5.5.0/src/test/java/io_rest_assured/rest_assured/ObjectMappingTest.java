/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.LinkedHashMap;
import java.util.Map;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.internal.mapping.ObjectMapping;
import io.restassured.mapper.ObjectMapperType;
import io.restassured.specification.FilterableRequestSpecification;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectMappingTest {
    @Test
    void serializesRequestBodyWithExplicitGsonMapper() throws Throwable {
        String objectMappingClassName = String.join(
                ".", "io", "restassured", "internal", "mapping", "ObjectMapping");
        assertThat(invokeGeneratedClassLookup(objectMappingClassName))
                .isEqualTo(ObjectMapping.class);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("name", "rest-assured");
        message.put("priority", 5);

        RestAssured.reset();
        try {
            FilterableRequestSpecification specification = (FilterableRequestSpecification) RestAssured
                    .given()
                    .contentType(ContentType.JSON)
                    .body(message, ObjectMapperType.GSON);

            String json = specification.getBody();
            assertThat(json).contains("\"name\":\"rest-assured\"");
            assertThat(json).contains("\"priority\":5");
        } finally {
            RestAssured.reset();
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(ObjectMapping.class, MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                ObjectMapping.class, "class$", MethodType.methodType(Class.class, String.class));
        return (Class<?>) classLookup.invokeExact(className);
    }
}
