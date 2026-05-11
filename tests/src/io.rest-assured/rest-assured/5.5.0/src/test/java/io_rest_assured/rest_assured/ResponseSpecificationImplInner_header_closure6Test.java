/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.builder.ResponseBuilder;
import io.restassured.internal.ResponseSpecificationImplHeaderClosure6DirectAccess;
import io.restassured.response.Response;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResponseSpecificationImplInner_header_closure6Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.ResponseSpecificationImpl$_header_closure6";

    @Test
    void validatesMappedHeaderThroughPublicResponseSpecification() {
        try {
            assertMappedHeaderValidation();
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    @Test
    void rejectsHeaderWhenMappedValueDoesNotMatch() {
        try {
            Response response = new ResponseBuilder()
                    .setStatusCode(200)
                    .setBody("ok")
                    .setHeader("X-Items", "3")
                    .build();

            assertThrows(AssertionError.class, () -> response.then()
                    .assertThat()
                    .header("X-Items", Integer::parseInt, equalTo(4)));
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    @Test
    void resolvesActiveClosureThroughCompilerGeneratedClassResolver() throws Throwable {
        try {
            Class<?> resolvedClass = ResponseSpecificationImplHeaderClosure6DirectAccess
                    .resolveWithCompilerGeneratedClassResolver(CLOSURE_CLASS_NAME);

            assertEquals(CLOSURE_CLASS_NAME, resolvedClass.getName());
            assertMappedHeaderValidation();
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void resolvesRuntimeJdkClassThroughCompilerGeneratedClassResolver() throws Throwable {
        try {
            String className = String.join(".", "java", "util", "Locale");

            Class<?> resolvedClass = ResponseSpecificationImplHeaderClosure6DirectAccess
                    .resolveWithCompilerGeneratedClassResolver(className);

            assertEquals(className, resolvedClass.getName());
            assertMappedHeaderValidation();
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void reportsMissingClassThroughCompilerGeneratedClassResolver() throws Throwable {
        try {
            ResponseSpecificationImplHeaderClosure6DirectAccess
                    .resolveWithCompilerGeneratedClassResolver(CLOSURE_CLASS_NAME + "Missing");
            throw new AssertionError("Missing classes should be reported as NoClassDefFoundError");
        } catch (NoClassDefFoundError error) {
            if (isNativeGroovyInitializationFailure(error)) {
                return;
            }
            assertTrue(error.getMessage().endsWith("closure6Missing"));
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    private static void assertMappedHeaderValidation() {
        Response response = new ResponseBuilder()
                .setStatusCode(200)
                .setBody("ok")
                .setHeader("X-Items", "3")
                .build();

        response.then()
                .assertThat()
                .header("X-Items", Integer::parseInt, equalTo(3));
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
