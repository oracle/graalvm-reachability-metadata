/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.assertion.StreamVerifier;
import io.restassured.builder.ResponseBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class StreamVerifierTest {
    @Test
    void resolvesCompilerGeneratedClassResolver() throws Throwable {
        Class<?> resolvedClass = (Class<?>) classResolver().invokeExact(
                "io.restassured.assertion.StreamVerifier");

        assertSame(StreamVerifier.class, resolvedClass);
    }

    @Test
    void resolvesCompilerGeneratedClassResolverThroughGroovyStaticDispatch() {
        Object resolvedClass = InvokerHelper.invokeStaticMethod(
                StreamVerifier.class,
                "class$",
                new Object[] {"io.restassured.assertion.StreamVerifier"});

        assertSame(StreamVerifier.class, resolvedClass);
    }

    @Test
    void reportsUnknownCompilerGeneratedClassAsNoClassDefFoundError() throws Throwable {
        MethodHandle resolver = classResolver();
        NoClassDefFoundError error = assertThrows(
                NoClassDefFoundError.class,
                () -> {
                    Class<?> resolvedClass = (Class<?>) resolver.invokeExact(
                            "io.restassured.assertion.StreamVerifierMissingClass");
                    assertSame(StreamVerifier.class, resolvedClass);
                });

        assertEquals("io.restassured.assertion.StreamVerifierMissingClass", error.getMessage());
    }

    @Test
    void createsJsonAssertionForContentTypeAwareBodyPathValidation() {
        Response response = new ResponseBuilder()
                .setStatusCode(200)
                .setContentType(ContentType.JSON)
                .setBody("{\"message\":\"hello\",\"count\":2}")
                .build();

        response.then()
                .assertThat()
                .body("message", equalTo("hello"))
                .body("count", equalTo(2));
    }

    private static MethodHandle classResolver() throws IllegalAccessException, NoSuchMethodException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                StreamVerifier.class,
                MethodHandles.lookup());
        return lookup.findStatic(
                StreamVerifier.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
    }
}
