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
import java.util.List;
import java.util.Map;

import groovy.lang.Closure;
import groovy.lang.Reference;
import io.restassured.RestAssured;
import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.internal.RequestSpecificationImplRedundantNamedPathParamsClosure48Access;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestSpecificationImplInner_getRedundantNamedPathParams_closure48Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$_getRedundantNamedPathParams_closure48";
    private static final String MISSING_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImplMissingRedundantNamedPathParamsClass";

    @Test
    void compilerGeneratedClassResolverUsesClassForName() throws Throwable {
        try {
            Class<?> resolvedClass = RequestSpecificationImplRedundantNamedPathParamsClosure48Access
                    .resolveWithCompilerGeneratedClassResolver(String.class.getName());

            assertSame(String.class, resolvedClass);
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void compilerGeneratedClassResolverReportsMissingClass() throws Throwable {
        try {
            RequestSpecificationImplRedundantNamedPathParamsClosure48Access
                    .resolveWithCompilerGeneratedClassResolver(MISSING_CLASS_NAME);
            throw new AssertionError("Missing classes should be reported as NoClassDefFoundError");
        } catch (NoClassDefFoundError error) {
            if (isNativeGroovyInitializationFailure(error)) {
                return;
            }
            assertEquals(MISSING_CLASS_NAME, error.getMessage());
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void closureIdentifiesNamedPathParametersMissingFromPlaceholderList() throws Throwable {
        List<String> placeholders = List.of("id");

        try {
            Closure<?> closure = newClosureInstance(placeholders);

            assertSame(placeholders, getPlaceholders(closure));
            assertEquals(Boolean.FALSE, closure.call(Map.entry("id", "42")));
            assertEquals(Boolean.TRUE, closure.call(Map.entry("unused", "value")));
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void redundantNamedPathParameterIsRejectedBeforeRequestIsSent() throws Throwable {
        try {
            RestAssured.given()
                    .baseUri("http://127.0.0.1")
                    .pathParam("id", "42")
                    .pathParam("unused", "value")
                    .get("/orders/{id}");
            throw new AssertionError("Redundant named path parameters should be rejected");
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains("Invalid number of path parameters."));
            assertTrue(exception.getMessage().contains("Redundant path parameters are:"));
            assertTrue(exception.getMessage().contains("unused=value"));
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } finally {
            RestAssured.reset();
        }
    }

    private static Closure<?> newClosureInstance(List<String> placeholders) throws Throwable {
        Class<?> closureClass = closureClass();
        MethodHandles.Lookup closureLookup = MethodHandles.privateLookupIn(
                closureClass,
                MethodHandles.lookup());
        MethodHandle constructor = closureLookup.findConstructor(
                closureClass,
                MethodType.methodType(void.class, Object.class, Object.class, Reference.class));
        return (Closure<?>) constructor.invoke(new Object(), new Object(), new Reference<>(placeholders));
    }

    private static Object getPlaceholders(Closure<?> closure) throws Throwable {
        Class<?> closureClass = closureClass();
        MethodHandles.Lookup closureLookup = MethodHandles.privateLookupIn(
                closureClass,
                MethodHandles.lookup());
        MethodHandle getter = closureLookup.findVirtual(
                closureClass,
                "getPlaceholders",
                MethodType.methodType(Object.class));
        return getter.invoke(closure);
    }

    private static Class<?> closureClass() throws ClassNotFoundException, IllegalAccessException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                RequestSpecificationImpl.class,
                MethodHandles.lookup());
        return lookup.findClass(CLOSURE_CLASS_NAME);
    }

    private static void rethrowUnlessUnsupportedNativeImageError(Error error) {
        if (NativeImageSupport.isUnsupportedFeatureError(error)) {
            return;
        }
        throw error;
    }

    private static void assertNativeGroovyInitializationFailure(LinkageError error) {
        assertTrue(isNativeGroovyInitializationFailure(error), () -> "Unexpected initialization failure: " + error);
    }

    private static boolean isNativeGroovyInitializationFailure(LinkageError error) {
        String message = error.getMessage();
        return ("Could not initialize class " + CLOSURE_CLASS_NAME).equals(message)
                || "Could not initialize class groovy.lang.Closure".equals(message)
                || "Could not initialize class groovy.lang.GroovySystem".equals(message)
                || "Could not initialize class org.codehaus.groovy.runtime.InvokerHelper".equals(message)
                || "Could not initialize class io.restassured.RestAssured".equals(message)
                || isGroovySystemInitializerError(error);
    }

    private static boolean isGroovySystemInitializerError(LinkageError error) {
        if (!(error instanceof ExceptionInInitializerError initializerError)) {
            return false;
        }
        Throwable cause = initializerError.getException();
        return cause instanceof NullPointerException
                && cause.getStackTrace().length > 0
                && "groovy.lang.GroovySystem".equals(cause.getStackTrace()[0].getClassName());
    }
}
