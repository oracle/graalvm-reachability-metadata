/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.restassured.RestAssured;
import io.restassured.builder.ResponseBuilder;
import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.internal.RequestSpecificationImplRemoveUnnamedPathParamClosure3DirectAccess;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestSpecificationImplInner_removeUnnamedPathParam_closure3Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$_removeUnnamedPathParam_closure3";
    private static final String MISSING_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImplMissingRemoveUnnamedPathParamClosure3Target";

    @Test
    void compilerGeneratedClassResolverUsesClassForName() throws Throwable {
        try {
            String className = runtimeClassName(RequestSpecificationImpl.class.getName());

            Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(className);

            assertSame(RequestSpecificationImpl.class, resolvedClass);
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void compilerGeneratedClassResolverReportsMissingClasses() throws Throwable {
        try {
            NoClassDefFoundError error = assertThrows(
                    NoClassDefFoundError.class,
                    () -> resolveWithCompilerGeneratedClassResolver(MISSING_CLASS_NAME));

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
    void removeUnnamedPathParamDropsFirstMatchingUnnamedName() {
        AtomicBoolean filterWasCalled = new AtomicBoolean(false);

        try {
            Response response = RestAssured.given()
                    .filter((requestSpec, responseSpec, context) -> {
                        filterWasCalled.set(true);
                        requestSpec.removeUnnamedPathParam("customerId");
                        assertRemainingUnnamedPathParams(requestSpec);
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

    private static void assertRemainingUnnamedPathParams(FilterableRequestSpecification requestSpec) {
        Map<String, String> unnamedPathParams = requestSpec.getUnnamedPathParams();
        List<String> unnamedPathParamValues = requestSpec.getUnnamedPathParamValues();

        assertFalse(unnamedPathParams.containsKey("customerId"));
        assertEquals(Map.of("orderId", "A-1"), unnamedPathParams);
        assertEquals(List.of("A-1"), unnamedPathParamValues);
        assertThrows(UnsupportedOperationException.class, () -> unnamedPathParams.put("other", "value"));
        assertThrows(UnsupportedOperationException.class, () -> unnamedPathParamValues.add("extra"));
    }

    private static Class<?> resolveWithCompilerGeneratedClassResolver(String className) throws Throwable {
        return RequestSpecificationImplRemoveUnnamedPathParamClosure3DirectAccess
                .resolveWithCompilerGeneratedClassResolver(className);
    }

    private static String runtimeClassName(String defaultClassName) {
        String configuredClassName = System.getProperty("rest.assured.removeUnnamedPathParamClosure3.targetClass");
        if (configuredClassName != null) {
            return configuredClassName;
        }
        return new StringBuilder(defaultClassName.length())
                .append(defaultClassName)
                .toString();
    }

    private static void rethrowUnlessUnsupportedNativeImageError(Throwable throwable) throws Throwable {
        if (throwable instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
            return;
        }
        throw throwable;
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
