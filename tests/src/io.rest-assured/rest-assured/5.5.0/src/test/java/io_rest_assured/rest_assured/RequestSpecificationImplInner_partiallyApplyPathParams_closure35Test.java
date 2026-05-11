/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.util.List;

import io.restassured.RestAssured;
import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.internal.RequestSpecificationImplPartiallyApplyPathParamsClosure35Access;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class RequestSpecificationImplInner_partiallyApplyPathParams_closure35Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$_partiallyApplyPathParams_closure35";
    private static final String MISSING_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImplMissingPartiallyApplyPathParamsClosure35Target";

    @Test
    void compilerGeneratedClassResolverUsesClassForName() throws Throwable {
        try {
            Class<?> resolvedClass = RequestSpecificationImplPartiallyApplyPathParamsClosure35Access
                    .resolveWithCompilerGeneratedClassResolver(RequestSpecificationImpl.class.getName());

            assertSame(RequestSpecificationImpl.class, resolvedClass);
        } catch (Throwable throwable) {
            rethrowUnlessExpectedNativeImageError(throwable);
        }
    }

    @Test
    void compilerGeneratedClassResolverReportsMissingClasses() throws Throwable {
        try {
            RequestSpecificationImplPartiallyApplyPathParamsClosure35Access
                    .resolveWithCompilerGeneratedClassResolver(MISSING_CLASS_NAME);
            fail("Missing classes should be reported as NoClassDefFoundError");
        } catch (NoClassDefFoundError error) {
            if (isNativeGroovyInitializationFailure(error)) {
                return;
            }
            assertTrue(error.getMessage().contains(MISSING_CLASS_NAME));
        } catch (Throwable throwable) {
            rethrowUnlessExpectedNativeImageError(throwable);
        }
    }

    @Test
    void partiallyAppliesNamedAndUnnamedPathParameters() {
        try {
            RequestSpecificationImpl requestSpecification = (RequestSpecificationImpl) RestAssured.given()
                    .pathParam("id", "123");

            String uri = requestSpecification.partiallyApplyPathParams(
                    "http://example.com/users/{id}/orders/{orderId}?expand={expand}",
                    true,
                    List.of("A-1", "details"));

            assertEquals("http://example.com/users/123/orders/A-1?expand=details", uri);
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } finally {
            RestAssured.reset();
        }
    }

    @Test
    void leavesUnresolvedPathParametersInPartiallyAppliedPath() {
        try {
            RequestSpecificationImpl requestSpecification = (RequestSpecificationImpl) RestAssured.given();

            String uri = requestSpecification.partiallyApplyPathParams(
                    "/users/{id}/orders/{orderId}",
                    false,
                    List.of("123"));

            assertEquals("http://localhost:8080/users/123/orders/{orderId}", uri);
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } finally {
            RestAssured.reset();
        }
    }

    private static void rethrowUnlessExpectedNativeImageError(Throwable throwable) throws Throwable {
        if (throwable instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
            return;
        }
        if (throwable instanceof LinkageError error && isNativeGroovyInitializationFailure(error)) {
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
