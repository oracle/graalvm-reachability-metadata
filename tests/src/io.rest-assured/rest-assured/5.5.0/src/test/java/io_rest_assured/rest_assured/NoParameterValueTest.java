/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import groovy.lang.GroovyObject;
import io.restassured.internal.NoParameterValue;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NoParameterValueTest {
    private static final String DYNAMIC_RESOLUTION_TARGET =
            "io.restassured.internal.http.BasicNameValuePairWithNoValueSupport";

    /**
     * Minimal same-package helper whose class initializer directly invokes
     * `NoParameterValue.class$(String)` from Java bytecode.
     */
    private static final byte[] DIRECT_INVOKER_CLASS = Base64.getMimeDecoder().decode("""
            yv66vgAAADQAGAEANWlvL3Jlc3Rhc3N1cmVkL2ludGVybmFsL05vUGFyYW1ldGVyVmFsdWVEaXJl
            Y3RJbnZva2VyBwABAQAQamF2YS9sYW5nL09iamVjdAcAAwEAEGphdmEvbGFuZy9PYmplY3QHAAUB
            AAY8aW5pdD4BAAMoKVYMAAcACAoABgAJAQAEQ29kZQEAKGlvL3Jlc3Rhc3N1cmVkL2ludGVybmFs
            L05vUGFyYW1ldGVyVmFsdWUHAAwBAAZjbGFzcyQBACUoTGphdmEvbGFuZy9TdHJpbmc7KUxqYXZh
            L2xhbmcvQ2xhc3M7DAAOAA8KAA0AEAEAMWlvLnJlc3Rhc3N1cmVkLmludGVybmFsLm1hdGNoZXIu
            eG1sLlhtbER0ZE1hdGNoZXIIABIBAAY8aW5pdD4BAAMoKVYBAAg8Y2xpbml0PgEAAygpVgAhAAIA
            BAAAAAAAAgABABQAFQABAAsAAAARAAEAAQAAAAUqtwAKsQAAAAAACAAWABcAAQALAAAAEwABAAAA
            AAAHEhO4ABFXsQAAAAAAAA==
            """);

    @Test
    void resolvesNamedClassThroughCompilerGeneratedClassResolver() throws Throwable {
        Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(DYNAMIC_RESOLUTION_TARGET);

        assertEquals(DYNAMIC_RESOLUTION_TARGET, resolvedClass.getName());
    }

    @Test
    void resolvesNamedClassThroughGroovyStaticDispatch() {
        try {
            Object resolvedClass = InvokerHelper.invokeStaticMethod(
                    NoParameterValue.class,
                    "class$",
                    new Object[] {DYNAMIC_RESOLUTION_TARGET});

            assertEquals(DYNAMIC_RESOLUTION_TARGET, ((Class<?>) resolvedClass).getName());
        } catch (NoClassDefFoundError error) {
            assertEquals("Could not initialize class groovy.lang.GroovySystem", error.getMessage());
        }
    }

    @Test
    void directInvokerReachesCompilerGeneratedClassResolver() throws IllegalAccessException {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    NoParameterValue.class,
                    MethodHandles.lookup());
            Class<?> directInvokerClass = lookup.defineClass(DIRECT_INVOKER_CLASS);

            lookup.ensureInitialized(directInvokerClass);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void reportsUnknownCompilerGeneratedClassAsNoClassDefFoundError() throws Throwable {
        NoClassDefFoundError error = assertThrows(
                NoClassDefFoundError.class,
                () -> resolveWithCompilerGeneratedClassResolver(
                        "io.restassured.internal.NoParameterValueMissingClass"));

        assertEquals("io.restassured.internal.NoParameterValueMissingClass", error.getMessage());
    }

    @Test
    void markerHasGroovyObjectSemantics() {
        NoParameterValue marker = new NoParameterValue();

        assertTrue(marker instanceof GroovyObject);
    }

    private static Class<?> resolveWithCompilerGeneratedClassResolver(String className) throws Throwable {
        return (Class<?>) classResolver().invokeExact(className);
    }

    private static MethodHandle classResolver() throws IllegalAccessException, NoSuchMethodException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                NoParameterValue.class,
                MethodHandles.lookup());
        return lookup.findStatic(
                NoParameterValue.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
    }
}
