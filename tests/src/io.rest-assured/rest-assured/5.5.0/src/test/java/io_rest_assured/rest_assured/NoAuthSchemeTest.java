/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import groovy.lang.GroovyObject;
import groovy.lang.MetaClass;
import io.restassured.authentication.NoAuthScheme;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NoAuthSchemeTest {
    @Test
    void resolvesCompilerGeneratedClassResolver() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                NoAuthScheme.class,
                MethodHandles.lookup());
        MethodHandle classResolver = lookup.findStatic(
                NoAuthScheme.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classResolver.invokeExact(
                "io.restassured.authentication.NoAuthScheme");

        assertSame(NoAuthScheme.class, resolvedClass);
    }

    @Test
    void resolvesCompilerGeneratedClassResolverThroughGroovyStaticDispatch() {
        try {
            Object resolvedClass = InvokerHelper.invokeStaticMethod(
                    NoAuthScheme.class,
                    "class$",
                    new Object[] {"io.restassured.authentication.NoAuthScheme"});

            assertSame(NoAuthScheme.class, resolvedClass);
        } catch (NoClassDefFoundError error) {
            assertGroovyInitializationFailure(error);
        }
    }

    @Test
    void resolvesCompilerGeneratedClassResolverThroughGroovyMetaClass() {
        try {
            MetaClass metaClass = InvokerHelper.getMetaClass(NoAuthScheme.class);

            Object resolvedClass = metaClass.invokeStaticMethod(
                    NoAuthScheme.class,
                    "class$",
                    new Object[] {"io.restassured.authentication.NoAuthScheme"});

            assertSame(NoAuthScheme.class, resolvedClass);
        } catch (NoClassDefFoundError error) {
            assertGroovyInitializationFailure(error);
        }
    }

    @Test
    void reportsUnknownCompilerGeneratedClassAsNoClassDefFoundError() throws Throwable {
        NoClassDefFoundError error = assertThrows(
                NoClassDefFoundError.class,
                () -> classResolver().invokeWithArguments(
                        "io.restassured.authentication.NoAuthSchemeMissingClass"));

        assertEquals("io.restassured.authentication.NoAuthSchemeMissingClass", error.getMessage());
    }

    @Test
    void authenticationSchemeDoesNotAuthenticate() {
        NoAuthScheme authenticationScheme = assertInstanceOf(NoAuthScheme.class, new NoAuthScheme());

        assertDoesNotThrow(() -> authenticationScheme.authenticate(null));
        assertEquals(NoAuthScheme.class, ((GroovyObject) authenticationScheme).getMetaClass().getTheClass());
    }

    private static MethodHandle classResolver() throws IllegalAccessException, NoSuchMethodException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                NoAuthScheme.class,
                MethodHandles.lookup());
        return lookup.findStatic(
                NoAuthScheme.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
    }

    private static void assertGroovyInitializationFailure(NoClassDefFoundError error) {
        String message = error.getMessage();
        assertTrue(
                "Could not initialize class groovy.lang.GroovySystem".equals(message)
                        || "Could not initialize class org.codehaus.groovy.runtime.InvokerHelper".equals(message));
    }
}
