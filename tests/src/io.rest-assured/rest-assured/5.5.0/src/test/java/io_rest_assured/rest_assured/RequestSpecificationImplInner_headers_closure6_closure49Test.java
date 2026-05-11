/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.util.List;
import java.util.Map;

import io.restassured.RestAssured;
import io.restassured.http.Headers;
import io.restassured.internal.RequestSpecificationImplHeadersClosure6Closure49DirectAccess;
import io.restassured.specification.QueryableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.SpecificationQuerier;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RequestSpecificationImplInner_headers_closure6_closure49Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$_headers_closure6_closure49";
    private static final String RESOLVABLE_CLASS_NAME = "io.restassured.http.Header";
    private static final String MISSING_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImplMissingHeadersClosure6Closure49Target";

    @Test
    @Order(2)
    void compilerGeneratedClassResolverResolvesRestAssuredHeaderClass() throws Throwable {
        try {
            assertListValuedHeadersCreateOneHeaderForEachValue();

            Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(RESOLVABLE_CLASS_NAME);

            Class<?> type = assertInstanceOf(Class.class, resolvedClass);
            assertEquals(RESOLVABLE_CLASS_NAME, type.getName());
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    @Order(3)
    void compilerGeneratedClassResolverReportsMissingClasses() throws Throwable {
        try {
            assertListValuedHeadersCreateOneHeaderForEachValue();

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
    @Order(1)
    void listValuedHeadersCreateOneHeaderForEachValue() throws Throwable {
        try {
            assertListValuedHeadersCreateOneHeaderForEachValue();

            Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(RESOLVABLE_CLASS_NAME);
            Class<?> type = assertInstanceOf(Class.class, resolvedClass);
            assertEquals(RESOLVABLE_CLASS_NAME, type.getName());
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    private static void assertListValuedHeadersCreateOneHeaderForEachValue() {
        RequestSpecification requestSpecification = RestAssured.given()
                .headers(Map.of("X-Trace", List.of("alpha", "bravo", "charlie")));

        QueryableRequestSpecification queryableSpecification = SpecificationQuerier.query(requestSpecification);
        Headers headers = queryableSpecification.getHeaders();

        assertEquals(List.of("alpha", "bravo", "charlie"), headers.getValues("X-Trace"));
    }

    private static Class<?> resolveWithCompilerGeneratedClassResolver(String className) throws Throwable {
        return RequestSpecificationImplHeadersClosure6Closure49DirectAccess
                .resolveWithCompilerGeneratedClassResolver(className);
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
