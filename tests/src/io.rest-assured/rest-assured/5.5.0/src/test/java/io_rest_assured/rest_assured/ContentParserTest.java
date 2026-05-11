/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.internal.ContentParser;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ContentParserTest {
    @Test
    void resolvesCompilerGeneratedClassResolverForReachableJdkType() throws Throwable {
        Class<?> resolvedClass = (Class<?>) classResolver().invokeWithArguments("java.lang.String");

        assertSame(String.class, resolvedClass);
    }

    @Test
    void reportsUnknownCompilerGeneratedClassAsNoClassDefFoundError() throws Throwable {
        NoClassDefFoundError error = assertThrows(
                NoClassDefFoundError.class,
                () -> classResolver().invokeWithArguments("io.restassured.internal.ContentParserMissingClass"));

        assertEquals("io.restassured.internal.ContentParserMissingClass", error.getMessage());
    }

    private static MethodHandle classResolver() throws IllegalAccessException, NoSuchMethodException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(ContentParser.class, MethodHandles.lookup());
        return lookup.findStatic(
                ContentParser.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
    }
}
