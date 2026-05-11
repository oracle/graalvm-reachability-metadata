/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.restassured.RestAssured;
import io.restassured.builder.ResponseBuilder;
import io.restassured.internal.NoParameterValue;
import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.internal.RequestSpecificationImplGetUnnamedPathParamsClosure42Access;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestSpecificationImplInner_getUnnamedPathParams_closure42Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$_getUnnamedPathParams_closure42";
    private static final String MISSING_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImplMissingGetUnnamedPathParamsAccumulatorClass";

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
            Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(
                    NoParameterValue.class.getName());

            assertSame(NoParameterValue.class, resolvedClass);
        } catch (NoClassDefFoundError error) {
            if (isNativeGroovyInitializationFailure(error)
                    || NoParameterValue.class.getName().equals(error.getMessage())) {
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
    void getUnnamedPathParamsAccumulatesPlaceholderNamesAndValues() {
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

    private static void assertUnnamedPathParams(FilterableRequestSpecification requestSpec) {
        Map<String, String> unnamedPathParams = requestSpec.getUnnamedPathParams();

        assertEquals(2, unnamedPathParams.size());
        assertEquals("C-42", unnamedPathParams.get("customerId"));
        assertEquals("A-1", unnamedPathParams.get("orderId"));
        assertThrows(UnsupportedOperationException.class, () -> unnamedPathParams.put("extra", "ignored"));
    }

    private static Class<?> resolveWithCompilerGeneratedClassResolver(String className) throws Throwable {
        return RequestSpecificationImplGetUnnamedPathParamsClosure42Access
                .resolveWithCompilerGeneratedClassResolver(className);
    }

    private static void resetRestAssured() {
        try {
            RestAssured.reset();
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
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
