/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.assertion.ResponseTimeMatcher;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ResponseTimeMatcherTest {
    @Test
    void resolvesCompilerGeneratedClassResolver() throws Throwable {
        Class<?> resolvedClass = (Class<?>) classResolver().invokeWithArguments(
                "io.restassured.assertion.ResponseTimeMatcher");

        assertSame(ResponseTimeMatcher.class, resolvedClass);
    }

    @Test
    void reportsUnknownCompilerGeneratedClassAsNoClassDefFoundError() throws Throwable {
        NoClassDefFoundError error = assertThrows(
                NoClassDefFoundError.class,
                () -> classResolver().invokeWithArguments(
                        "io.restassured.assertion.ResponseTimeMatcherMissingClass"));

        assertEquals("io.restassured.assertion.ResponseTimeMatcherMissingClass", error.getMessage());
    }

    private static MethodHandle classResolver() throws IllegalAccessException, NoSuchMethodException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                ResponseTimeMatcher.class,
                MethodHandles.lookup());
        return lookup.findStatic(
                ResponseTimeMatcher.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
    }
}
