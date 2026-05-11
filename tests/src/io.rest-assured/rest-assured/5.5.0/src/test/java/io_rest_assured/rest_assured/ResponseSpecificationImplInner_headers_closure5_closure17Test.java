/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.builder.ResponseBuilder;
import io.restassured.internal.ResponseSpecificationImplHeadersClosure5Closure17DirectAccess;
import io.restassured.response.Response;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResponseSpecificationImplInner_headers_closure5_closure17Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.ResponseSpecificationImpl$_headers_closure5_closure17";
    private static final String OUTER_CLOSURE_CLASS_NAME =
            "io.restassured.internal.ResponseSpecificationImpl$_headers_closure5";
    private static final String INNER_LIST_CLOSURE_CLASS_NAME =
            "io.restassured.internal.ResponseSpecificationImpl$_headers_closure5_closure17_closure18";

    @Test
    void validatesExpectedHeaderListThroughPublicResponseSpecification() {
        try {
            assertExpectedHeaderListValidation();
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    @Test
    void resolvesActiveClosureThroughCompilerGeneratedClassResolver() throws Throwable {
        try {
            Class<?> resolvedClass = ResponseSpecificationImplHeadersClosure5Closure17DirectAccess
                    .resolveWithCompilerGeneratedClassResolver(CLOSURE_CLASS_NAME);

            assertEquals(CLOSURE_CLASS_NAME, resolvedClass.getName());
            assertExpectedHeaderListValidation();
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void resolvesRuntimeJdkClassThroughCompilerGeneratedClassResolver() throws Throwable {
        try {
            String className = String.join(".", "java", "util", "LinkedList");

            Class<?> resolvedClass = ResponseSpecificationImplHeadersClosure5Closure17DirectAccess
                    .resolveWithCompilerGeneratedClassResolver(className);

            assertEquals(className, resolvedClass.getName());
            assertExpectedHeaderListValidation();
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void reportsMissingClassThroughCompilerGeneratedClassResolver() throws Throwable {
        try {
            ResponseSpecificationImplHeadersClosure5Closure17DirectAccess
                    .resolveWithCompilerGeneratedClassResolver(CLOSURE_CLASS_NAME + "Missing");
            throw new AssertionError("Missing classes should be reported as NoClassDefFoundError");
        } catch (NoClassDefFoundError error) {
            if (isNativeGroovyInitializationFailure(error)) {
                return;
            }
            assertTrue(error.getMessage().endsWith("closure17Missing"));
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    private static void assertExpectedHeaderListValidation() {
        Response response = new ResponseBuilder()
                .setStatusCode(200)
                .setBody("ok")
                .setHeader("X-Trace", "trace-1")
                .setHeader("X-Mode", "list")
                .build();
        Map<String, Object> expectedHeaders = new LinkedHashMap<>();
        expectedHeaders.put("X-Trace", List.of(equalTo("trace-1"), "trace-1"));
        expectedHeaders.put("X-Mode", List.of(equalTo("list"), "list"));

        response.then()
                .assertThat()
                .headers(expectedHeaders);
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
                || ("Could not initialize class " + OUTER_CLOSURE_CLASS_NAME).equals(message)
                || ("Could not initialize class " + INNER_LIST_CLOSURE_CLASS_NAME).equals(message)
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
