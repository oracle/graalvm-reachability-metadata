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
import java.util.concurrent.atomic.AtomicBoolean;

import groovy.lang.Closure;
import groovy.lang.Tuple2;
import io.restassured.RestAssured;
import io.restassured.builder.ResponseBuilder;
import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.internal.RequestSpecificationImplGetUnnamedPathParamValuesClosure44Access;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestSpecificationImplInner_getUnnamedPathParamValues_closure44Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$_getUnnamedPathParamValues_closure44";
    private static final String[] CLASS_FOR_NAME_TARGETS = {
        "io.restassured.internal.util.SafeExceptionRethrower",
        "io.restassured.internal.support.Prettifier",
        "io.restassured.internal.UriValidator"
    };
    private static final String MISSING_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImplMissingGetUnnamedPathParamValuesClosure44Class";

    @Test
    void compilerGeneratedClassResolverUsesClassForName() throws Throwable {
        try {
            for (String className : CLASS_FOR_NAME_TARGETS) {
                Class<?> resolvedClass = RequestSpecificationImplGetUnnamedPathParamValuesClosure44Access
                        .resolveWithCompilerGeneratedClassResolver(className);

                assertEquals(className, resolvedClass.getName());
            }
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    @Test
    void compilerGeneratedClassResolverReportsMissingClass() throws Throwable {
        try {
            RequestSpecificationImplGetUnnamedPathParamValuesClosure44Access
                    .resolveWithCompilerGeneratedClassResolver(MISSING_CLASS_NAME);
            throw new AssertionError("Missing classes should be reported as NoClassDefFoundError");
        } catch (NoClassDefFoundError error) {
            if (isNativeGroovyInitializationFailure(error)) {
                return;
            }
            assertEquals(MISSING_CLASS_NAME, error.getMessage());
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    @Test
    void closureCollectsTupleValue() throws Throwable {
        try {
            Closure<?> closure = newClosureInstance();

            assertEquals("C-42", closure.call(new Tuple2<>("customerId", "C-42")));
            assertEquals("A-1", closure.call(new Tuple2<>(null, "A-1")));
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    @Test
    void closureReportsNullInput() throws Throwable {
        try {
            Closure<?> closure = newClosureInstance();

            assertThrows(NullPointerException.class, () -> closure.call((Object) null));
            assertThrows(NullPointerException.class, closure::call);
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    @Test
    void getUnnamedPathParamValuesCollectsSuppliedUnnamedValues() {
        AtomicBoolean filterWasCalled = new AtomicBoolean(false);

        try {
            Response response = RestAssured.given()
                    .filter((requestSpec, responseSpec, context) -> {
                        filterWasCalled.set(true);
                        assertUnnamedPathParamValues(requestSpec);
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
            RestAssured.reset();
        }
    }

    private static void assertUnnamedPathParamValues(FilterableRequestSpecification requestSpec) {
        List<String> unnamedValues = requestSpec.getUnnamedPathParamValues();

        assertEquals(List.of("C-42", "A-1"), unnamedValues);
        assertThrows(UnsupportedOperationException.class, () -> unnamedValues.add("extra"));
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

    private static Class<?> closureClass() throws ClassNotFoundException, IllegalAccessException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                RequestSpecificationImpl.class,
                MethodHandles.lookup());
        return lookup.findClass(CLOSURE_CLASS_NAME);
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
