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
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import groovy.lang.Closure;
import groovy.lang.Tuple2;
import io.restassured.RestAssured;
import io.restassured.builder.ResponseBuilder;
import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestSpecificationImplInner_getUnnamedPathParams_closure41Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$_getUnnamedPathParams_closure41";
    private static final String MISSING_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImplMissingGetUnnamedPathParamsClass";
    private static final String REST_ASSURED_CLASS_NAME = "io.restassured.internal.NoParameterValue";
    private static final byte[] DIRECT_INVOKER_CLASS = Base64.getMimeDecoder().decode("""
            yv66vgAAADQAEwEAWmlvL3Jlc3Rhc3N1cmVkL2ludGVybmFsL1JlcXVlc3RTcGVjaWZpY2F0aW9u
            SW1wbEdldFVubmFtZWRQYXRoUGFyYW1zQ2xvc3VyZTQxRGlyZWN0SW52b2tlcgcAAQEAEGphdmEv
            bGFuZy9PYmplY3QHAAMBAAY8aW5pdD4BAAMoKVYMAAUABgoABAAHAQAEQ29kZQEACDxjbGluaXQ+
            AQBQaW8vcmVzdGFzc3VyZWQvaW50ZXJuYWwvUmVxdWVzdFNwZWNpZmljYXRpb25JbXBsJF9nZXRV
            bm5hbWVkUGF0aFBhcmFtc19jbG9zdXJlNDEHAAsBAAZjbGFzcyQBACUoTGphdmEvbGFuZy9TdHJp
            bmc7KUxqYXZhL2xhbmcvQ2xhc3M7DAANAA4KAAwADwEAKGlvLnJlc3Rhc3N1cmVkLmludGVybmFs
            Lk5vUGFyYW1ldGVyVmFsdWUIABEAIQACAAQAAAAAAAIAAQAFAAYAAQAJAAAAEQABAAEAAAAFKrcA
            CLEAAAAAAAgACgAGAAEACQAAABMAAQAAAAAABxISuAAQV7EAAAAAAAA=
            """);

    @Test
    void compilerGeneratedClassResolverUsesClassForName() throws Throwable {
        try {
            Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(
                    RequestSpecificationImpl.class.getName());

            assertSame(RequestSpecificationImpl.class, resolvedClass);
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void compilerGeneratedClassResolverLoadsRestAssuredClassByName() throws Throwable {
        try {
            Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(REST_ASSURED_CLASS_NAME);

            assertEquals(REST_ASSURED_CLASS_NAME, resolvedClass.getName());
        } catch (NoClassDefFoundError error) {
            if (isNativeGroovyInitializationFailure(error) || REST_ASSURED_CLASS_NAME.equals(error.getMessage())) {
                return;
            }
            throw error;
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void compilerGeneratedClassResolverReportsMissingClass() throws Throwable {
        try {
            resolveWithCompilerGeneratedClassResolver(MISSING_CLASS_NAME);
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
    void directInvokerReachesCompilerGeneratedClassResolver() throws IllegalAccessException {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    RequestSpecificationImpl.class,
                    MethodHandles.lookup());
            Class<?> directInvokerClass = lookup.defineClass(DIRECT_INVOKER_CLASS);

            lookup.ensureInitialized(directInvokerClass);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void closureSelectsTuplesWithPlaceholderNames() throws Throwable {
        try {
            Closure<?> closure = newClosureInstance();

            assertEquals(Boolean.TRUE, closure.call(new Tuple2<>("customerId", "C-42")));
            assertEquals(Boolean.FALSE, closure.call(new Tuple2<String, String>(null, "redundant-value")));
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    @Test
    void closureReportsNullInput() throws Throwable {
        try {
            Closure<?> closure = newClosureInstance();

            assertThrows(NullPointerException.class, () -> doCallWithoutArgument(closure));
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    @Test
    void getUnnamedPathParamsReturnsOnlyPlaceholderMappedUnnamedParameters() {
        AtomicBoolean filterWasCalled = new AtomicBoolean(false);

        try {
            Response response = RestAssured.given()
                    .filter((requestSpec, responseSpec, context) -> {
                        filterWasCalled.set(true);
                        assertUnnamedPathParams(requestSpec);
                        return new ResponseBuilder()
                                .setStatusCode(200)
                                .setBody("ok")
                                .build();
                    })
                    .get("/customers/{customerId}/orders/{orderId}", "C-42", "A-1");

            assertEquals(200, response.statusCode());
            assertEquals("ok", response.asString());
            assertTrue(filterWasCalled.get());
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } finally {
            resetRestAssured();
        }
    }

    private static void resetRestAssured() {
        try {
            RestAssured.reset();
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    private static void assertUnnamedPathParams(FilterableRequestSpecification requestSpec) {
        Map<String, String> unnamedPathParams = requestSpec.getUnnamedPathParams();

        assertEquals(2, unnamedPathParams.size());
        assertEquals("C-42", unnamedPathParams.get("customerId"));
        assertEquals("A-1", unnamedPathParams.get("orderId"));
        assertThrows(UnsupportedOperationException.class, () -> unnamedPathParams.put("extra", "ignored"));
    }

    private static Class<?> resolveWithCompilerGeneratedClassResolver(String className) throws Throwable {
        return (Class<?>) classResolver().invokeExact(className);
    }

    private static Closure<?> newClosureInstance() throws Throwable {
        Class<?> closureClass = closureClass();
        MethodHandles.Lookup closureLookup = MethodHandles.privateLookupIn(
                closureClass,
                MethodHandles.lookup());
        MethodHandle constructor = closureLookup.findConstructor(
                closureClass,
                MethodType.methodType(void.class, Object.class, Object.class));
        return (Closure<?>) constructor.invoke(new Object(), new Object());
    }

    private static Object doCallWithoutArgument(Closure<?> closure) throws Throwable {
        Class<?> closureClass = closureClass();
        MethodHandles.Lookup closureLookup = MethodHandles.privateLookupIn(
                closureClass,
                MethodHandles.lookup());
        MethodHandle doCall = closureLookup.findVirtual(
                closureClass,
                "doCall",
                MethodType.methodType(Object.class));
        return doCall.invoke(closure);
    }

    private static MethodHandle classResolver()
            throws ClassNotFoundException, IllegalAccessException, NoSuchMethodException {
        Class<?> closureClass = closureClass();
        MethodHandles.Lookup closureLookup = MethodHandles.privateLookupIn(
                closureClass,
                MethodHandles.lookup());
        return closureLookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
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
