/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.authentication.CertAuthScheme;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CertAuthSchemeTest {
    @Test
    void resolvesCompilerGeneratedClassResolver() throws Throwable {
        Class<?> resolvedClass = (Class<?>) classResolver().invokeWithArguments(
                "io.restassured.authentication.CertAuthScheme");

        assertSame(CertAuthScheme.class, resolvedClass);
    }

    @Test
    void resolvesCompilerGeneratedClassResolverThroughGroovyStaticDispatch() {
        try {
            Object resolvedClass = InvokerHelper.invokeStaticMethod(
                    CertAuthScheme.class,
                    "class$",
                    new Object[] {"io.restassured.authentication.CertAuthScheme"});

            assertSame(CertAuthScheme.class, resolvedClass);
        } catch (NoClassDefFoundError error) {
            assertEquals("Could not initialize class groovy.lang.GroovySystem", error.getMessage());
        }
    }

    @Test
    void reportsUnknownCompilerGeneratedClassAsNoClassDefFoundError() throws Throwable {
        NoClassDefFoundError error = assertThrows(
                NoClassDefFoundError.class,
                () -> classResolver().invokeWithArguments(
                        "io.restassured.authentication.CertAuthSchemeMissingClass"));

        assertEquals("io.restassured.authentication.CertAuthSchemeMissingClass", error.getMessage());
    }

    private static MethodHandle classResolver() throws IllegalAccessException, NoSuchMethodException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                CertAuthScheme.class,
                MethodHandles.lookup());
        return lookup.findStatic(
                CertAuthScheme.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
    }
}
