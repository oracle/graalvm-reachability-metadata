/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.builder.ResponseBuilder;
import io.restassured.internal.RestAssuredResponseOptionsGroovyImplAnonymous1Access;
import io.restassured.mapper.ObjectMapper;
import io.restassured.mapper.ObjectMapperDeserializationContext;
import io.restassured.mapper.ObjectMapperSerializationContext;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RestAssuredResponseOptionsGroovyImplAnonymous1Test {
    @Test
    void resolvesJdkClassThroughAnonymousCompilerGeneratedClassResolver() throws Throwable {
        String className = String.join(".", "java", "lang", "String");

        Class<?> resolvedClass = RestAssuredResponseOptionsGroovyImplAnonymous1Access
                .resolveWithCompilerGeneratedClassResolver(className);

        assertEquals(String.class, resolvedClass);
    }

    @Test
    void resolvesAnonymousDataToDeserializeClassThroughOwnResolver() throws Throwable {
        String className = String.join(
                ".",
                "io",
                "restassured",
                "internal",
                "RestAssuredResponseOptionsGroovyImpl" + Character.toString((char) 36) + "1");

        Class<?> resolvedClass = RestAssuredResponseOptionsGroovyImplAnonymous1Access
                .resolveWithCompilerGeneratedClassResolver(className);

        assertEquals(className, resolvedClass.getName());
    }

    @Test
    void reportsMissingClassThroughAnonymousCompilerGeneratedClassResolver() {
        String missingClassName = String.join(
                ".",
                "io",
                "restassured",
                "internal",
                "RestAssuredResponseOptionsGroovyImplAnonymous1Missing");

        NoClassDefFoundError error = assertThrows(
                NoClassDefFoundError.class,
                () -> RestAssuredResponseOptionsGroovyImplAnonymous1Access
                        .resolveWithCompilerGeneratedClassResolver(missingClassName));

        assertEquals(missingClassName, error.getMessage());
    }

    @Test
    void customObjectMapperReadsResponseBodyThroughDeserializationContext() {
        String body = "custom mapper body";
        Response response = new ResponseBuilder()
                .setBody(body)
                .setContentType("text/plain; charset=UTF-8")
                .build();

        String mappedBody = response.as(String.class, new ObjectMapper() {
            @Override
            public Object deserialize(ObjectMapperDeserializationContext context) {
                String asString = context.getDataToDeserialize().asString();
                String asBytes = new String(context.getDataToDeserialize().asByteArray(), StandardCharsets.UTF_8);
                String asStream = readAll(context.getDataToDeserialize().asInputStream());

                return String.join("|", asString, asBytes, asStream, context.getContentType());
            }

            @Override
            public Object serialize(ObjectMapperSerializationContext context) {
                throw new UnsupportedOperationException("This test only exercises response deserialization");
            }
        });

        assertEquals(body + "|" + body + "|" + body + "|text/plain; charset=UTF-8", mappedBody);
    }

    private static String readAll(InputStream inputStream) {
        try (InputStream stream = inputStream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
